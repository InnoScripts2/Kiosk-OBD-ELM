<#!
.SYNOPSIS
Скачивает Gradle cache из GitHub Actions и импортирует его в локальный Gradle home.

.DESCRIPTION
Скрипт автоматически находит последний неистёкший артефакт с указанным именем
(по умолчанию `gradle-caches-restored`) в репозитории GitHub, скачивает его и
передаёт во вспомогательный скрипт `import-gradle-cache.ps1`. Это устраняет
необходимость вручную открывать вкладку Actions и загружать архивы.

.Требования
- PowerShell 7+
- Переменная окружения `GITHUB_TOKEN` (PAT с правами `actions:read`)

.PARAMETER Repo
Репозиторий в формате owner/name. По умолчанию `InnoScripts2/Kiosk-OBD-ELM`.

.PARAMETER ArtifactName
Имя артефакта, который создаёт workflow. По умолчанию `gradle-caches-restored`.

.PARAMETER GradleHome
Необязательный путь к Gradle home. Если не указан, определяется как в
`import-gradle-cache.ps1`.

.PARAMETER KeepArchive
Не удалять временный zip после импорта (удобно для отладки).

.EXAMPLE
PS> pwsh ./android/scripts/powershell/bootstrap-gradle-cache.ps1

.EXAMPLE
PS> pwsh ./android/scripts/powershell/bootstrap-gradle-cache.ps1 -Repo Contoso/App -ArtifactName gradle-cache -GradleHome D:\gradle
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $false)]
    [string]$Repo = "InnoScripts2/Kiosk-OBD-ELM",

    [Parameter(Mandatory = $false)]
    [string]$ArtifactName = "gradle-caches-restored",

    [Parameter(Mandatory = $false)]
    [string]$GradleHome,

    [Parameter(Mandatory = $false)]
    [switch]$KeepArchive
)

function Write-Step {
    param([string]$Message)
    Write-Host "[gradle-cache-bootstrap] $Message"
}

$token = $env:GITHUB_TOKEN
if (-not $token) {
    throw "GITHUB_TOKEN не установлен. Укажите PAT с правами actions:read."
}

$apiBase = "https://api.github.com/repos/$Repo"
$headers = @{
    Authorization = "Bearer $token"
    Accept        = "application/vnd.github+json"
    "User-Agent"  = "gradle-cache-bootstrap"
}

Write-Step "Получение списка артефактов из $Repo"
try {
    $artifactsResponse = Invoke-RestMethod -Method Get -Uri "$apiBase/actions/artifacts?per_page=100" -Headers $headers
}
catch {
    throw "Не удалось получить список артефактов: $($_.Exception.Message)"
}

$artifact = $artifactsResponse.artifacts |
Where-Object { $_.name -eq $ArtifactName -and -not $_.expired } |
Sort-Object -Property created_at -Descending |
Select-Object -First 1

if (-not $artifact) {
    throw "Артефакт '$ArtifactName' не найден или истёк. Убедитесь, что workflow уже запускался."
}

$downloadUrl = $artifact.archive_download_url
$tempZip = Join-Path ([System.IO.Path]::GetTempPath()) ("$($ArtifactName)-$($artifact.id).zip")
Write-Step "Скачивание артефакта id=$($artifact.id) в $tempZip"
try {
    Invoke-WebRequest -Uri $downloadUrl -Headers $headers -OutFile $tempZip
}
catch {
    throw "Не удалось скачать артефакт: $($_.Exception.Message)"
}

$importScript = Join-Path $PSScriptRoot "import-gradle-cache.ps1"
if (-not (Test-Path -LiteralPath $importScript)) {
    throw "Не найден import-gradle-cache.ps1 по пути $importScript"
}

Write-Step "Импорт кэша через import-gradle-cache.ps1"
$importParams = @{ ArchivePath = $tempZip }
if ($GradleHome) {
    $importParams.GradleHome = $GradleHome
}
& $importScript @importParams

if (-not $KeepArchive) {
    Remove-Item -LiteralPath $tempZip -Force -ErrorAction SilentlyContinue
}

Write-Step "Готово. Gradle cache импортирован."
