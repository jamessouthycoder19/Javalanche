#!/bin/bash

# Get command line parameters
server="$1"

jdkDir=$(ls /etc/javalanche | grep jdk-)
javaDir="/etc/javalanche/$jdkDir/bin/java"

if [ "$server" == "compile" ]; then
    javacDir="/etc/javalanche/$jdkDir/bin/javac"
    sudo "$javacDir" -cp /etc/javalanche/Servers/json.jar "@/etc/javalanche/files.txt"
fi


if [ "$server" == "C2" ]; then
    if [ ! -f "/etc/letsencrypt/live/api.javalanche.net/fullchain.pem" ] || [ ! -f "/etc/letsencrypt/live/api.javalanche.net/privkey.pem" ]; then
        sudo apt update
        sudo apt-get install certbot -y
        sudo certbot certonly -d "api.javalanche.net" --agree-tos --email jms9508@rit.edu --manual --preferred-challenges dns
    fi

    if [ ! -f "/etc/letsencrypt/live/www.javalanche.net/fullchain.pem" ] || [ ! -f "/etc/letsencrypt/live/www.javalanche.net/privkey.pem" ]; then
        sudo apt update
        sudo apt-get install certbot -y
        sudo certbot certonly -d "www.javalanche.net" --agree-tos --email jms9508@rit.edu --manual --preferred-challenges dns
    fi
    
fi
# set firewall rules
if [ "$server" == "C2" ]; then
    sudo iptables -A INPUT -p tcp --dport 1234 -j ACCEPT
elif [ "$server" == "Beacon" ]; then
    sudo iptables -A OUTPUT -p tcp --dport 1234 -j ACCEPT
    sudo iptables -A INPUT -p tcp --dport https -j ACCEPT
elif [ "$server" == "DnsBeacon" ]; then
    sudo iptables -A OUTPUT -p tcp --dport 1234 -j ACCEPT
    sudo iptables -A INPUT -p udp --dport 53 -j ACCEPT
fi

# Run the desired server
if [ "$server" == "C2" ] || [ "$server" == "c2" ]; then
    sudo systemctl start apache2
    sudo "$javaDir" -cp /etc/javalanche/Servers/json.jar:/etc/javalanche/ Servers.C2.C2Server
elif [ "$server" == "Beacon" ] || [ "$server" == "beacon" ]; then
    sudo "$javaDir" -cp /etc/javalanche/Servers/json.jar:/etc/javalanche/ Servers.Beacon.BeaconServer
elif [ "$server" == "CLI" ] || [ "$server" == "cli" ]; then
    sudo "$javaDir" -cp /etc/javalanche/Servers/json.jar:/etc/javalanche/ Servers.CLI.C2ServerCLI
elif [ "$server" == "DnsBeacon" ] || [ "$server" == "dnsbeacon" ] || [ "$server" == "dnsBeacon" ] || [ "$server" == "Dnsbeacon" ]; then
    sudo systemctl stop systemd-resolved
    sudo "$javaDir" -cp /etc/javalanche/Servers/json.jar:/etc/javalanche/ Servers.DnsBeacon.DnsBeaconServer
else
    echo "Usage: javalanche.sh [C2|Beacon|DnsBeacon|CLI|]"
    exit 1
fi