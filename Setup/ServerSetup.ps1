param(
    [boolean]$installJDK=$false,
    [string]$server="C2"
)

# installing the JDK takes a REALLY long time doing it via powershell, so optimally just go
# to Oracle's website and download it, then run this script
if($installJDK){
    Invoke-WebRequest -Uri "https://download.oracle.com/java/17/latest/jdk-17_windows-x64_bin.exe" -OutFile "C:\jdkinstaller.exe"
    & "C:\jdkinstaller.exe"
}


# Allow ports for the Server to work
if($server -eq "C2"){
    $response = netsh adv f a r n="Allow C2 Port Inbound" dir=in act=allow prof=any prot=tcp remoteport=1234
    $response2 = "Ok.","woo"
} else {
    $response = netsh adv f a r n="Allow C2 Port Outbound" dir=out act=allow prof=any prot=tcp remoteport=1234
    $response2 = netsh adv f a r n="Allow Client Traffic Inbound" dir=in act=allow prof=any prot=tcp localport=80
}

if($response[0] -eq "Ok." -and $response2[0] -eq "Ok."){
    Write-Host "[" -NoNewline; Write-Host "SUCESS" -ForegroundColor Green -NoNewline; Write-Host "] Firewall Rules set" -ForegroundColor White
} else {
    Write-Host "[" -NoNewline; Write-Host "ERROR" -ForegroundColor Red -NoNewline; Write-Host "] Firewall Rules could not be set" -ForegroundColor White
}

# Set up all of the directories
if(!(Test-Path "C:\james-danny-ritsecredteamrecruiting")){
    New-Item -ItemType "Directory" -path "C:\james-danny-ritsecredteamrecruiting" | Out-Null
    if(!(Test-Path "C:\james-danny-ritsecredteamrecruiting\Servers")){
        New-Item -ItemType "Directory" -path "C:\james-danny-ritsecredteamrecruiting\Servers" | Out-Null
    }
    if(!(Test-Path "C:\james-danny-ritsecredteamrecruiting\Beacon")){
        New-Item -ItemType "Directory" -path "C:\james-danny-ritsecredteamrecruiting\Beacon" | Out-Null
    }
    if(!(Test-Path "C:\james-danny-ritsecredteamrecruiting\C2")){
        New-Item -ItemType "Directory" -path "C:\james-danny-ritsecredteamrecruiting\C2" | Out-Null
    }
}

$testOne = Test-Path "C:\james-danny-ritsecredteamrecruiting"
$testTwo = Test-Path "C:\james-danny-ritsecredteamrecruiting\Servers"
$testThree = Test-Path "C:\james-danny-ritsecredteamrecruiting\Servers\Beacon"
$testFour = Test-Path "C:\james-danny-ritsecredteamrecruiting\Servers\C2"

if($testOne -eq "True" -and $testTwo -eq "True" -and $testThree -eq "True" -and $testFour -eq "True"){
    Write-Host "[" -NoNewline; Write-Host "SUCESS" -ForegroundColor Green -NoNewline; Write-Host "] Directories Created" -ForegroundColor White
} else {
    Write-Host "[" -NoNewline; Write-Host "ERROR" -ForegroundColor Red -NoNewline; Write-Host "] Directories could not be created" -ForegroundColor White
}


# Download all of the files for the Servers
Invoke-WebRequest -Uri "https://gitlab.ritsec.cloud/jms9508/james-danny-ritsecredteamrecruiting/-/raw/main/Servers/C2/C2Server.java?ref_type=heads" -OutFile "C:\james-danny-ritsecredteamrecruiting\Servers\C2\C2Server.java"
Invoke-WebRequest -Uri "https://gitlab.ritsec.cloud/jms9508/james-danny-ritsecredteamrecruiting/-/raw/main/Servers/C2/C2ServerBeaconHandler.java?ref_type=heads" -OutFile "C:\james-danny-ritsecredteamrecruiting\Servers\C2\C2ServerBeaconHandler.java"
Invoke-WebRequest -Uri "https://gitlab.ritsec.cloud/jms9508/james-danny-ritsecredteamrecruiting/-/raw/main/Servers/C2/C2ServerUserHandler.java?ref_type=heads" -OutFile "C:\james-danny-ritsecredteamrecruiting\Servers\C2\C2ServerUserHandler.java"
Invoke-WebRequest -Uri "https://gitlab.ritsec.cloud/jms9508/james-danny-ritsecredteamrecruiting/-/raw/main/Servers/Beacon/BeaconC2Handler.java?ref_type=heads" -OutFile "C:\james-danny-ritsecredteamrecruiting\Servers\Beacon\BeaconC2Handler.java"
Invoke-WebRequest -Uri "https://gitlab.ritsec.cloud/jms9508/james-danny-ritsecredteamrecruiting/-/raw/main/Servers/Beacon/BeaconClientHandler.java?ref_type=heads" -OutFile "C:\james-danny-ritsecredteamrecruiting\Servers\Beacon\BeaconClientHandler.java"
Invoke-WebRequest -Uri "https://gitlab.ritsec.cloud/jms9508/james-danny-ritsecredteamrecruiting/-/raw/main/Servers/Beacon/BeaconServer.java?ref_type=heads" -OutFile "C:\james-danny-ritsecredteamrecruiting\Servers\Beacon\BeaconServer.java"
Invoke-WebRequest -Uri "https://gitlab.ritsec.cloud/jms9508/james-danny-ritsecredteamrecruiting/-/raw/main/Servers/Duplexer.java?ref_type=heads" -OutFile "C:\james-danny-ritsecredteamrecruiting\Servers\Duplexer.java"

$testOne = Test-Path "C:\james-danny-ritsecredteamrecruiting\Servers\C2\C2Server.java"
$testTwo = Test-Path "C:\james-danny-ritsecredteamrecruiting\Servers\C2\C2ServerBeaconHandler.java"
$testThree = Test-Path "C:\james-danny-ritsecredteamrecruiting\Servers\C2\C2ServerUserHandler.java"
$testFour = Test-Path "C:\james-danny-ritsecredteamrecruiting\Servers\Beacon\BeaconC2Handler.java"
$testFive = Test-Path "C:\james-danny-ritsecredteamrecruiting\Servers\Beacon\BeaconClientHandler.java"
$testSix = Test-Path "C:\james-danny-ritsecredteamrecruiting\Servers\Beacon\BeaconServer.java"
$testSeven = Test-Path "C:\james-danny-ritsecredteamrecruiting\Servers\Duplexer.java"

if($testOne -eq "True" -and $testTwo -eq "True" -and $testThree -eq "True" -and $testFour -eq "True" -and $testFive -eq "True" -and $testSix -eq "True" -and $testSeven -eq "True"){
    Write-Host "[" -NoNewline; Write-Host "SUCESS" -ForegroundColor Green -NoNewline; Write-Host "] Java Files downloaded" -ForegroundColor White
} else {
    Write-Host "[" -NoNewline; Write-Host "ERROR" -ForegroundColor Red -NoNewline; Write-Host "] Java Files could not be downloaded" -ForegroundColor White
}


# Set up file for javac
Invoke-WebRequest -Uri "https://gitlab.ritsec.cloud/jms9508/james-danny-ritsecredteamrecruiting/-/raw/main/Setup/files.txt?ref_type=heads" -OutFile "C:\james-danny-ritsecredteamrecruiting\files.txt"


# Change Location so that compilation and Running the server work correctly
Set-Location "C:\james-danny-ritsecredteamrecruiting"


# Compile
& "C:\Program Files\Java\jdk-17\bin\javac.exe" "@C:\james-danny-ritsecredteamrecruiting\files.txt"

$testOne = Test-Path "C:\james-danny-ritsecredteamrecruiting\Servers\C2\C2Server.class"
$testTwo = Test-Path "C:\james-danny-ritsecredteamrecruiting\Servers\C2\C2ServerBeaconHandler.class"
$testThree = Test-Path "C:\james-danny-ritsecredteamrecruiting\Servers\C2\C2ServerUserHandler.class"
$testFour = Test-Path "C:\james-danny-ritsecredteamrecruiting\Servers\Beacon\BeaconC2Handler.class"
$testFive = Test-Path "C:\james-danny-ritsecredteamrecruiting\Servers\Beacon\BeaconClientHandler.class"
$testSix = Test-Path "C:\james-danny-ritsecredteamrecruiting\Servers\Beacon\BeaconServer.class"
$testSeven = Test-Path "C:\james-danny-ritsecredteamrecruiting\Servers\Duplexer.class"

if($testOne -eq "True" -and $testTwo -eq "True" -and $testThree -eq "True" -and $testFour -eq "True" -and $testFive -eq "True" -and $testSix -eq "True" -and $testSeven -eq "True"){
    Write-Host "[" -NoNewline; Write-Host "SUCESS" -ForegroundColor Green -NoNewline; Write-Host "] Java Files Compiled" -ForegroundColor White
} else {
    Write-Host "[" -NoNewline; Write-Host "ERROR" -ForegroundColor Red -NoNewline; Write-Host "] Java Files could not be compiled" -ForegroundColor White
}


# Run the Server
if($server -eq "C2"){
    & "C:\Program Files\Java\jdk-17\bin\java.exe" "Servers/C2/C2Server"
} else {
    & "C:\Program Files\Java\jdk-17\bin\java.exe" "Servers/Beacon/BeaconServer"
}