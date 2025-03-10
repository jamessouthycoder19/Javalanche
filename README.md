Javalanche is a tool developed by James Southcott and Danny Nichols for the RITSEC Red Team

This tool is meant to be used in Red Vs. Blue style Cybersecurity Competitions for educational purposes.

# Network Diagram:

![alt text](Images/C2NetworkDiagram.drawio.png)

# Deployment

**Agents**

We recommend using Ansible to remotely deploy Windows and Linux agents.

```
Prerequisites: A Linux machine with SSH access to all linux competition mahcines, and winrm access to all windows compeitition machines
$ sudo apt update
$ sudo apt upgrade -y
$ sudo apt install ansible ssh sshpass nano git -y
Note: the above dependencies satisfy windows deployment via winrm, and linux deployment via ssh. 
if deploying windows via psrp, the additional 2 commands will need to be run
$ sudo apt install pip -y
$ pip install pypsrp
$ git clone https://gitlab.ritsec.cloud/jms9508/Javalanche
$ cd Javalanche/Setup/Ansible
Note: In some competitions, you will be provided an inventory.yml file for deployment. 
You will have to edit the hosts: line in the playbook to reflect the group of hosts to deploy to.
If you are not given an inventory.yml file, you will need to edit the existing inventory.yml 
file to include the proper hosts, usernames, and passwords
$ ansible-playbook -i inventory/inventory.yml playbook.yml -deploy
```

**Redirect Server**

The Redirect Server shortens many different URL's, to make deployment of agents and servers much easier

```
$ sudo apt update
$ sudo apt upgrade -y
$ sudo apt install curl -y
$ sudo curl -o serverSetup.sh https://gitlab.ritsec.cloud/jms9508/Javalanche/-/raw/main/Setup/redirectServerSetup.sh?ref_type=heads
$ sudo chmod +x serverSetup.sh
$ sudo ./serverSetup.sh
```

**C2 Server**

We recommend Deploying the C2 Server on a linux machine, for the best experience

```
$ sudo apt update
$ sudo apt upgrade -y
$ sudo apt install curl tmux -y
$ sudo mkdir /home/javalanche
$ cd /home/javalanche
$ sudo curl -L -o serverSetup.sh http://javalanche.net/linuxServerSetup
$ sudo chmod +x serverSetup.sh
$ tmux new -s c2
$ sudo ./serverSetup.sh
```

**Proxy Server**

We recommend Deploying the Long Range Beacon Servers on a linux machine, for the best experience.

```
Prerequisite: The Proxy Server must have a Public IP Address. If you are not able to obtain a Public IP Address, 
then both the Proxy and C2 Servers must be deployed on the same WAN as the competition machines.
$ sudo apt update
$ sudo apt install curl tmux -y
$ sudo mkdir /home/javalanche
$ cd /home/javalanche
$ sudo curl -L -o serverSetup.sh http://javalanche.net/linuxServerSetup
$ sudo chmod +x serverSetup.sh
$ tmux new -s c2
$ sudo ./serverSetup.sh -server Beacon
```


Main C2 Server - Java

    C2Server.java - Listen for connections from Long Range Beacons, and authenticate new Beacons. Interacts with the 
    Long Range Beacon Handler Threads, as well as the User Handler Thread.

    C2ServerBeaconHandler.java - Every time a connection from a new Long Range Beacon Server is created, 
    a new thread is created using this class. This thread will send and receive messages to and from the 
    Beacons. These threads interact with the Main C2 Server.
    
    C2ServerUserHandler.java - One thread is created at the beginning of the execution of the C2 Server. 
    This thread is what the User Operating the C2 will interact with via CLI. This thread interacts with 
    the main C2 Server. There are 6 options for the User when using the CLI:
    1. Send Commands to one or many clients
    2. Enter shell of individual client
    3. Launch an attack chain
    4. Request data from clients
    5. Get status from Clients
    6. Shutdown
    Options 1, 2, 3, and 5 underneath all work the same "under the hood", they are just sending commands to clients.
    Option 1 is mainly used for distributing commands to many clients at once, Option 2 is for testing out commands
    on a specific client, Option 3 is for more involved attacks/breaks that have been written out ahead of time. Finally,
    Option 5 is sending a specific command, and checks to see if it got the expected output, to determine if command
    execution is still available on this machine
    Option 4 simply requests data that is stored in the long range beacon servers
    

Long Range Beacon Server - Java

    BeaconServer.java - The Main Server for the Long Range Beacon. This server will listen for connections new 
    clients (victims). Whenever a new client makes a connection, a new thread will be created using the 
    BeaconClientHandler.java class. The BeaconServer is responsible for maintaining data structures of all of
    it's current clients, as well as all responses to commands from clients.

    BeaconClientHandler.java - After a client makes a connection, this class will be used to create a new thread. 
    This thread is responsible for sending and receiving input to each client, after the Long Range Beacon Server sends 
    commands to the victims. This thread is also responsible for encryption/decryption (currently a simple 13 character 
    rotational cipher) of messages between the Beacon Server and the client. Finally, this thread is also responsible
    for updating PWNBOARD (pwnboard.win/pwnboard), a website our red team uses to determine which machines we have
    access to.

    BeaconC2Handler.java - When the Long Range Beacon Server is started, a new thread is started from this class. 
    This thread is responsible for interacting with the C2 Server, accepting commands from the C2, and taking the 
    proper action based on these commands. All of these commands map to one or more functions defined in 
    BeaconServer.java. These commands will do one of two things; 1. Send commands to Victims (ex. ps> New-ADUser); 
    2. Provide data to the C2 Server (ex. responses of victims to certain commands)

Networking

    Duplexer.java - This Java class is utilized by all of the Servers. It is meant to simplify networking java networking
    instructions, down to just send() and receive().

    KeepAlive.java - This Java class is used by both C2ServerBeaconHandler.java, BeaconC2Handler.java, and 
    BeaconClientHandler.java. Java network sockets time out after a while, so this class is responsible for sending 
    KEEP_ALIVE messages back and forth between the sockets, to ensure that the connections stay alive. The messages
    are sent at random intervals between 30 and 90 seconds  

Payloads

    windowsBinary.exe - This executable is currently the main payload for all Windows Clients. Written in C++, the binary
    utilizes the WinAPI extensively. The payload is a reverse shell, meaning that the payload iniializes the connection 
    with the beacon server. The payload is responsible for exeucting commands, and encrypting/decrypting messages
    (currently a 13 character caesar cipher) sent back and forth from the server.

    linuxBinary - This executable is currently the main payload for all Linux clients. Written in Golang, this payload is
    very similar in functionality to the windows payload, initializing the connection with the beacon server, executing
    commands, and encrypting/decrypting messages.

Server Deployment

    serverSetup.sh - This bash script is responsible for deploying either the C2 Server or Beacon Server to a linux machine.
    the script will install Java if not already installed, create firewall rules for the necessary ports (1234 on C2 server, 
    80 on the beacon servers), and download all files necessary to run the servers. 

    serverSetup.ps1 - This powershell script is responsbile for deploying either the C2 Server or Beacon Server to a windows
    machine. It handles all of the same tasks as serverSetup.sh, installing Java if necessary, creating firewall rules, and
    downloading files. We do not recommend deploying the Javalanche servers to windows however, due to the CLI's appearence
    being built for linux, as well as the overhead of running Windows as a server.

    linuxFiles.txt / windowsFiles.txt - These files are used when compiling Java.

Agent Deployment (Ansible)

    playbook.yml - This is the main ansible playbook for deploying Javalanche agents, to both Windows and Linux. Many tags are available for use to limit what is deployed. For first deployments, use -deploy to deploy payloads to all windows and 
    linux machines specified in inventory.yml. -windows will only deploy to windows machines, -linux will only deploy to 
    linux machines. -windowsRestart will restart the payload on all windows machines, -linuxRestart will restart the
    payload on all linux machines, and -restart will restart the payload on all machines in general

    inventory.yml - Default inventory file for Ansible Deployment. Typically in Red vs. Blue competitions a competition
    organizer will provide an ansible inventory file, but this default file can be used for testing and other things.

    windows role - This role Deploys the Windows payload to all windows machines. The Payload is registered as a service, and 
    started. The executable file path, executable file name, service name, and service description are all chosen randomly
    from a set of defined paths/names/descriptions.

    linux role - This role deploys the linux payload to all linux machines. The Payload runs as a daemon, with the daemon name,
    daemon description, and file name randomized. The file is located in /bin.

    windowsRestart role - This role will restart the service on all windows machines. While it may look like there are a lot of errors generated when running this role, this is simply due to all of the possible names of the service being restarted,
    while only one actually exists on the given client.

    linuxRestart role - This role will restart the daemon on all linux machines. While it may look like there are a lot of errors generated when running this role, this is simply due to all of the possible names of the daemon being restarted,
    while only one actually exists on the given client.

UML:
![alt text](<Images/BabysFirstC2UML.drawio(1).png>)

# TODO

Fix Windows Attack Chains

Fix Linux Attack Chains

Look into Bitsadmin to transfer files,

the goose,

Implement AES + RSA for HTTPS

Have the payloads communicate with each other should they not be able to reach a Beacon
    First check to see if the payload can still communicate with desired client, if it can, just send the message to that one, else, send a message to all of the other clients seeing if someone else can communicate with it.

Make pwnboard request run in it's own thread to speed things up