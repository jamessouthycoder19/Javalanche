#!/bin/bash

# Get command line parameters
server="$1"

jdkDir=$(ls /etc/javalanche | grep jdk-)
javaDir="/etc/javalanche/$jdkDir/bin/java"


# set firewall rules
if [ "$server" == "C2" ]; then
    sudo iptables -A INPUT -p tcp --dport 1234 -j ACCEPT
elif [ "$server" == "Beacon" ]; then
    sudo iptables -A OUTPUT -p tcp --dport 1234 -j ACCEPT
    sudo iptables -A INPUT -p tcp --dport https -j ACCEPT
fi

# Run the desired server
if [ "$server" == "C2" ]; then
    sudo "$javaDir" -cp /etc/javalanche/Servers/json.jar:/etc/javalanche/ Servers.C2.C2Server
elif [ "$server" == "Beacon" ]; then
    sudo "$javaDir" -cp /etc/javalanche/Servers/json.jar:/etc/javalanche/ Servers.Beacon.BeaconServer
fi