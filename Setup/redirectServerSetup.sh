#!/bin/bash

# Install apache web server
sudo apt install apache2 -y

apt-get update
apt-get install -y certbot
certbot certonly -d javalanche.net -d *.javalanche.net --agree-tos --email james.southcott19@gmail.com --manual --preferred-challenges dns

# find the line number where we want to insert our lines
linenumber=$(awk '
/<Directory \/var\/www\/>/ {
  getline second
  getline third
  getline fourth
  getline fifth
  if (second ~ /Options Indexes FollowSymLinks/ && third ~ /AllowOverride None/ && fourth ~ /Require all granted/ && fifth ~ /<\/Directory>/)
    print NR+2
}
' /etc/apache2/apache2.conf)

sed -i "$linenumber i\\
Redirect /linuxServiceFile https://raw.githubusercontent.com/jamessouthycoder19/Javalanche/refs/heads/main/Payloads/httpsBinary/linuxServiceFile.service\\
Redirect /linuxPayload https://github.com/jamessouthycoder19/Javalanche/raw/refs/heads/main/Payloads/httpsBinary/linux\\
Redirect /windowsPayload https://github.com/jamessouthycoder19/Javalanche/raw/refs/heads/main/Payloads/httpsBinary/windows.exe\\
Redirect /install.sh https://raw.githubusercontent.com/jamessouthycoder19/Javalanche/refs/heads/main/Setup/install.sh\\
Redirect / https://github.com/jamessouthycoder19/Javalanche\\
" /etc/apache2/apache2.conf

sudo curl -s -o /etc/apache2/sites-available/redirect-https.conf https://raw.githubusercontent.com/jamessouthycoder19/Javalanche/refs/heads/main/Setup/redirect-https.conf

sudo a2enmod proxy proxy_http ssl headers rewrite > /dev/null
sudo a2ensite redirect-https.conf > /dev/null

sudo systemctl restart apache2