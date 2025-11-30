[CmdletBinding()]
param(
    [string]$OutputCsv = "tmp/dtc-brand-codes.csv"
)

$ErrorActionPreference = 'Stop'

function Get-RepoRoot {
    return Get-Item -LiteralPath (Join-Path $PSScriptRoot "..\..")
}

function Resolve-RepoPath([string]$relative) {
    $root = Get-RepoRoot
    return Join-Path $root.FullName $relative
}

function Get-RelativePath([string]$absolute) {
    $root = (Get-RepoRoot).FullName
    $normalizedRoot = $root.TrimEnd('\')
    if ($absolute.StartsWith($normalizedRoot)) {
        return $absolute.Substring($normalizedRoot.Length + 1)
    }
    return $absolute
}

$sourceDefinitions = @(
    @{ Brand = 'BMW'; RelativePath = 'base/All BMW OBD2 Codes List (7)'; Pattern = '*.txt' },
    @{ Brand = 'Lexus'; RelativePath = 'base/All Lexus OBD2 Codes List (5)'; Pattern = '*.txt' },
    @{ Brand = 'Toyota'; RelativePath = 'base/All Toyota OBD2 Codes List (2)'; Pattern = '*.txt' },
    @{ Brand = 'Nissan'; RelativePath = 'base/All Nissan OBD2 Codes List (3)'; Pattern = '*.txt' },
    @{ Brand = 'Generic'; RelativePath = 'base/Generic OBD2 Codes List (6)'; Pattern = '*.txt' },
    @{ Brand = 'Manufacturer'; RelativePath = 'base/All Manufacturer-specific OBD2 Codes (4)'; Pattern = '*.txt' }
)

$outPath = Resolve-RepoPath $OutputCsv
$outDir = Split-Path -Parent $outPath
if (-not (Test-Path -LiteralPath $outDir)) {
    New-Item -ItemType Directory -Path $outDir -Force | Out-Null
}

$pattern = '(?<code>[PBCU][0-9A-F]{4})\s+(?<desc>.*?)(?=(?:\s+[PBCU][0-9A-F]{4}\s+)|$)'
$regexOptions = [System.Text.RegularExpressions.RegexOptions]::IgnoreCase

$brandMaps = @{}
foreach ($definition in $sourceDefinitions) {
    $brand = $definition.Brand
    $dirPath = Resolve-RepoPath $definition.RelativePath
    if (-not (Test-Path -LiteralPath $dirPath)) {
        Write-Warning "Source directory not found for ${brand}: $dirPath"
        continue
    }

    $files = Get-ChildItem -LiteralPath $dirPath -File -Recurse -Filter $definition.Pattern
    if (-not $files) {
        Write-Warning "No text files found for ${brand} under $dirPath"
        continue
    }

    if (-not $brandMaps.ContainsKey($brand)) {
        $brandMaps[$brand] = @{}
    }

    foreach ($file in $files) {
        $relativeFile = Get-RelativePath $file.FullName
        $lines = Get-Content -LiteralPath $file.FullName -Encoding UTF8
        foreach ($line in $lines) {
            if ([string]::IsNullOrWhiteSpace($line)) { continue }
            $matches = [System.Text.RegularExpressions.Regex]::Matches($line, $pattern, $regexOptions)
            if ($matches.Count -eq 0) { continue }
            foreach ($match in $matches) {
                $code = $match.Groups['code'].Value.ToUpperInvariant()
                $description = $match.Groups['desc'].Value.Trim()
                if (-not $brandMaps[$brand].ContainsKey($code)) {
                    $brandMaps[$brand][$code] = [pscustomobject]@{
                        Code        = $code
                        Class       = $code.Substring(0, 1)
                        Brand       = $brand
                        Description = $description
                        SourceFile  = $relativeFile
                    }
                }
            }
        }
    }
}

$records = $brandMaps.Values | ForEach-Object { $_.Values } | Sort-Object Brand, Code
if (-not $records -or $records.Count -eq 0) {
    throw "No DTC records were extracted from donor text files."
}

$records | Export-Csv -Path $outPath -NoTypeInformation -Encoding UTF8

Write-Output "CSV saved to: $outPath"
$brandGroups = $records | Group-Object Brand | Sort-Object Name
foreach ($brandGroup in $brandGroups) {
    Write-Output ("Brand {0}: {1}" -f $brandGroup.Name, $brandGroup.Count)
    $classGroups = $brandGroup.Group | Group-Object Class | Sort-Object Name
    foreach ($classGroup in $classGroups) {
        Write-Output ("  Class {0}: {1}" -f $classGroup.Name, $classGroup.Count)
    }
}
