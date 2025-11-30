#Requires -Version 5.1
<#
.SYNOPSIS
    Kiosk maintenance utility script - performs routine maintenance tasks

.DESCRIPTION
    This PowerShell script handles various maintenance operations for the kiosk system:
    - Log rotation and cleanup
    - APK metrics collection
    - Dependency inventory
    - Session log validation
    - Checksum verification

.PARAMETER Task
    The maintenance task to perform. Options:
    - LogRotation: Rotate and archive old logs
    - CollectMetrics: Collect APK size and performance metrics
    - ValidateLogs: Validate session logs and checksums
    - CleanupArtifacts: Remove build artifacts and temp files
    - All: Perform all maintenance tasks

.PARAMETER DryRun
    If specified, shows what would be done without making changes

.EXAMPLE
    .\kiosk-maintenance.ps1 -Task LogRotation
    Rotates logs older than 30 days

.EXAMPLE
    .\kiosk-maintenance.ps1 -Task All -DryRun
    Shows all maintenance operations without executing them

.NOTES
    Author: GitHub Copilot
    Version: 1.0
    Created: 24.11.2025 (Session 3G)
    Category: G (Documentation/Infrastructure)
#>

[CmdletBinding()]
param(
    [Parameter(Mandatory=$true)]
    [ValidateSet('LogRotation', 'CollectMetrics', 'ValidateLogs', 'CleanupArtifacts', 'All')]
    [string]$Task,

    [Parameter(Mandatory=$false)]
    [switch]$DryRun
)

$ErrorActionPreference = 'Stop'
$RepoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)

# Color output functions
function Write-TaskHeader {
    param([string]$Message)
    Write-Host "`n=== $Message ===" -ForegroundColor Cyan
}

function Write-Success {
    param([string]$Message)
    Write-Host "✓ $Message" -ForegroundColor Green
}

function Write-Warning {
    param([string]$Message)
    Write-Host "⚠ $Message" -ForegroundColor Yellow
}

function Write-Info {
    param([string]$Message)
    Write-Host "  $Message" -ForegroundColor Gray
}

# Task: Log Rotation
function Invoke-LogRotation {
    Write-TaskHeader "Log Rotation"
    
    $LogsDir = Join-Path $RepoRoot "logs"
    $ArchiveDir = Join-Path $LogsDir ".archive"
    $RetentionDays = 90
    $CutoffDate = (Get-Date).AddDays(-$RetentionDays)

    if (-not (Test-Path $LogsDir)) {
        Write-Warning "Logs directory not found: $LogsDir"
        return
    }

    # Create archive directory if needed
    if (-not $DryRun -and -not (Test-Path $ArchiveDir)) {
        New-Item -ItemType Directory -Path $ArchiveDir -Force | Out-Null
        Write-Info "Created archive directory: $ArchiveDir"
    }

    # Find old issue logs (session logs are kept permanently)
    $IssuesDir = Join-Path $LogsDir "issues"
    if (Test-Path $IssuesDir) {
        $OldIssues = Get-ChildItem -Path $IssuesDir -Filter "*.json" | 
            Where-Object { $_.LastWriteTime -lt $CutoffDate }

        if ($OldIssues) {
            Write-Info "Found $($OldIssues.Count) issue logs older than $RetentionDays days"
            foreach ($file in $OldIssues) {
                $targetPath = Join-Path $ArchiveDir $file.Name
                if ($DryRun) {
                    Write-Info "Would archive: $($file.Name)"
                } else {
                    Move-Item -Path $file.FullName -Destination $targetPath -Force
                    # Also move .meta file
                    $metaFile = "$($file.FullName).meta"
                    if (Test-Path $metaFile) {
                        Move-Item -Path $metaFile -Destination "$targetPath.meta" -Force
                    }
                    Write-Success "Archived: $($file.Name)"
                }
            }
        } else {
            Write-Info "No old issue logs to archive"
        }
    }

    Write-Success "Log rotation complete"
}

# Task: Collect Metrics
function Invoke-CollectMetrics {
    Write-TaskHeader "Collect APK Metrics"
    
    $AndroidDir = Join-Path $RepoRoot "android"
    $AppDir = Join-Path $AndroidDir "app"
    $BuildDir = Join-Path $AppDir "build/outputs/apk/debug"

    if (-not (Test-Path $BuildDir)) {
        Write-Warning "Build directory not found. Run ./gradlew assembleDebug first"
        return
    }

    $ApkFiles = Get-ChildItem -Path $BuildDir -Filter "*.apk"
    if ($ApkFiles) {
        Write-Info "Found APK files:"
        foreach ($apk in $ApkFiles) {
            $sizeMB = [math]::Round($apk.Length / 1MB, 2)
            Write-Info "  $($apk.Name): $sizeMB MB"
        }
        Write-Success "Metrics collected"
    } else {
        Write-Warning "No APK files found in $BuildDir"
    }
}

# Task: Validate Logs
function Invoke-ValidateLogs {
    Write-TaskHeader "Validate Session Logs"
    
    $SessionsDir = Join-Path $RepoRoot "logs/sessions"
    if (-not (Test-Path $SessionsDir)) {
        Write-Warning "Sessions directory not found: $SessionsDir"
        return
    }

    $JsonFiles = Get-ChildItem -Path $SessionsDir -Filter "*.json"
    $ValidationErrors = 0

    foreach ($jsonFile in $JsonFiles) {
        $metaFile = "$($jsonFile.FullName).meta"
        
        # Check if .meta file exists
        if (-not (Test-Path $metaFile)) {
            Write-Warning "Missing .meta file for: $($jsonFile.Name)"
            $ValidationErrors++
            continue
        }

        # Verify checksum
        try {
            $json = Get-Content -Path $jsonFile.FullName -Raw | ConvertFrom-Json
            $meta = Get-Content -Path $metaFile -Raw | ConvertFrom-Json

            # Remove metadata field and calculate MD5
            $jsonNoMetadata = $json | Select-Object -Property * -ExcludeProperty metadata
            $jsonString = $jsonNoMetadata | ConvertTo-Json -Depth 100 -Compress
            $md5 = [System.Security.Cryptography.MD5]::Create()
            $hash = $md5.ComputeHash([System.Text.Encoding]::UTF8.GetBytes($jsonString))
            $calculatedChecksum = [System.BitConverter]::ToString($hash).Replace("-", "").ToLower()

            if ($meta.checksum.value -eq $calculatedChecksum) {
                Write-Success "$($jsonFile.Name): checksum valid"
            } else {
                Write-Warning "$($jsonFile.Name): checksum mismatch"
                Write-Info "  Expected: $($meta.checksum.value)"
                Write-Info "  Calculated: $calculatedChecksum"
                $ValidationErrors++
            }
        } catch {
            Write-Warning "$($jsonFile.Name): validation error: $_"
            $ValidationErrors++
        }
    }

    if ($ValidationErrors -eq 0) {
        Write-Success "All session logs validated successfully"
    } else {
        Write-Warning "$ValidationErrors validation error(s) found"
    }
}

# Task: Cleanup Artifacts
function Invoke-CleanupArtifacts {
    Write-TaskHeader "Cleanup Build Artifacts"
    
    $ArtifactPatterns = @(
        "android/**/build",
        "03-apps/**/node_modules",
        "packages/**/node_modules",
        "*.tmp",
        "*.log"
    )

    foreach ($pattern in $ArtifactPatterns) {
        $fullPattern = Join-Path $RepoRoot $pattern
        $items = Get-ChildItem -Path (Split-Path $fullPattern -Parent) -Filter (Split-Path $fullPattern -Leaf) -Recurse -Force -ErrorAction SilentlyContinue

        if ($items) {
            Write-Info "Found $(($items | Measure-Object).Count) items matching: $pattern"
            foreach ($item in $items) {
                if ($DryRun) {
                    Write-Info "Would remove: $($item.FullName)"
                } else {
                    Remove-Item -Path $item.FullName -Recurse -Force
                    Write-Success "Removed: $($item.FullName)"
                }
            }
        }
    }

    Write-Success "Cleanup complete"
}

# Main execution
try {
    Write-Host "`nKiosk Maintenance Utility" -ForegroundColor Cyan
    Write-Host "Repository: $RepoRoot" -ForegroundColor Gray
    if ($DryRun) {
        Write-Host "DRY RUN MODE - No changes will be made`n" -ForegroundColor Yellow
    }

    switch ($Task) {
        'LogRotation' { Invoke-LogRotation }
        'CollectMetrics' { Invoke-CollectMetrics }
        'ValidateLogs' { Invoke-ValidateLogs }
        'CleanupArtifacts' { Invoke-CleanupArtifacts }
        'All' {
            Invoke-LogRotation
            Invoke-CollectMetrics
            Invoke-ValidateLogs
            Invoke-CleanupArtifacts
        }
    }

    Write-Host "`n✓ Task completed successfully" -ForegroundColor Green
} catch {
    Write-Host "`n✗ Task failed: $_" -ForegroundColor Red
    exit 1
}
