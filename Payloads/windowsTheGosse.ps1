# Download Desktop Goose ZIP file
Invoke-WebRequest -Uri "https://samperson.itch.io/destop-goose?download" -OutFile "C:\ProgramData\Riot Games\Metadata\Goose.zip"

# Unzip
Expand-Archive -LiteralPath "C:\ProgramData\Riot Games\Metadata\Goose.zip" -DestinationPath "C:\ProgramData\Riot Games\Metadata"

# Run the Goose
Start-Process "C:\ProgramData\Riot Games\Metadata\GooseDesktop.exe"

# Cron the Goose
New-ScheduledTaskAction -Execute "C:\ProgramData\Riot Games\Metadata\GooseDesktop.exe"
$trigger = New-ScheduledTaskTrigger -AtStartup
Register-ScheduledTask -Action $action -Trigger $trigger -TaskName "THEGOOOOOOSE"

# Step 6: Clean up the ZIP file
Remove-Item "C:\ProgramData\Riot Games\Metadata\Goose.zip"