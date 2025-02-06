/*
Javalanche Windows Executable
Purpose: Contains method for resolving IP Addresses of javalanche servers
Author: James Southcott
*/

#include "resolveServerAddr.h"

/*
Name: resolveBeaconServerIPAddr
Purpose: Attempts to resolve a Beacon (beaconX.javalanche.net, x exists in {1, 2, 3, 4, 5}), and ensures that this host is able to communicate with the Beacon via HTTP
Param: ipAddressBuf - Pointer that will contain the resolved IPv4 Address of the Beacon Server
Return Value:
// Return values
0 - Success, ip successfully resolved
1 - WSAStartup could not be initialized
2 - Resolving ip address method failed
3 - No ip addresses could be successfully resolved
*/
int resolveBeaconServerIPAddr(char* ipAddressBuf) {

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
                if (connectResult == 0) {
                    closesocket(resolvedClientSocket);
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