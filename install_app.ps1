# Install Deepslate Launcher as a permanent Start Menu / Desktop app.
# Shortcuts point at this project, so source edits apply on next launch.

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $MyInvocation.MyCommand.Path
$VenvPython = Join-Path $Root ".venv\Scripts\python.exe"
$PipPython = Join-Path $Root ".venv\Scripts\python.exe"
$Vbs = Join-Path $Root "DeepslateLauncher.vbs"
$IconPath = Join-Path $Root "assets\icon.ico"

if (-not (Test-Path $VenvPython)) {
    Write-Host "Creating virtual environment..."
    $pyCmd = Get-Command python -ErrorAction SilentlyContinue
    if ($pyCmd) {
        $pyPath = $pyCmd.Source
    } else {
        $pyPath = "C:\Users\Dragos\AppData\Local\Programs\Python\Python314\python.exe"
    }
    if (-not (Test-Path $pyPath)) { throw "Python not found. Install Python 3.10+ first." }
    & $pyPath -m venv (Join-Path $Root ".venv")
}

Write-Host "Ensuring dependencies..."
& $PipPython -m pip install -q -r (Join-Path $Root "requirements.txt")

if (-not (Test-Path $IconPath)) {
    Write-Host "Generating app icon..."
    & $PipPython (Join-Path $Root "make_icon.py")
} else {
    # Refresh icon from deepslate source when present
    $Deepslate = Join-Path $Root "assets\deepslate_block.png"
    if (Test-Path $Deepslate) {
        Write-Host "Updating deepslate app icon..."
        & $PipPython (Join-Path $Root "make_icon.py")
    }
}

$Wsh = New-Object -ComObject WScript.Shell
$Desktop = [Environment]::GetFolderPath("Desktop")
$StartMenu = Join-Path ([Environment]::GetFolderPath("StartMenu")) "Programs\Deepslate Launcher"
New-Item -ItemType Directory -Force -Path $StartMenu | Out-Null

function New-LauncherShortcut([string]$Path) {
    $sc = $Wsh.CreateShortcut($Path)
    $sc.TargetPath = "wscript.exe"
    $sc.Arguments = "`"$Vbs`""
    $sc.WorkingDirectory = $Root
    $sc.WindowStyle = 1
    $sc.Description = "Deepslate Minecraft Launcher (editable install)"
    if (Test-Path $IconPath) { $sc.IconLocation = "$IconPath,0" }
    $sc.Save()
}

New-LauncherShortcut (Join-Path $Desktop "Deepslate Launcher.lnk")
New-LauncherShortcut (Join-Path $StartMenu "Deepslate Launcher.lnk")

$InstallInfo = Join-Path $env:LOCALAPPDATA "DeepslateLauncher\install.json"
New-Item -ItemType Directory -Force -Path (Split-Path $InstallInfo) | Out-Null
@{
    root = $Root
    desktop = (Join-Path $Desktop "Deepslate Launcher.lnk")
    startMenu = $StartMenu
    installedAt = (Get-Date).ToString("o")
    mode = "editable-source"
} | ConvertTo-Json | Set-Content -Path $InstallInfo -Encoding UTF8

Write-Host ""
Write-Host "Installed Deepslate Launcher."
Write-Host "  Desktop shortcut: Desktop\Deepslate Launcher"
Write-Host "  Start Menu:       Deepslate Launcher"
Write-Host "  Source folder:    $Root"
Write-Host ""
Write-Host "Edit app.py / core.py anytime, then relaunch to see changes."
Write-Host "Optional portable build: .\build_exe.bat"
