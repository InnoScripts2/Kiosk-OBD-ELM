#!/usr/bin/env pwsh
<#!
.SYNOPSIS
  Собирает фронтенд киоска и выгружает содержимое dist/ в Supabase Storage.

.DESCRIPTION
  Скрипт автоматизирует резервную публикацию `platform/ui/web/kiosk-frontend` в бакет
  Supabase Storage (например, `kiosk-ui`). Он собирает Vite-проект (если не указан
  `-SkipBuild`), опционально очищает целевой бакет и затем рекурсивно копирует весь dist
  через `supabase storage cp`. По умолчанию используется связанный проект
  `ddaunoxyguqiejrjtwsf` и профиль CLI `supabase`.

.PARAMETER UpdateAndroidString
    После публикации автоматически обновляет `app/src/main/res/values/strings.xml`
    (строка `kiosk_url_remote`) на фактический Supabase URL.

.PARAMETER AppendCacheBuster
    Добавляет `?v=<timestamp>` к публичному URL при обновлении строки Android. Можно
    передать собственное значение через `-CacheBusterToken`.

.PARAMETER CacheBusterToken
    Произвольный токен для busting-кеша. Если указан, перекрывает `-AppendCacheBuster`.

.EXAMPLE
  pwsh scripts/powershell/publish-kiosk-frontend.ps1

.EXAMPLE
  pwsh scripts/powershell/publish-kiosk-frontend.ps1 -SkipBuild -BucketName ui-dev -ClearBucket

.NOTES
  Требования: Supabase CLI (>= 2.62), `npm`, PowerShell 7+. Перед запуском выполните
  `supabase login` и `supabase link --project-ref ddaunoxyguqiejrjtwsf`.
#>
[CmdletBinding()]
param(
    [string]$BucketName = "kiosk-ui",
    [string]$ProjectRef = "ddaunoxyguqiejrjtwsf",
    [switch]$SkipBuild,
    [switch]$ClearBucket,
    [switch]$DryRun,
    [string]$SupabaseProfile = "supabase",
    [int]$ParallelJobs = 4,
    [string]$FrontendPath,
    [string]$DistPath,
    [switch]$UpdateAndroidString,
    [switch]$AppendCacheBuster,
    [string]$CacheBusterToken,
    [string]$AndroidStringsPath
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Write-Step {
    param([string]$Message)
    Write-Host "==> $Message" -ForegroundColor Cyan
}

function Set-FileUtf8NoBom {
    param(
        [string]$Path,
        [string]$Content
    )
    $encoding = New-Object System.Text.UTF8Encoding($false)
    [System.IO.File]::WriteAllText($Path, $Content, $encoding)
}

function Update-AndroidString {
    param(
        [string]$StringsPath,
        [string]$RemoteUrl
    )

    if (-not (Test-Path -Path $StringsPath)) {
        throw "Не найден strings.xml: $StringsPath"
    }

    $content = Get-Content -Path $StringsPath -Raw
    $pattern = '<string\s+name="kiosk_url_remote">.*?</string>'
    $escapedUrl = [System.Security.SecurityElement]::Escape($RemoteUrl)
    $replacement = "    <string name=\"kiosk_url_remote\">$escapedUrl</string>"

    $updated = [System.Text.RegularExpressions.Regex]::Replace(
        $content,
        $pattern,
        $replacement,
        [System.Text.RegularExpressions.RegexOptions]::Singleline
    )

    if ($updated -eq $content) {
        throw "Не удалось найти элемент kiosk_url_remote в $StringsPath"
    }

    if ($DryRun) {
        Write-Step "DRY RUN: обновление kiosk_url_remote пропущено"
    }
    else {
        Set-FileUtf8NoBom -Path $StringsPath -Content $updated
        Write-Step "Строка kiosk_url_remote обновлена: $RemoteUrl"
    }
}

function Invoke-ExternalCommand {
    param(
        [string]$FilePath,
        [string[]]$ArgumentList
    )
    $rendered = "$FilePath $($ArgumentList -join ' ')"
    Write-Host "$rendered" -ForegroundColor DarkGray
    if ($DryRun) { return }
    $process = Start-Process -FilePath $FilePath -ArgumentList $ArgumentList -NoNewWindow -Wait -PassThru
    if ($process.ExitCode -ne 0) {
        throw "Команда завершилась с кодом $($process.ExitCode): $rendered"
    }
}

if ($ParallelJobs -lt 1) {
    throw "Параметр -ParallelJobs должен быть >= 1."
}

$repoRoot = Resolve-Path -Path (Join-Path -Path $PSScriptRoot -ChildPath "..\..")
if (-not $FrontendPath) {
    $FrontendPath = Join-Path -Path $repoRoot -ChildPath "platform/ui/web/kiosk-frontend"
}
if (-not $DistPath) {
    $DistPath = Join-Path -Path $FrontendPath -ChildPath "dist"
}
if (-not $AndroidStringsPath) {
    $AndroidStringsPath = Join-Path -Path $repoRoot -ChildPath "app/src/main/res/values/strings.xml"
}

if (-not (Test-Path -Path $FrontendPath)) {
    throw "Не найден каталог фронтенда: $FrontendPath"
}

if (-not (Get-Command npm -ErrorAction SilentlyContinue)) {
    throw "npm недоступен в PATH"
}

if (-not (Get-Command supabase -ErrorAction SilentlyContinue)) {
    throw "Supabase CLI недоступен в PATH"
}

Write-Step "Репозиторий: $repoRoot"
Write-Step "Каталог фронтенда: $FrontendPath"

if (-not $SkipBuild) {
    Write-Step "Сборка Vite (npm run build)"
    Invoke-ExternalCommand -FilePath "npm" -ArgumentList @("--prefix", $FrontendPath, "run", "build")
}
else {
    Write-Step "Пропуск сборки по флагу -SkipBuild"
}

if (-not (Test-Path -Path $DistPath)) {
    throw "Каталог dist не найден после сборки: $DistPath"
}

$distFiles = Get-ChildItem -Path $DistPath -File -Recurse
if (-not $distFiles) {
    throw "Каталог dist пуст: $DistPath"
}

Write-Step ("Файлов в dist: {0}" -f $distFiles.Count)

$profileArgs = @()
if ($SupabaseProfile) {
    $profileArgs = @("--profile", $SupabaseProfile)
}

if ($ClearBucket) {
    Write-Step "Очистка бакета ss:///$BucketName (supabase storage rm -r)"
    Invoke-ExternalCommand -FilePath "supabase" -ArgumentList @($profileArgs + @("storage", "rm", "-r", "ss:///$BucketName"))
}
else {
    Write-Step "Очистка бакета пропущена (не указан -ClearBucket)"
}

Write-Step "Загрузка dist → ss:///$BucketName (supabase storage cp -r)"
$cpArgs = @($profileArgs + @("storage", "cp", "-r", $DistPath, "ss:///$BucketName", "--jobs", $ParallelJobs.ToString()))
Invoke-ExternalCommand -FilePath "supabase" -ArgumentList $cpArgs

$publicUrl = "https://$ProjectRef.supabase.co/storage/v1/object/public/$BucketName/index.html"

$derivedCacheToken = $CacheBusterToken
if (-not [string]::IsNullOrWhiteSpace($CacheBusterToken)) {
    $derivedCacheToken = $CacheBusterToken.Trim()
}
elseif ($AppendCacheBuster) {
    $derivedCacheToken = (Get-Date -Format 'yyyyMMddHHmmss')
}

$publicUrlWithToken = if ([string]::IsNullOrWhiteSpace($derivedCacheToken)) {
    $publicUrl
}
else {
    "$publicUrl?v=$derivedCacheToken"
}

Write-Step "Готово. Проверьте URL: $publicUrlWithToken"

if ($UpdateAndroidString) {
    Update-AndroidString -StringsPath $AndroidStringsPath -RemoteUrl $publicUrlWithToken
}
else {
    Write-Host "Если требуется, обновите строковый ресурс app/src/main/res/values/strings.xml -> kiosk_url_remote." -ForegroundColor Yellow
}
