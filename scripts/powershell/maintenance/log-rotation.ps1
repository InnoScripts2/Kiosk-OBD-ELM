#Requires -Version 5.1
<#
.SYNOPSIS
    Log rotation utility for kiosk system logs

.DESCRIPTION
    This script manages log file rotation and archival for the kiosk system.
    - Rotates session logs (kept permanently)
    - Archives issue logs older than retention period
    - Compresses archived logs
    - Maintains `.meta` files alongside logs

.PARAMETER RetentionDays
    Number of days to retain issue logs before archiving (default: 90)

.PARAMETER Compress
    If specified, compresses archived logs to .zip format

.PARAMETER DryRun
    If specified, shows what would be done without making changes

.EXAMPLE
    .\log-rotation.ps1
    Archives issue logs older than 90 days

.EXAMPLE
    .\log-rotation.ps1 -RetentionDays 30 -Compress -DryRun
    Shows what would be archived with 30-day retention and compression

.NOTES
    Author: GitHub Copilot
    Version: 1.0
    Created: 24.11.2025 (Session 3G)
    Category: G (Documentation/Infrastructure)
    
    Retention Policy:
    - Session logs: Permanent (never rotated)
    - Issue logs: Archived after RetentionDays, deleted after 1 year in archive
#>

[CmdletBinding()]
param(
    [Parameter(Mandatory=$false)]
    [int]$RetentionDays = 90,

    [Parameter(Mandatory=$false)]
    [switch]$Compress,

    [Parameter(Mandatory=$false)]
    [switch]$DryRun
)

$ErrorActionPreference = 'Stop'
$RepoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$LogsDir = Join-Path $RepoRoot "logs"

# Color output
function Write-Header {
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

# Initialize
Write-Header "Log Rotation Utility"
Write-Info "Repository: $RepoRoot"
Write-Info "Retention: $RetentionDays days"
Write-Info "Compress: $Compress"
if ($DryRun) {
    Write-Warning "DRY RUN MODE - No changes will be made"
}

if (-not (Test-Path $LogsDir)) {
    Write-Error "Logs directory not found: $LogsDir"
    exit 1
}

# Session logs (permanent)
Write-Header "Session Logs"
$SessionsDir = Join-Path $LogsDir "sessions"
if (Test-Path $SessionsDir) {
    $sessionLogs = Get-ChildItem -Path $SessionsDir -Filter "*.json"
    Write-Info "Found $($sessionLogs.Count) session logs (kept permanently)"
    Write-Success "Session logs: no rotation needed"
} else {
    Write-Warning "Sessions directory not found: $SessionsDir"
}

# Issue logs (archive old ones)
Write-Header "Issue Logs"
$IssuesDir = Join-Path $LogsDir "issues"
$ArchiveDir = Join-Path $IssuesDir ".archive"
$CutoffDate = (Get-Date).AddDays(-$RetentionDays)

if (-not (Test-Path $IssuesDir)) {
    Write-Warning "Issues directory not found: $IssuesDir"
} else {
    # Create archive directory
    if (-not $DryRun -and -not (Test-Path $ArchiveDir)) {
        New-Item -ItemType Directory -Path $ArchiveDir -Force | Out-Null
        Write-Info "Created archive directory"
    }

    # Find old resolved issues
    $issueLogs = Get-ChildItem -Path $IssuesDir -Filter "*.json" | 
        Where-Object { $_.FullName -notmatch '\.archive' }

    $archivedCount = 0
    $retainedCount = 0

    foreach ($log in $issueLogs) {
        try {
            $content = Get-Content -Path $log.FullName -Raw | ConvertFrom-Json
            $issueDate = [DateTime]::Parse($content.date)
            $isResolved = $content.status -in @('resolved', 'wontfix')
            
            $shouldArchive = $isResolved -and $issueDate -lt $CutoffDate

            if ($shouldArchive) {
                $archivedCount++
                $targetPath = Join-Path $ArchiveDir $log.Name
                
                if ($DryRun) {
                    Write-Info "Would archive: $($log.Name) (resolved on $($content.resolvedDate))"
                } else {
                    # Move JSON and .meta files
                    Move-Item -Path $log.FullName -Destination $targetPath -Force
                    $metaFile = "$($log.FullName).meta"
                    if (Test-Path $metaFile) {
                        Move-Item -Path $metaFile -Destination "$targetPath.meta" -Force
                    }
                    
                    Write-Success "Archived: $($log.Name)"
                }
            } else {
                $retainedCount++
                $reason = if (-not $isResolved) { "still open" } else { "within retention" }
                Write-Info "Retained: $($log.Name) ($reason)"
            }
        } catch {
            Write-Warning "Error processing $($log.Name): $_"
        }
    }

    Write-Info "Archived: $archivedCount, Retained: $retainedCount"
}

# Compress archived logs
if ($Compress -and (Test-Path $ArchiveDir)) {
    Write-Header "Compression"
    $archiveDate = Get-Date -Format "yyyy-MM-dd"
    $zipFile = Join-Path (Split-Path $ArchiveDir) "archive-$archiveDate.zip"
    
    if ($DryRun) {
        Write-Info "Would create archive: $zipFile"
    } else {
        try {
            Compress-Archive -Path "$ArchiveDir\*" -DestinationPath $zipFile -Force
            Write-Success "Created archive: $zipFile"
            
            # Remove uncompressed files
            Remove-Item -Path "$ArchiveDir\*" -Recurse -Force
            Write-Info "Removed uncompressed files from .archive"
        } catch {
            Write-Warning "Compression failed: $_"
        }
    }
}

# Cleanup old archives (>1 year)
Write-Header "Archive Cleanup"
$archiveRetentionDays = 365
$archiveCutoffDate = (Get-Date).AddDays(-$archiveRetentionDays)

if (Test-Path $ArchiveDir) {
    $oldArchives = Get-ChildItem -Path $ArchiveDir -Filter "*.zip" | 
        Where-Object { $_.LastWriteTime -lt $archiveCutoffDate }

    if ($oldArchives) {
        Write-Info "Found $($oldArchives.Count) archives older than $archiveRetentionDays days"
        foreach ($archive in $oldArchives) {
            if ($DryRun) {
                Write-Info "Would delete: $($archive.Name)"
            } else {
                Remove-Item -Path $archive.FullName -Force
                Write-Success "Deleted: $($archive.Name)"
            }
        }
    } else {
        Write-Info "No old archives to delete"
    }
}

# Summary
Write-Header "Summary"
Write-Info "Retention policy: Issue logs archived after $RetentionDays days"
Write-Info "Archive cleanup: Archives deleted after 365 days"
Write-Success "Log rotation complete"

if ($DryRun) {
    Write-Host "`n⚠ DRY RUN COMPLETE - No changes were made" -ForegroundColor Yellow
} else {
    Write-Host "`n✓ Rotation complete" -ForegroundColor Green
}
