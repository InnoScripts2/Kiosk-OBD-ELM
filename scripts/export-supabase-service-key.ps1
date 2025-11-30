<#
.SYNOPSIS
    Fetches the Supabase service key from Vault and exposes it to Gradle/Node pipelines.

.DESCRIPTION
    Designed for CI usage prior to running ./gradlew or Node smoke tests. The script reads the specified field
    (default: `serviceKey`) from a Vault KV path such as `kv/selfservice/platform/supabase/qa/service`, masks the
    value, and exports it as `SUPABASE_SERVICE_KEY` (configurable via -EnvVariable). Optionally emits Azure DevOps
    and GitHub Actions pipeline variables to make the secret available to subsequent steps.

.EXAMPLE
    pwsh ./android/scripts/export-supabase-service-key.ps1 -Environment qa -EmitPipelineVariables

    Loads `kv/selfservice/platform/supabase/qa/service:serviceKey`, stores it in `SUPABASE_SERVICE_KEY`, masks the
    value and publishes it to both Azure DevOps and GitHub Actions contexts.

.NOTES
    - Requires HashiCorp Vault CLI with VAULT_ADDR/VAULT_TOKEN already configured.
    - Supports PowerShell 7+ on Windows/Linux/macOS.
    - Does not persist secrets on disk; only environment variables / pipeline variables.
#>

[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidateNotNullOrEmpty()]
    [string]$Environment,

    [Parameter()]
    [ValidateNotNullOrEmpty()]
    [string]$VaultPathTemplate = "kv/selfservice/platform/supabase/{env}/service",

    [Parameter()]
    [ValidateNotNullOrEmpty()]
    [string]$SecretField = "serviceKey",

    [Parameter()]
    [ValidateNotNullOrEmpty()]
    [string]$EnvVariable = "SUPABASE_SERVICE_KEY",

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
    Write-Host "[export-supabase-service-key] Dry run: would fetch '$SecretField' from '$vaultPath' and export it as '$EnvVariable'."
    return
}

if (-not (Get-Command vault -ErrorAction SilentlyContinue)) {
    throw "The 'vault' CLI is not available in PATH. Install HashiCorp Vault CLI before running this script."
}

$vaultArgs = @("kv", "get", "-field=$SecretField", $vaultPath)
Write-Host "[export-supabase-service-key] Fetching '$SecretField' from '$vaultPath'..."
$secret = & vault @vaultArgs
if ($LASTEXITCODE -ne 0) {
    throw "Failed to read field '$SecretField' from '$vaultPath' via Vault CLI."
}
$secret = $secret.Trim()
if (-not $secret) {
    throw "Received empty value for '$SecretField' at '$vaultPath'."
}

Set-Item -Path "env:$EnvVariable" -Value $secret
Write-Host "[export-supabase-service-key] Environment variable '$EnvVariable' populated (value masked)."

if ($EmitPipelineVariables) {
    Set-AzurePipelineVariable -Name $EnvVariable -Value $secret
    Set-GitHubEnvVariable -Name $EnvVariable -Value $secret
}

Write-Host "[export-supabase-service-key] Done. Run './gradlew :app:checkSupabaseServiceKey -Pkiosk.environment=$normalizedEnv' next."
