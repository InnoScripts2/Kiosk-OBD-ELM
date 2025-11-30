param(
    [string]$Variant = "debug",
    [string]$LibraryName = "pass_thru_jni",
    [string]$Payload = "02 3E 00",
    [int]$TimeoutMs = 30000,
    [int]$ProtocolId = 1,
    [int]$BaudRate = 500000,
    [int]$ChannelFlags = 0,
    [string]$DeviceId = "",
    [string]$Runner = "androidx.test.runner.AndroidJUnitRunner",
    [string]$InstrumentationPackage = "",
    [switch]$SkipInstall,
    [switch]$DryRun
)

$scriptRoot = Split-Path -Parent $PSCommandPath
$androidRoot = Split-Path -Parent $scriptRoot
$gradleScript = if ($IsWindows) { Join-Path $androidRoot "gradlew.bat" } else { Join-Path $androidRoot "gradlew" }

function ConvertToVariantName([string]$name) {
    $parts = $name -split '[-_]'
    $converted = $parts | ForEach-Object {
        if ([string]::IsNullOrEmpty($_)) { return "" }
        return $_.Substring(0, 1).ToUpper() + $_.Substring(1)
    }
    return ($converted -join "")
}

function Invoke-Gradle([string[]]$tasks) {
    if ($DryRun) {
        Write-Host "DRY-RUN>" $gradleScript ($tasks -join ' ')
        return
    }
    Push-Location $androidRoot
    try {
        & $gradleScript @tasks
        if ($LASTEXITCODE -ne 0) {
            throw "Gradle command failed with exit code $LASTEXITCODE"
        }
    }
    finally {
        Pop-Location
    }
}

$variantName = ConvertToVariantName $Variant
if (-not $variantName) {
    throw "Variant name cannot be empty"
}

if (-not $SkipInstall) {
    Invoke-Gradle @(":app:install$variantName", ":app:install${variantName}AndroidTest")
}

$packageSuffix = ""
if ([string]::IsNullOrEmpty($InstrumentationPackage)) {
    if ($Variant.ToLower().EndsWith("debug")) {
        $packageSuffix = ".debug"
    }
    $instrumentationComponent = "com.selfservice.kiosk$packageSuffix.test/$Runner"
}
else {
    $instrumentationComponent = "$InstrumentationPackage/$Runner"
}

$adbArgs = @()
if ($DeviceId) {
    $adbArgs += "-s"
    $adbArgs += $DeviceId
}
$adbArgs += "shell"
$adbArgs += "am"
$adbArgs += "instrument"
$adbArgs += "-w"
$adbArgs += "-r"
$adbArgs += "-e"
$adbArgs += "passthruSmoke"
$adbArgs += "true"
$adbArgs += "-e"
$adbArgs += "passthruLibrary"
$adbArgs += $LibraryName
$adbArgs += "-e"
$adbArgs += "passthruPayload"
$adbArgs += $Payload
$adbArgs += "-e"
$adbArgs += "passthruTimeoutMs"
$adbArgs += $TimeoutMs.ToString()
$adbArgs += "-e"
$adbArgs += "passthruProtocolId"
$adbArgs += $ProtocolId.ToString()
$adbArgs += "-e"
$adbArgs += "passthruBaudRate"
$adbArgs += $BaudRate.ToString()
$adbArgs += "-e"
$adbArgs += "passthruFlags"
$adbArgs += $ChannelFlags.ToString()
$adbArgs += $instrumentationComponent

if ($DryRun) {
    Write-Host "DRY-RUN> adb" ($adbArgs -join ' ')
    exit 0
}

& adb @adbArgs
if ($LASTEXITCODE -ne 0) {
    throw "PassThru smoke instrumentation failed with exit code $LASTEXITCODE"
}
