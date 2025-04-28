Javalanche is a tool developed by James Southcott and Danny Nichols for the RITSEC Red Team

This tool is meant to be used in Red Vs. Blue style Cybersecurity Competitions for educational purposes.

# Network Diagram:

![alt text](Images/C2NetworkDiagram.drawio.png)

# Deployment

**Installation**

For a production ready instance, copy our installation one liner into a terminal
```
$ sudo curl -L http://javalanche.net/install.sh | bash
```

For Developers, see the following to build javalanche from your development branch
```
$ sudo curl -L -o install.sh http://javalanche.net/install.sh
$ ./install.sh -branch your-dev-branch
```

**C2 Server**

To run the C2 Server

```
$ javalanche C2
```

**Proxy Server**

At least 1 Proxy Server is required for Javalanche. The Proxy Server must have a Public IP Address. 
If you are not able to obtain a Public IP Address, then both the Proxy and C2 Servers must be deployed on the 
same WAN as the competition machines.

```
$ javalanche Beacon
```

For extended documentation, see Setup/setupDocumentation.txt or Setup/serverDocumentation.txt