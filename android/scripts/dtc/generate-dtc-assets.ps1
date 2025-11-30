[CmdletBinding()]
param(
    [Parameter()][string]$SummaryCsv = "tmp/dtc-summary.csv",
    [Parameter()][string]$BrandCsv = "tmp/dtc-brand-codes.csv",
    [Parameter()][string]$IndexOutput = "feature-obd-core/src/main/assets/dtc-index.json",
    [Parameter()][string]$BrandOutputDir = "feature-obd-core/src/main/assets/dtc-brand-overrides",
    [Parameter()][string]$ResourceIndexOutput = "feature-obd-core/src/main/resources/com/selfservice/obd/core/dtc/dtc-index.json",
    [Parameter()][string]$ResourceBrandOutputDir = "feature-obd-core/src/main/resources/com/selfservice/obd/core/dtc/dtc-brand-overrides"
)

$ErrorActionPreference = "Stop"

function Resolve-ExistingPath {
    param([string]$Path, [switch]$AllowMissing)
    if ([string]::IsNullOrWhiteSpace($Path)) {
        if ($AllowMissing) { return $null }
        throw "Path parameter is empty"
    }
    if (-not (Test-Path -LiteralPath $Path)) {
        if ($AllowMissing) { return $null }
        throw "File not found: $Path"
    }
    return [System.IO.Path]::GetFullPath($Path)
}

function Normalize-Text {
    param([string]$Value)
    if ([string]::IsNullOrWhiteSpace($Value)) { return $null }
    $collapsed = ($Value -replace "\s+", " ").Trim()
    if ($collapsed.Length -eq 0) { return $null }
    return $collapsed
}

function Map-SystemFromClass {
    param([string]$ClassCode)
    switch (($ClassCode ?? "").ToUpperInvariant()) {
        "P" { return "powertrain" }
        "B" { return "body" }
        "C" { return "chassis" }
        "U" { return "network" }
        default { return "unknown" }
    }
}

function Normalize-Status {
    param([string]$Status)
    $normalized = ($Status ?? "").ToLowerInvariant()
    if ([string]::IsNullOrWhiteSpace($normalized)) { return "unknown" }
    switch ($normalized) {
        "both" { return "both" }
        "app-only" { return "app-only" }
        "base-only" { return "base-only" }
        default { return "unknown" }
    }
}

function Get-SourcesForStatus {
    param([string]$Status)
    switch ($Status) {
        "both" { return @("dtcmapping.json", "dtc-codes.json") }
        "base-only" { return @("dtcmapping.json") }
        "app-only" { return @("dtc-codes.json") }
        default { return @() }
    }
}

function Build-Details {
    param([string]$Preferred, [string]$Base, [string]$App)
    $details = [ordered]@{}
    if ($Preferred) { $details.preferred = $Preferred }
    if ($Base) { $details.base = $Base }
    if ($App) { $details.app = $App }
    if ($details.Count -eq 0) { return $null }
    return $details
}

function Ensure-Directory {
    param([string]$Path)
    if (-not (Test-Path -LiteralPath $Path)) {
        New-Item -ItemType Directory -Path $Path -Force | Out-Null
    }
    return [System.IO.Path]::GetFullPath($Path)
}

function Get-BrandSlug {
    param([string]$Brand)
    if ([string]::IsNullOrWhiteSpace($Brand)) { return "brand" }
    $slug = $Brand.ToLowerInvariant()
    $slug = $slug -replace "[^a-z0-9]+", "-"
    $slug = $slug.Trim('-')
    if ([string]::IsNullOrWhiteSpace($slug)) { return "brand" }
    return $slug
}

function ConvertTo-JsonFile {
    param($Data, [string]$Path)
    $targetPath = [System.IO.Path]::GetFullPath($Path)
    $parent = Split-Path -Parent $targetPath
    if ($parent -and -not (Test-Path -LiteralPath $parent)) {
        New-Item -ItemType Directory -Path $parent -Force | Out-Null
    }
    $json = ($Data | ConvertTo-Json -Depth 16)
    Set-Content -Path $targetPath -Value ($json + [Environment]::NewLine) -Encoding utf8
}

$summaryPath = Resolve-ExistingPath -Path $SummaryCsv
$brandPath = Resolve-ExistingPath -Path $BrandCsv -AllowMissing
$brandDir = Ensure-Directory -Path $BrandOutputDir
$resourceIndexPath = $null
if (-not [string]::IsNullOrWhiteSpace($ResourceIndexOutput)) {
    $resourceIndexPath = [System.IO.Path]::GetFullPath($ResourceIndexOutput)
}
$resourceBrandDir = $null
if (-not [string]::IsNullOrWhiteSpace($ResourceBrandOutputDir)) {
    $resourceBrandDir = Ensure-Directory -Path $ResourceBrandOutputDir
}

$generationTimestamp = [DateTime]::UtcNow.ToString("o")

$summaryRows = Import-Csv -Path $summaryPath
$brandRows = @()
if ($brandPath) {
    $brandRows = Import-Csv -Path $brandPath
}

$brandGroups = @{}
$brandLookup = @{}

foreach ($row in $brandRows) {
    $code = Normalize-Text $row.Code
    $brand = Normalize-Text $row.Brand
    if (-not $code -or -not $brand) { continue }
    $classCode = Normalize-Text $row.Class
    $system = Map-SystemFromClass $classCode
    $description = Normalize-Text $row.Description
    $sourceFile = Normalize-Text $row.SourceFile

    $entry = [ordered]@{
        code   = $code.ToUpperInvariant()
        class  = ($classCode ?? "").ToUpperInvariant()
        system = $system
    }
    if ($description) { $entry.description = $description }
    if ($sourceFile) { $entry.source = $sourceFile }

    if (-not $brandGroups.ContainsKey($brand)) {
        $brandGroups[$brand] = New-Object System.Collections.ArrayList
    }
    $null = $brandGroups[$brand].Add($entry)

    if (-not $brandLookup.ContainsKey($entry.code)) {
        $brandLookup[$entry.code] = New-Object System.Collections.ArrayList
    }
    if (-not $brandLookup[$entry.code].Contains($brand)) {
        $null = $brandLookup[$entry.code].Add($brand)
    }
}

$entries = New-Object System.Collections.ArrayList
foreach ($row in ($summaryRows | Sort-Object Code)) {
    $code = Normalize-Text $row.Code
    if (-not $code) { continue }
    $classCode = Normalize-Text $row.Class
    $system = Map-SystemFromClass $classCode
    $status = Normalize-Status $row.SourceStatus
    $sources = [string[]](Get-SourcesForStatus $status)

    $preferred = Normalize-Text $row.DescriptionPreferred
    $baseDescription = Normalize-Text $row.DescriptionBase
    $appDescription = Normalize-Text $row.DescriptionApp

    $primary = $preferred
    if (-not $primary) { $primary = $baseDescription }
    if (-not $primary) { $primary = $appDescription }

    $details = Build-Details -Preferred $preferred -Base $baseDescription -App $appDescription

    $brandRefs = @()
    $codeKey = $code.ToUpperInvariant()
    if ($brandLookup.ContainsKey($codeKey)) {
        $brandRefs = @($brandLookup[$codeKey] | Sort-Object -Unique)
    }

    $entry = [ordered]@{
        code        = $codeKey
        class       = ($classCode ?? "").ToUpperInvariant()
        system      = $system
        status      = $status
        sources     = $sources
        description = $primary
    }
    if ($details) { $entry.details = $details }
    if ($brandRefs.Count -gt 0) { $entry.brandOverrides = $brandRefs }

    $null = $entries.Add($entry)
}

$indexPayload = [ordered]@{
    generatedAt = $generationTimestamp
    source      = [ordered]@{
        summaryCsv = $summaryPath
        brandCsv   = $brandPath
    }
    entries     = $entries
}

ConvertTo-JsonFile -Data $indexPayload -Path $IndexOutput
if ($resourceIndexPath) {
    ConvertTo-JsonFile -Data $indexPayload -Path $resourceIndexPath
}

if (Test-Path -LiteralPath $brandDir) {
    Get-ChildItem -Path $brandDir -Filter "*.json" -File -ErrorAction SilentlyContinue | Remove-Item -Force
}
if ($resourceBrandDir -and (Test-Path -LiteralPath $resourceBrandDir)) {
    Get-ChildItem -Path $resourceBrandDir -Filter "*.json" -File -ErrorAction SilentlyContinue | Remove-Item -Force
}

foreach ($brand in ($brandGroups.Keys | Sort-Object)) {
    $slug = Get-BrandSlug -Brand $brand
    $payload = [ordered]@{
        brand       = $brand
        slug        = $slug
        generatedAt = $generationTimestamp
        entries     = ($brandGroups[$brand] | Sort-Object code)
    }
    $target = Join-Path $brandDir "$slug.json"
    ConvertTo-JsonFile -Data $payload -Path $target
    if ($resourceBrandDir) {
        $resourceTarget = Join-Path $resourceBrandDir "$slug.json"
        ConvertTo-JsonFile -Data $payload -Path $resourceTarget
    }
}

Write-Host ("Generated {0} index entries and {1} brand files." -f $entries.Count, $brandGroups.Count)
