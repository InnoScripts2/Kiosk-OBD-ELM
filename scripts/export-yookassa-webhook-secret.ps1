<#
.SYNOPSIS
    Fetches the YooKassa webhook secret from Vault and exports it as an environment variable for Gradle.

.DESCRIPTION
    This helper is intended to be executed inside CI before calling ./gradlew. It reads the field specified by
    -SecretField (defaults to `webhookSecret`) from a KV path such as `kv/selfservice/payments/qa/psp`, masks
    the value in pipeline logs, and assigns it to `YOOKASSA_WEBHOOK_SECRET` (or any variable specified via -EnvVariable).
    When -EmitPipelineVariables is used, the script also propagates the secret into Azure DevOps and GitHub Actions
    contexts so that subsequent steps inherit it automatically.

.EXAMPLE
    pwsh ./android/scripts/export-yookassa-webhook-secret.ps1 -Environment qa -EmitPipelineVariables

    Downloads `kv/selfservice/payments/qa/psp:webhookSecret`, stores it in `YOOKASSA_WEBHOOK_SECRET`, masks the
    value in logs and publishes a secret pipeline variable.

.NOTES
    - Requires the `vault` CLI with VAULT_ADDR/VAULT_TOKEN already configured.
    - Designed for PowerShell 7+ (cross-platform). Does not write the secret to disk.
#>

[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidateNotNullOrEmpty()]
    [string]$Environment,

    [Parameter()]
    [ValidateNotNullOrEmpty()]
    [string]$VaultPathTemplate = "kv/selfservice/payments/{env}/psp",

    [Parameter()]
    [ValidateNotNullOrEmpty()]
    [string]$SecretField = "webhookSecret",

    [Parameter()]
    [ValidateNotNullOrEmpty()]
    [string]$EnvVariable = "YOOKASSA_WEBHOOK_SECRET",

    [switch]$EmitPipelineVariables,

    [switch]$DryRun
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Resolve-VaultPath {
    param(
        [string]$Template,
        [string]$EnvName
    )
    return ($Template -replace "{env}", $EnvName)
}

function Set-AzurePipelineVariable {
    param(
        [string]$Name,
        [string]$Value
    )
    if ($env:TF_BUILD -eq "True" -or $env:AZURE_HTTP_USER_AGENT) {
        Write-Host "##vso[task.setvariable variable=$Name;issecret=true]$Value"
    }
}

function Set-GitHubEnvVariable {
    param(
        [string]$Name,
        [string]$Value
    )
    if ($env:GITHUB_ENV) {
        Add-Content -Path $env:GITHUB_ENV -Value "$Name=$Value"
    }
    if ($env:GITHUB_ACTIONS -eq "true") {
        Write-Host "::add-mask::$Value"
        if ($env:GITHUB_OUTPUT) {
            Add-Content -Path $env:GITHUB_OUTPUT -Value "$Name=$Value"
        }
    }
}

$normalizedEnv = $Environment.Trim().ToLowerInvariant()
if (-not $normalizedEnv) {
    throw "Environment name cannot be blank."
}

$vaultPath = Resolve-VaultPath -Template $VaultPathTemplate -EnvName $normalizedEnv

if ($DryRun) {
    Write-Host "[export-yookassa-webhook-secret] Dry run: would fetch '$SecretField' from '$vaultPath' and export it as '$EnvVariable'."
    return
}

if (-not (Get-Command vault -ErrorAction SilentlyContinue)) {
    throw "The 'vault' CLI is not available in PATH. Install HashiCorp Vault CLI before running this script."
}

$vaultArgs = @("kv", "get", "-field=$SecretField", $vaultPath)

Write-Host "[export-yookassa-webhook-secret] Fetching '$SecretField' from '$vaultPath'..."
$secret = & vault @vaultArgs
if ($LASTEXITCODE -ne 0) {
    throw "Failed to read field '$SecretField' from '$vaultPath' via Vault CLI."
}
$secret = $secret.Trim()
if (-not $secret) {
    throw "Received empty value for '$SecretField' at '$vaultPath'."
}

Set-Item -Path "env:$EnvVariable" -Value $secret
Write-Host "[export-yookassa-webhook-secret] Environment variable '$EnvVariable' populated (value masked)."

if ($EmitPipelineVariables) {
    Set-AzurePipelineVariable -Name $EnvVariable -Value $secret
    Set-GitHubEnvVariable -Name $EnvVariable -Value $secret
}

Write-Verbose "Provide '-Ppayments.yookassa.webhookSecret=$($secret.Length)-bytes' to Gradle if you need to override the property explicitly."
Write-Host "[export-yookassa-webhook-secret] Done. Invoke './gradlew :app:checkYooKassaWebhookSecret -Pkiosk.environment=$normalizedEnv -Ppayments.gateway=yookassa' next."
