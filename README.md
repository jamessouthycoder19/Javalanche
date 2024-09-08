This is a tool developed by James Southcott and Danny Nichols for the RITSEC Red Team Recruiting Process.

This tool is meant to be used in Red Vs. Blue style Cybersecurity Competitions for educational purposes.

Plans

Main C2 Server - Java

    C2Server.java - Listen for connections from Long Range Beacons. Interacts with both the Long Range 
    Beacon Handler Threads, as well as the User Handler Thread.

    C2ServerBeaconHandler.java - Every time a connection from a new Long Range Beacon Server is created, 
    a new thread is created using this class. This thread will listen for messages from the Beacons, 
    based on whatever the User Operating the C2 requests. These threads interact with the Main C2 Server.
    
    C2ServerUserHandler.java - One thread is created at the beginning of the execution of the C2 Server. 
    This thread is what the User Operating the C2 will interact with via CLI. This thread interacts with 
    the main C2 Server.
    

Long Range Beacon Server - Java

    BeaconServer.java - The Main Server for the Long Range Beacon. When this server starts up, a new thread 
    will be created using the BeaconC2Server.java class. This server will listen for connections new 
    clients (victims). Whenever a new client makes a connection, an initial payload will be given accordingly. 
    Following this, a new thread will be created using the BeaconClientHandler.java class.

    BeaconClientHandler.java - After a client makes a connection, this class will be used to create a new thread. 
    This thread is responsible for receiving input back from each client, after the Long Range Beacon Server sends 
    commands to the victims.

    BeaconC2Handler.java - When the Long Range Beacon Server is started, a new thread is started from this class. 
    This thread is responsible for interacting with the C2 Server, accepting commands from the C2, and taking the 
    proper action based on these commands. All of these commands map to one or more functions defined in 
    BeaconServer.java. These commands will do one of two things; 1. Send commadns to Victims (ex. ps> New-ADUser); 
    2. Provide data to the C2 Server (ex. Connection Status of all of the vicims, responses of victims to certain 
    commands)

Payloads
    TBD