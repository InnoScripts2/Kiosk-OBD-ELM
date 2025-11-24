#[CmdletBinding()]
param(
    [switch]$Execute
)

$ErrorActionPreference = 'Stop'

# Session 1G Update (23.11.2025):
# Этот скрипт форвардит на android/tools/session-05-archive-plan.ps1
# Категории сессий: B (BLE/OBD), C (Reports/Payments), G (Documentation/Infrastructure)
# Статусы доноров отслеживаются в plan-80-session-roadmap.md
# Для B/C категорий: код переносится в android/feature-*
# Для G категорий: обновляются docs/, logs/, scripts/ без касания feature-модулей

Write-Warning 'Скрипт перенесён в android/tools/session-05-archive-plan.ps1'

$forwardScript = Join-Path $PSScriptRoot '..\tools\session-05-archive-plan.ps1'

if (-not (Test-Path -LiteralPath $forwardScript)) {
    throw "Не найден целевой скрипт: $forwardScript"
}

& $forwardScript @PSBoundParameters
