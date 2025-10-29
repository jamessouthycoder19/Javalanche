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
    sudo curl -s -o /etc/javalanche/jdkinstaller.tar.gz https://download.oracle.com/java/25/latest/jdk-25_linux-x64_bin.tar.gz > /dev/null
    echo "[*] Extracting Java"
    sudo gzip -d /etc/javalanche/jdkinstaller.tar.gz
    sudo tar -xf /etc/javalanche/jdkinstaller.tar -C /etc/javalanche
fi

jdkDir=$(ls /etc/javalanche | grep jdk-)
javacDir="/etc/javalanche/$jdkDir/bin/javac"

# set up directories
if [ ! -d "/etc/javalanche/Web" ]; then
    sudo mkdir /etc/javalanche/Web
fi
if [ ! -d "/etc/javalanche/Servers" ]; then
    sudo mkdir /etc/javalanche/Servers
fi
if [ ! -d "/etc/javalanche/Servers/Beacon" ]; then
    sudo mkdir /etc/javalanche/Servers/Beacon
fi
if [ ! -d "/etc/javalanche/Servers/DnsBeacon" ]; then
    sudo mkdir /etc/javalanche/Servers/DnsBeacon
fi
if [ ! -d "/etc/javalanche/Servers/C2" ]; then
    sudo mkdir /etc/javalanche/Servers/C2
fi
if [ ! -d "/etc/javalanche/Servers/CLI" ]; then
    sudo mkdir /etc/javalanche/Servers/CLI
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
sudo curl -s -o /etc/javalanche/Servers/C2/C2ServerBeaconHandler.java https://gitlab.ritsec.cloud/jms9508/Javalanche/-/raw/$branch/Servers/C2/C2ServerBeaconHandler.java?ref_type=heads
sudo curl -s -o /etc/javalanche/Servers/C2/C2ServerAPI.java https://gitlab.ritsec.cloud/jms9508/Javalanche/-/raw/$branch/Servers/C2/C2ServerAPI.java?ref_type=heads
sudo curl -s -o /etc/javalanche/Servers/C2/pwnBoardRequest.java https://gitlab.ritsec.cloud/jms9508/Javalanche/-/raw/$branch/Servers/C2/pwnBoardRequest.java?ref_type=heads
sudo curl -s -o /etc/javalanche/Servers/Beacon/BeaconServer.java https://gitlab.ritsec.cloud/jms9508/Javalanche/-/raw/$branch/Servers/Beacon/BeaconServer.java?ref_type=heads
sudo curl -s -o /etc/javalanche/Servers/Beacon/BeaconClientHandler.java https://gitlab.ritsec.cloud/jms9508/Javalanche/-/raw/$branch/Servers/Beacon/BeaconClientHandler.java?ref_type=heads
sudo curl -s -o /etc/javalanche/Servers/Beacon/BeaconC2Handler.java https://gitlab.ritsec.cloud/jms9508/Javalanche/-/raw/$branch/Servers/Beacon/BeaconC2Handler.java?ref_type=heads
sudo curl -s -o /etc/javalanche/Servers/DnsBeacon/DnsBeaconC2Handler.java https://gitlab.ritsec.cloud/jms9508/Javalanche/-/raw/$branch/Servers/DnsBeacon/DnsBeaconC2Handler.java?ref_type=heads
sudo curl -s -o /etc/javalanche/Servers/DnsBeacon/DnsBeaconServer.java https://gitlab.ritsec.cloud/jms9508/Javalanche/-/raw/$branch/Servers/DnsBeacon/DnsBeaconServer.java?ref_type=heads
sudo curl -s -o /etc/javalanche/Servers/CLI/HTTPSRequest.java https://gitlab.ritsec.cloud/jms9508/Javalanche/-/raw/$branch/Servers/CLI/HTTPSRequest.java?ref_type=heads
sudo curl -s -o /etc/javalanche/Servers/CLI/C2ServerCLI.java https://gitlab.ritsec.cloud/jms9508/Javalanche/-/raw/$branch/Servers/CLI/C2ServerCLI.java?ref_type=heads
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

echo "[*] Downloading Web UI files & installing Apache"

sudo apt-get install apache2 -y > /dev/null

if [ -f "sudo rm /etc/apache2/ports.conf" ]; then
    sudo rm sudo rm /etc/apache2/ports.conf
fi
if [ -f "/etc/apache2/sites-available/000-default.conf" ]; then
    sudo rm /etc/apache2/sites-available/000-default.conf
fi
if [ -f "/etc/apache2/sites-available/default-ssl.conf" ]; then
    sudo rm /etc/apache2/sites-available/default-ssl.conf
fi
sudo curl -s -o /etc/apache2/ports.conf https://gitlab.ritsec.cloud/jms9508/Javalanche/-/raw/$branch/Setup/ports.conf?ref_type=heads
sudo curl -s -o /etc/apache2/sites-available/api-http.conf https://gitlab.ritsec.cloud/jms9508/Javalanche/-/raw/$branch/Setup/api-http.conf?ref_type=heads
sudo curl -s -o /etc/apache2/sites-available/api-https.conf https://gitlab.ritsec.cloud/jms9508/Javalanche/-/raw/$branch/Setup/api-https.conf?ref_type=heads
sudo curl -s -o /etc/apache2/sites-available/web-https.conf https://gitlab.ritsec.cloud/jms9508/Javalanche/-/raw/$branch/Setup/web-https.conf?ref_type=heads
sudo a2enmod proxy proxy_http ssl headers rewrite > /dev/null
sudo a2ensite api-http.conf > /dev/null
sudo a2ensite api-https.conf > /dev/null
sudo a2ensite web-https.conf > /dev/null
sudo systemctl stop apache2

if [ ! -d "/var/www/html/assets" ]; then
    sudo mkdir /var/www/html/assets
fi

# Download Web UI files
BASE_URL="https://gitlab.ritsec.cloud/jms9508/Javalanche/-/raw/$branch/Web/dist"
MANIFEST_URL="$BASE_URL/.vite/manifest.json"

sudo curl -s -o /etc/javalanche/manifest.json "$MANIFEST_URL?ref_type=heads"

# Extract all unique asset paths from "file", "css", and "assets" fields
ASSETS=$(sudo jq -r '
  to_entries[] | .value |
  [ .file ] +
  ( .css // [] ) +
  ( .assets // [] )
  | .[]
' /etc/javalanche/manifest.json | sort -u)

for asset in $ASSETS; do
  sudo curl -s -o "/var/www/html/$asset" "$BASE_URL/$asset?ref_type=heads"
done

sudo curl -s -o "/var/www/html/index.html" "$BASE_URL/index.html?ref_type=heads"
sudo curl -s -o "/var/www/html/RITSECRedTeamLogo.png" "$BASE_URL/RITSECRedTeamLogo.png?ref_type=heads"

# Download file for javac to use
sudo curl -s -o /etc/javalanche/files.txt https://gitlab.ritsec.cloud/jms9508/Javalanche/-/raw/$branch/Setup/linuxFiles.txt?ref_type=heads

# Download file cmd line tool to launch whatever server is desired
sudo curl -s -o /bin/javalanche https://gitlab.ritsec.cloud/jms9508/Javalanche/-/raw/$branch/Setup/javalanche.sh?ref_type=heads
sudo chmod +x /bin/javalanche

echo "[*] Compiling Javalanche files"

# Compile
sudo "$javacDir" -cp /etc/javalanche/Servers/json.jar "@/etc/javalanche/files.txt"

echo ""
echo "Success!"
echo "Use 'javalanche C2' to run the C2 Server"
echo "Use 'javalanche Beacon' to run the HTTPS Beacon Server"
echo "Use 'javalanche DnsBeacon' to run the DNS Beacon Server"
echo "Use 'javalanche CLI' to use the CLI"