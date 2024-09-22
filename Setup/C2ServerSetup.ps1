param(
    [boolean]$installJDK=$false
)

# installing the JDK takes a REALLY long time doing it via powershell, so optimally just go
# to Oracle's website and download it, then run this script
if($installJDK){
    Invoke-WebRequest -Uri "https://download.oracle.com/java/17/latest/jdk-17_windows-x64_bin.exe" -OutFile "C:\jdkinstaller.exe"
    & "C:\jdkinstaller.exe"
}

# Set up all of the directories
New-Item -ItemType "Directory" -path "C:\james-danny-ritsecredteamrecruiting"
New-Item -ItemType "Directory" -path "C:\james-danny-ritsecredteamrecruiting\Servers"
New-Item -ItemType "Directory" -path "C:\james-danny-ritsecredteamrecruiting\Servers\Beacon"
New-Item -ItemType "Directory" -path "C:\james-danny-ritsecredteamrecruiting\Servers\C2"

# Download all of the files for the Servers
Invoke-WebRequest -Uri "https://gitlab.ritsec.cloud/jms9508/james-danny-ritsecredteamrecruiting/-/raw/main/Servers/C2/C2Server.java?ref_type=heads" -OutFile "C:\james-danny-ritsecredteamrecruiting\Servers\C2\C2Server.java"
Invoke-WebRequest -Uri "https://gitlab.ritsec.cloud/jms9508/james-danny-ritsecredteamrecruiting/-/raw/main/Servers/C2/C2ServerBeaconHandler.java?ref_type=heads" -OutFile "C:\james-danny-ritsecredteamrecruiting\Servers\C2\C2ServerBeaconHandler.java"
Invoke-WebRequest -Uri "https://gitlab.ritsec.cloud/jms9508/james-danny-ritsecredteamrecruiting/-/raw/main/Servers/C2/C2ServerUserHandler.java?ref_type=heads" -OutFile "C:\james-danny-ritsecredteamrecruiting\Servers\C2\C2ServerUserHandler.java"
Invoke-WebRequest -Uri "https://gitlab.ritsec.cloud/jms9508/james-danny-ritsecredteamrecruiting/-/raw/main/Servers/Beacon/BeaconC2Handler.java?ref_type=heads" -OutFile "C:\james-danny-ritsecredteamrecruiting\Servers\Beacon\BeaconC2Handler.java"
Invoke-WebRequest -Uri "https://gitlab.ritsec.cloud/jms9508/james-danny-ritsecredteamrecruiting/-/raw/main/Servers/Beacon/BeaconClientHandler.java?ref_type=heads" -OutFile "C:\james-danny-ritsecredteamrecruiting\Servers\Beacon\BeaconClientHandler.java"
Invoke-WebRequest -Uri "https://gitlab.ritsec.cloud/jms9508/james-danny-ritsecredteamrecruiting/-/raw/main/Servers/Beacon/BeaconServer.java?ref_type=heads" -OutFile "C:\james-danny-ritsecredteamrecruiting\Servers\Beacon\BeaconServer.java"
Invoke-WebRequest -Uri "https://gitlab.ritsec.cloud/jms9508/james-danny-ritsecredteamrecruiting/-/raw/main/Servers/Duplexer.java?ref_type=heads" -OutFile "C:\james-danny-ritsecredteamrecruiting\Servers\Duplexer.java"

# Set up file for javac
Invoke-WebRequest -Uri "https://gitlab.ritsec.cloud/jms9508/james-danny-ritsecredteamrecruiting/-/raw/main/Setup/files.txt?ref_type=heads" -OutFile "C:\james-danny-ritsecredteamrecruiting\files.txt"

# Compile
& "C:\Program Files\Java\jdk-17\bin\javac.exe" "@C:\james-danny-ritsecredteamrecruiting\files.txt"

# Run the Server
& "C:\Program Files\Java\jdk-17\bin\java.exe" "Servers\C2\C2Server.java"