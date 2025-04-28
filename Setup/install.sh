#!/bin/bash

# Default branch is main, -branch parameter will pull from a different branch
branch="main"
if [ "$1" == "-branch" ]; then
    branch="$2"
fi

if [ ! -d "/etc/javalanche" ]; then
    sudo mkdir /etc/javalanche
fi

if [ ! -f "/etc/javalanche/jdkinstaller.tar" ]; then
    # install Java development kit
    echo "[*] Downloading Java"
    sudo curl -s -o /etc/javalanche/jdkinstaller.tar.gz https://download.oracle.com/java/24/latest/jdk-24_linux-x64_bin.tar.gz > /dev/null
    echo "[*] Extracting Java"
    sudo gzip -d /etc/javalanche/jdkinstaller.tar.gz
    sudo tar -xf /etc/javalanche/jdkinstaller.tar -C /etc/javalanche
fi

jdkDir=$(ls /etc/javalanche | grep jdk-)
javacDir="/etc/javalanche/$jdkDir/bin/javac"
keytoolDir="/etc/javalanche/$jdkDir/bin/keytool"


# set up directories
if [ ! -d "/etc/javalanche/Servers" ]; then
    sudo mkdir /etc/javalanche/Servers
fi
if [ ! -d "/etc/javalanche/Servers/Beacon" ]; then
    sudo mkdir /etc/javalanche/Servers/Beacon
fi
if [ ! -d "/etc/javalanche/Servers/C2" ]; then
    sudo mkdir /etc/javalanche/Servers/C2
fi
if [ ! -d "/etc/javalanche/Servers/encryption" ]; then
    sudo mkdir /etc/javalanche/Servers/encryption
fi
if [ ! -d "/etc/javalanche/Servers/encryption/aes" ]; then
    sudo mkdir /etc/javalanche/Servers/encryption/aes
fi
if [ ! -d "/etc/javalanche/Servers/encryption/rot13" ]; then
    sudo mkdir /etc/javalanche/Servers/encryption/rot13
fi
if [ ! -d "/etc/javalanche/Servers/encryption/rsa" ]; then
    sudo mkdir /etc/javalanche/Servers/encryption/rsa
fi

echo "[*] Downloading Javalanche files"

# Download all of the java files
sudo curl -s -o /etc/javalanche/Servers/C2/C2Server.java https://gitlab.ritsec.cloud/jms9508/Javalanche/-/raw/$branch/Servers/C2/C2Server.java?ref_type=heads
sudo curl -s -o /etc/javalanche/Servers/C2/C2ServerUserHandler.java https://gitlab.ritsec.cloud/jms9508/Javalanche/-/raw/$branch/Servers/C2/C2ServerUserHandler.java?ref_type=heads
sudo curl -s -o /etc/javalanche/Servers/C2/C2ServerBeaconHandler.java https://gitlab.ritsec.cloud/jms9508/Javalanche/-/raw/$branch/Servers/C2/C2ServerBeaconHandler.java?ref_type=heads
sudo curl -s -o /etc/javalanche/Servers/C2/C2ServerAPI.java https://gitlab.ritsec.cloud/jms9508/Javalanche/-/raw/$branch/Servers/C2/C2ServerAPI.java?ref_type=heads
sudo curl -s -o /etc/javalanche/Servers/Beacon/BeaconServer.java https://gitlab.ritsec.cloud/jms9508/Javalanche/-/raw/$branch/Servers/Beacon/BeaconServer.java?ref_type=heads
sudo curl -s -o /etc/javalanche/Servers/Beacon/BeaconClientHandler.java https://gitlab.ritsec.cloud/jms9508/Javalanche/-/raw/$branch/Servers/Beacon/BeaconClientHandler.java?ref_type=heads
sudo curl -s -o /etc/javalanche/Servers/Beacon/BeaconC2Handler.java https://gitlab.ritsec.cloud/jms9508/Javalanche/-/raw/$branch/Servers/Beacon/BeaconC2Handler.java?ref_type=heads
sudo curl -s -o /etc/javalanche/Servers/Beacon/pwnBoardRequest.java https://gitlab.ritsec.cloud/jms9508/Javalanche/-/raw/$branch/Servers/Beacon/pwnBoardRequest.java?ref_type=heads
sudo curl -s -o /etc/javalanche/Servers/Duplexer.java https://gitlab.ritsec.cloud/jms9508/Javalanche/-/raw/$branch/Servers/Duplexer.java?ref_type=heads
sudo curl -s -o /etc/javalanche/Servers/keepAlive.java https://gitlab.ritsec.cloud/jms9508/Javalanche/-/raw/$branch/Servers/keepAlive.java?ref_type=heads
sudo curl -s -o /etc/javalanche/Servers/notifyLock.java https://gitlab.ritsec.cloud/jms9508/Javalanche/-/raw/$branch/Servers/notifyLock.java?ref_type=heads
sudo curl -s -o /etc/javalanche/Servers/json.jar https://gitlab.ritsec.cloud/jms9508/Javalanche/-/raw/$branch/Servers/json.jar?ref_type=heads
sudo curl -s -o /etc/javalanche/Servers/encryption/aes/aes.java https://gitlab.ritsec.cloud/jms9508/Javalanche/-/raw/$branch/Servers/encryption/aes/aes.java?ref_type=heads
sudo curl -s -o /etc/javalanche/Servers/encryption/aes/encryption.java https://gitlab.ritsec.cloud/jms9508/Javalanche/-/raw/$branch/Servers/encryption/aes/encryption.java?ref_type=heads
sudo curl -s -o /etc/javalanche/Servers/encryption/aes/decryption.java https://gitlab.ritsec.cloud/jms9508/Javalanche/-/raw/$branch/Servers/encryption/aes/decryption.java?ref_type=heads
sudo curl -s -o /etc/javalanche/Servers/encryption/aes/modes.java https://gitlab.ritsec.cloud/jms9508/Javalanche/-/raw/$branch/Servers/encryption/aes/modes.java?ref_type=heads
sudo curl -s -o /etc/javalanche/Servers/encryption/aes/keyExpansion.java https://gitlab.ritsec.cloud/jms9508/Javalanche/-/raw/$branch/Servers/encryption/aes/keyExpansion.java?ref_type=heads
sudo curl -s -o /etc/javalanche/Servers/encryption/rot13/rot13.java https://gitlab.ritsec.cloud/jms9508/Javalanche/-/raw/$branch/Servers/encryption/rot13/rot13.java?ref_type=heads
sudo curl -s -o /etc/javalanche/Servers/encryption/rsa/rsa.java https://gitlab.ritsec.cloud/jms9508/Javalanche/-/raw/$branch/Servers/encryption/rsa/rsa.java?ref_type=heads

# Download file for javac to use
sudo curl -s -o /etc/javalanche/files.txt https://gitlab.ritsec.cloud/jms9508/Javalanche/-/raw/$branch/Setup/linuxFiles.txt?ref_type=heads

# Download file cmd line tool to launch whatever server is desired
sudo curl -s -o /bin/javalanche https://gitlab.ritsec.cloud/jms9508/Javalanche/-/raw/$branch/Setup/javalanche.sh?ref_type=heads
sudo chmod +x /bin/javalanche

echo "[*] Compiling Javalanche files"

# Compile
sudo "$javacDir" -cp /etc/javalanche/Servers/json.jar "@/etc/javalanche/files.txt"

if [ ! -f "/etc/javalanche/testkey.jks" ]; then
    echo "[*] Creating self signed HTTPS Certificate"
    sudo "$keytoolDir" -genkeypair -keyalg RSA -alias selfsigned -keystore /etc/javalanche/testkey.jks -storepass password -validity 360 -keysize 2048 -dname "CN=Javalanche, OU=Javalanche, O=Javalanche, L=Rochester, ST=NY, C=US"
fi

echo ""
echo "Success!"
echo "Use 'javalanche C2' to run the C2 Server"
echo "Use 'javalanche Beacon' to run the Beacon Server"