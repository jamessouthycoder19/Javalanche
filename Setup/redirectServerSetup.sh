#!/bin/bash
# Install apache web server
sudo apt install apache2 -y

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
Redirect /linuxServiceFile https://gitlab.ritsec.cloud/jms9508/Javalanche/-/raw/main/Payloads/linuxBinary/linuxServiceFile.service?ref_type=heads\\
Redirect /linuxPayload https://gitlab.ritsec.cloud/jms9508/Javalanche/-/raw/main/Payloads/linuxBinary/main?ref_type=heads\\
Redirect /windowsPayload https://gitlab.ritsec.cloud/jms9508/Javalanche/-/raw/main/Payloads/windowsBinary/x64/Debug/windowsBinary.exe?ref_type=heads\\
Redirect /install.sh https://gitlab.ritsec.cloud/jms9508/Javalanche/-/raw/main/Setup/install.sh?ref_type=heads\\
Redirect /windowsServerSetup https://gitlab.ritsec.cloud/jms9508/Javalanche/-/raw/main/Setup/ServerSetup.ps1?ref_type=heads\\
Redirect / https://gitlab.ritsec.cloud/jms9508/Javalanche\\
" /etc/apache2/apache2.conf

sudo curl -s -o /etc/apache2/sites-available/redirect-https.conf https://gitlab.ritsec.cloud/jms9508/Javalanche/-/raw/main/Setup/redirect-https.conf?ref_type=heads

sudo a2enmod proxy proxy_http ssl headers rewrite > /dev/null
sudo a2ensite redirect-https.conf > /dev/null

sudo systemctl restart apache2