# Remove Desktop / Start Menu shortcuts created by install_app.ps1

$ErrorActionPreference = "Stop"
$InstallInfo = Join-Path $env:LOCALAPPDATA "DeepslateLauncher\install.json"

$Desktop = [Environment]::GetFolderPath("Desktop")
$DesktopLnk = Join-Path $Desktop "Deepslate Launcher.lnk"
$StartMenu = Join-Path ([Environment]::GetFolderPath("StartMenu")) "Programs\Deepslate Launcher"

if (Test-Path $InstallInfo) {
    $info = Get-Content $InstallInfo -Raw | ConvertFrom-Json
    if ($info.desktop) { $DesktopLnk = $info.desktop }
    if ($info.startMenu) { $StartMenu = $info.startMenu }
}

if (Test-Path $DesktopLnk) {
    Remove-Item $DesktopLnk -Force
    Write-Host "Removed desktop shortcut."
}
if (Test-Path $StartMenu) {
    Remove-Item $StartMenu -Recurse -Force
    Write-Host "Removed Start Menu folder."
}
if (Test-Path $InstallInfo) {
    Remove-Item $InstallInfo -Force
    $dir = Split-Path $InstallInfo
    if ((Get-ChildItem $dir -Force -ErrorAction SilentlyContinue | Measure-Object).Count -eq 0) {
        Remove-Item $dir -Force -ErrorAction SilentlyContinue
    }
}

Write-Host "Uninstall complete. Project files were left untouched."
