[CmdletBinding()]
param(
    [string]$BaseJson = "base/dtcmapping.json",
    [string]$AppJson = "feature-obd-core/src/main/assets/dtc-codes.json",
    [string]$OutputCsv = "tmp/dtc-summary.csv"
)

$ErrorActionPreference = 'Stop'

function Resolve-RepoPath([string]$relative) {
    $root = Get-Item -LiteralPath (Join-Path $PSScriptRoot "..\..")
    $path = Join-Path $root.FullName $relative
    return $path
}

$basePath = Resolve-RepoPath $BaseJson
$appPath = Resolve-RepoPath $AppJson
$outPath = Resolve-RepoPath $OutputCsv
$outDir = Split-Path -Parent $outPath
if (-not (Test-Path -LiteralPath $outDir)) {
    New-Item -ItemType Directory -Path $outDir -Force | Out-Null
}

if (-not (Test-Path -LiteralPath $basePath)) {
    throw "Base JSON not found: $basePath"
}

if (-not (Test-Path -LiteralPath $appPath)) {
    Write-Warning "App JSON not found: $appPath. Script will only use base data."
}

Write-Verbose "Loading base catalog from $basePath"
$baseJsonRaw = Get-Content -LiteralPath $basePath -Raw -Encoding UTF8
$baseObj = $baseJsonRaw | ConvertFrom-Json
$baseMap = @{}
foreach ($prop in $baseObj.PSObject.Properties) {
    $baseMap[$prop.Name] = [string]$prop.Value
}

$appMap = @{}
$appCount = 0
if (Test-Path -LiteralPath $appPath) {
    Write-Verbose "Loading app catalog from $appPath"
    $appJsonRaw = Get-Content -LiteralPath $appPath -Raw -Encoding UTF8
    $appObj = $appJsonRaw | ConvertFrom-Json
    if ($null -ne $appObj.dtcs) {
        foreach ($item in $appObj.dtcs) {
            if ($null -eq $item.code) { continue }
            $appMap[$item.code] = [string]$item.description
            $appCount++
        }
    }
}

$allCodes = [System.Collections.Generic.HashSet[string]]::new()
$baseMap.Keys | ForEach-Object { $null = $allCodes.Add($_) }
$appMap.Keys | ForEach-Object { $null = $allCodes.Add($_) }

$records = foreach ($code in ($allCodes | Sort-Object)) {
    $baseDesc = $null
    $appDesc = $null
    if ($baseMap.ContainsKey($code)) { $baseDesc = $baseMap[$code] }
    if ($appMap.ContainsKey($code)) { $appDesc = $appMap[$code] }
    $preferred = if ([string]::IsNullOrWhiteSpace($baseDesc)) { $appDesc } else { $baseDesc }
    $sourceStatus = switch ($true) {
        { $baseDesc -and $appDesc } { 'both'; break }
        { $baseDesc } { 'base-only'; break }
        { $appDesc } { 'app-only'; break }
        default { 'unknown' }
    }
    $class = if ($code.Length -gt 0) { $code.Substring(0, 1) } else { '' }
    [pscustomobject]@{
        Code                 = $code
        Class                = $class
        SourceStatus         = $sourceStatus
        DescriptionPreferred = $preferred
        DescriptionBase      = $baseDesc
        DescriptionApp       = $appDesc
    }
}

$records | Export-Csv -Path $outPath -NoTypeInformation -Encoding UTF8

$baseOnly = ($records | Where-Object { $_.SourceStatus -eq 'base-only' }).Count
$appOnly = ($records | Where-Object { $_.SourceStatus -eq 'app-only' }).Count
$overlap = ($records | Where-Object { $_.SourceStatus -eq 'both' }).Count
$total = $records.Count

Write-Output "Total codes: $total"
Write-Output "Base-only: $baseOnly"
Write-Output "App-only: $appOnly"
Write-Output "Overlap: $overlap"
Write-Output "App catalog size: $appCount"
Write-Output "CSV saved to: $outPath"

$classGroups = $records | Group-Object -Property Class | Sort-Object Name
foreach ($group in $classGroups) {
    Write-Output ("Class {0}: {1}" -f $group.Name, $group.Count)
}
