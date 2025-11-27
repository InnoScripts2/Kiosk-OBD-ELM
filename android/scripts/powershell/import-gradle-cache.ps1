<#!
.SYNOPSIS
Импортирует артефакт gradle-caches из GitHub Actions в локальный Gradle home.

.DESCRIPTION
Скрипт распаковывает загруженный архив (например, gradle-caches.zip), извлекает
каталоги .gradle/caches и .gradle/wrapper и копирует их в указанный Gradle home.
Поддерживает режим DryRun для проверки действий без изменений.

.PARAMETER ArchivePath
Путь к zip-архиву, скачанному из GitHub Actions (gradle-caches*.zip).

.PARAMETER GradleHome
Необязательный путь к каталогу Gradle. По умолчанию берётся переменная
GRADLE_USER_HOME или %USERPROFILE%\.gradle.

.PARAMETER DryRun
Если указан, скрипт выводит план операций без фактического копирования файлов.

.EXAMPLE
PS> pwsh ./android/scripts/powershell/import-gradle-cache.ps1 -ArchivePath .\gradle-caches.zip

.EXAMPLE
PS> pwsh ./android/scripts/powershell/import-gradle-cache.ps1 -ArchivePath .\gradle-caches.zip -GradleHome D:\gradle -DryRun
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$ArchivePath,

    [Parameter(Mandatory = $false)]
    [string]$GradleHome,

    [Parameter(Mandatory = $false)]
    [switch]$DryRun
)

function Write-Step {
    param([string]$Message)
    Write-Host "[gradle-cache] $Message"
}

if (-not (Test-Path -LiteralPath $ArchivePath)) {
    throw "Архив '$ArchivePath' не найден."
}

$resolvedArchive = (Resolve-Path -LiteralPath $ArchivePath).Path
if (-not $GradleHome) {
    if ($env:GRADLE_USER_HOME) {
        $GradleHome = $env:GRADLE_USER_HOME
    }
    else {
        $GradleHome = Join-Path $HOME ".gradle"
    }
}
if (Test-Path -LiteralPath $GradleHome) {
    $GradleHome = (Resolve-Path -LiteralPath $GradleHome).Path
}
$timestamp = Get-Date -Format "yyyyMMddHHmmss"
$tempRoot = Join-Path ([System.IO.Path]::GetTempPath()) "gradle-cache-$timestamp"

Write-Step "Распаковка архива '$resolvedArchive' в '$tempRoot'"
if (-not $DryRun) {
    if (Test-Path -LiteralPath $tempRoot) {
        Remove-Item -LiteralPath $tempRoot -Recurse -Force
    }
    Expand-Archive -LiteralPath $resolvedArchive -DestinationPath $tempRoot -Force
}

$extractedGradle = Join-Path $tempRoot ".gradle"
if (-not (Test-Path -LiteralPath $extractedGradle)) {
    # Некоторые артефакты содержат корневую папку gradle-caches/.gradle
    $maybeRoot = Get-ChildItem -LiteralPath $tempRoot -Directory -Recurse | Where-Object { $_.Name -eq ".gradle" } | Select-Object -First 1
    if ($maybeRoot) {
        $extractedGradle = $maybeRoot.FullName
    }
    else {
        throw "В архиве отсутствует каталог .gradle."
    }
}

$sourceCaches = Join-Path $extractedGradle "caches"
$sourceWrapper = Join-Path $extractedGradle "wrapper"
if (-not (Test-Path -LiteralPath $sourceCaches)) {
    throw "В архиве отсутствует .gradle/caches."
}
if (-not (Test-Path -LiteralPath $sourceWrapper)) {
    throw "В архиве отсутствует .gradle/wrapper."
}

if (-not (Test-Path -LiteralPath $GradleHome)) {
    Write-Step "Создание каталога Gradle home '$GradleHome'"
    if (-not $DryRun) {
        New-Item -ItemType Directory -Path $GradleHome -Force | Out-Null
    }
}

function Copy-GradleContent {
    param(
        [string]$Source,
        [string]$Target
    )
    Write-Step "Обновление '$Target'"
    if (-not $DryRun) {
        if (Test-Path -LiteralPath $Target) {
            $backup = "$Target.bak.$timestamp"
            Write-Step "Резервная копия -> $backup"
            Remove-Item -LiteralPath $backup -Recurse -Force -ErrorAction SilentlyContinue
            Move-Item -LiteralPath $Target -Destination $backup
        }
        Copy-Item -LiteralPath $Source -Destination $Target -Recurse -Force
    }
}

Copy-GradleContent -Source $sourceCaches -Target (Join-Path $GradleHome "caches")
Copy-GradleContent -Source $sourceWrapper -Target (Join-Path $GradleHome "wrapper")

if (-not $DryRun) {
    Write-Step "Очистка временного каталога"
    Remove-Item -LiteralPath $tempRoot -Recurse -Force -ErrorAction SilentlyContinue
}

Write-Step "Импорт завершён. Запустите './gradlew --offline assembleDebug' из каталога 'android'."
