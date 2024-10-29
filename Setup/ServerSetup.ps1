param(
    [boolean]$installJDK=$false,
    [string]$server="C2"
)

# installing the JDK takes a REALLY long time doing it via powershell, so optimally just go
# to Oracle's website and download it, then run this script
if($installJDK){
    Invoke-WebRequest -Uri "https://download.oracle.com/java/23/latest/jdk-23_windows-x64_bin.exe" -OutFile "C:\jdkinstaller.exe"
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
if(!(Test-Path "C:\Javalanche")){
    New-Item -ItemType "Directory" -path "C:\Javalanche" | Out-Null
    if(!(Test-Path "C:\Javalanche\Servers")){
        New-Item -ItemType "Directory" -path "C:\Javalanche\Servers" | Out-Null
    }
    if(!(Test-Path "C:\Javalanche\Servers\Beacon")){
        New-Item -ItemType "Directory" -path "C:\Javalanche\Servers\Beacon" | Out-Null
    }
    if(!(Test-Path "C:\Javalanche\Servers\C2")){
        New-Item -ItemType "Directory" -path "C:\Javalanche\Servers\C2" | Out-Null
    }
}

$testOne = Test-Path "C:\Javalanche"
$testTwo = Test-Path "C:\Javalanche\Servers"
$testThree = Test-Path "C:\Javalanche\Servers\Beacon"
$testFour = Test-Path "C:\Javalanche\Servers\C2"

if($testOne -eq "True" -and $testTwo -eq "True" -and $testThree -eq "True" -and $testFour -eq "True"){
    Write-Host "[" -NoNewline; Write-Host "SUCESS" -ForegroundColor Green -NoNewline; Write-Host "] Directories Created" -ForegroundColor White
} else {
    Write-Host "[" -NoNewline; Write-Host "ERROR" -ForegroundColor Red -NoNewline; Write-Host "] Directories could not be created" -ForegroundColor White
}


# Download all of the files for the Servers
Invoke-WebRequest -Uri "https://gitlab.ritsec.cloud/jms9508/Javalanche/-/raw/main/Servers/C2/C2Server.java?ref_type=heads" -OutFile "C:\Javalanche\Servers\C2\C2Server.java"
Invoke-WebRequest -Uri "https://gitlab.ritsec.cloud/jms9508/Javalanche/-/raw/main/Servers/C2/C2ServerBeaconHandler.java?ref_type=heads" -OutFile "C:\Javalanche\Servers\C2\C2ServerBeaconHandler.java"
Invoke-WebRequest -Uri "https://gitlab.ritsec.cloud/jms9508/Javalanche/-/raw/main/Servers/C2/C2ServerUserHandler.java?ref_type=heads" -OutFile "C:\Javalanche\Servers\C2\C2ServerUserHandler.java"
Invoke-WebRequest -Uri "https://gitlab.ritsec.cloud/jms9508/Javalanche/-/raw/main/Servers/Beacon/BeaconC2Handler.java?ref_type=heads" -OutFile "C:\Javalanche\Servers\Beacon\BeaconC2Handler.java"
Invoke-WebRequest -Uri "https://gitlab.ritsec.cloud/jms9508/Javalanche/-/raw/main/Servers/Beacon/BeaconClientHandler.java?ref_type=heads" -OutFile "C:\Javalanche\Servers\Beacon\BeaconClientHandler.java"
Invoke-WebRequest -Uri "https://gitlab.ritsec.cloud/jms9508/Javalanche/-/raw/main/Servers/Beacon/BeaconServer.java?ref_type=heads" -OutFile "C:\Javalanche\Servers\Beacon\BeaconServer.java"
Invoke-WebRequest -Uri "https://gitlab.ritsec.cloud/jms9508/Javalanche/-/raw/main/Servers/Duplexer.java?ref_type=heads" -OutFile "C:\Javalanche\Servers\Duplexer.java"
Invoke-WebRequest -Uri "https://gitlab.ritsec.cloud/jms9508/Javalanche/-/raw/main/Servers/keepAlive.java?ref_type=heads" -OutFile "C:\Javalanche\Servers\keepAlive.java"

$testOne = Test-Path "C:\Javalanche\Servers\C2\C2Server.java"
$testTwo = Test-Path "C:\Javalanche\Servers\C2\C2ServerBeaconHandler.java"
$testThree = Test-Path "C:\Javalanche\Servers\C2\C2ServerUserHandler.java"
$testFour = Test-Path "C:\Javalanche\Servers\Beacon\BeaconC2Handler.java"
$testFive = Test-Path "C:\Javalanche\Servers\Beacon\BeaconClientHandler.java"
$testSix = Test-Path "C:\Javalanche\Servers\Beacon\BeaconServer.java"
$testSeven = Test-Path "C:\Javalanche\Servers\Duplexer.java"
$testEight = Test-Path "C:\Javalanche\Servers\keepAlive.java"

if($testOne -eq "True" -and $testTwo -eq "True" -and $testThree -eq "True" -and $testFour -eq "True" -and $testFive -eq "True" -and $testSix -eq "True" -and $testSeven -eq "True" -and $testEight -eq "True"){
    Write-Host "[" -NoNewline; Write-Host "SUCESS" -ForegroundColor Green -NoNewline; Write-Host "] Java Files downloaded" -ForegroundColor White
} else {
    Write-Host "[" -NoNewline; Write-Host "ERROR" -ForegroundColor Red -NoNewline; Write-Host "] Java Files could not be downloaded" -ForegroundColor White
}


# Set up file for javac
Invoke-WebRequest -Uri "https://gitlab.ritsec.cloud/jms9508/Javalanche/-/raw/main/Setup/windowsFiles.txt?ref_type=heads" -OutFile "C:\Javalanche\files.txt"


# Change Location so that compilation and Running the server work correctly
Set-Location "C:\Javalanche"


# Compile
& "C:\Program Files\Java\jdk-23\bin\javac.exe" "@C:\Javalanche\files.txt"

$testOne = Test-Path "C:\Javalanche\Servers\C2\C2Server.class"
$testTwo = Test-Path "C:\Javalanche\Servers\C2\C2ServerBeaconHandler.class"
$testThree = Test-Path "C:\Javalanche\Servers\C2\C2ServerUserHandler.class"
$testFour = Test-Path "C:\Javalanche\Servers\Beacon\BeaconC2Handler.class"
$testFive = Test-Path "C:\Javalanche\Servers\Beacon\BeaconClientHandler.class"
$testSix = Test-Path "C:\Javalanche\Servers\Beacon\BeaconServer.class"
$testSeven = Test-Path "C:\Javalanche\Servers\Duplexer.class"
$testEight = Test-Path "C:\Javalanche\Servers\keepAlive.class"

if($testOne -eq "True" -and $testTwo -eq "True" -and $testThree -eq "True" -and $testFour -eq "True" -and $testFive -eq "True" -and $testSix -eq "True" -and $testSeven -eq "True" -and $testEight -eq "True"){
    Write-Host "[" -NoNewline; Write-Host "SUCESS" -ForegroundColor Green -NoNewline; Write-Host "] Java Files Compiled" -ForegroundColor White
} else {
    Write-Host "[" -NoNewline; Write-Host "ERROR" -ForegroundColor Red -NoNewline; Write-Host "] Java Files could not be compiled" -ForegroundColor White
}


# Run the Server
if($server -eq "C2"){
    & "C:\Program Files\Java\jdk-23\bin\java.exe" "Servers/C2/C2Server"
} else {
    & "C:\Program Files\Java\jdk-23\bin\java.exe" "Servers/Beacon/BeaconServer"
}