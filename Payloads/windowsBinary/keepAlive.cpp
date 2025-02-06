/*
Javalanche Windows Executable
Purpose: Contains methods keeping sockets alive
Author: James Southcott
*/
#include "keepAlive.h"
#include "caesarCipher.h"

/*
Name: sendKeepAlive
Purpose: Keeps the clientSocket SOCKET alive by sending KEEP_ALIVE messages back and forth with the server. Time between messages is pseudo-random, between 30 and 90 seconds. Messages are in an HTTP packet
Return: Void
*/
unsigned __stdcall sendKeepAlive(SOCKET* clientSocket) {
    SOCKET clientConnection = *clientSocket;

    // Get number of seconds since January 1, 1970, UTC as the seed value for our PRNG
    unsigned long long int PRNG = time(NULL) % 100000;

    // Setting up variables for the PRNG
    char squaredStringPRNG[20];
    squaredStringPRNG[19] = '\0';
    int squaredPRNGLength;
    char stringPRNG[20];
    stringPRNG[19] = '\0';
    int numMiddleChars;

    // Every 30 seconds, send a KEEP_ALIVE message to the server, to keep the socket open
    while (1) {
        // Create KEEP_ALIVE Message and 'encrypt it'
        char keepAlive[1024] = "KEEP_ALIVE\n";
        char message[1200] = "HTTP/1.1 200 OK\r\nContent-Length: 11\r\nContent-Type: text/plain; charset=utf-8\r\n\r\n";
        encrypt(keepAlive);
        strncat_s(message, keepAlive, 12);

        // Choose Pseudo-Random Number
        // To create our PRNG, we are using the middle square method.
        // The previous value (or seed) value is squared, and then the middle 
        // 5 digits (or 6 if we have an even number of digits) are used for the next number
        // This number should be between 30000 and 90000, which is done with the formula
        // final sleep time = random number % 60000 + 30000

        // Square the current PRNG
        PRNG = PRNG * PRNG;

        // Convert PRNG from and int to a string
        _itoa_s(PRNG, squaredStringPRNG, size_t(19), 10);

        // Determine how many digits should be chosen from the middle
        squaredPRNGLength = strnlen(squaredStringPRNG, size_t(19));
        if (squaredPRNGLength % 2 == 0) {
            numMiddleChars = 6;
        }
        else {
            numMiddleChars = 5;
        }

        // Copy over the middle characters
        for (int i = 0; i < numMiddleChars; i++) stringPRNG[i] = squaredStringPRNG[i + ((squaredPRNGLength - numMiddleChars) / 2)];
        stringPRNG[((squaredPRNGLength - numMiddleChars) / 2) + numMiddleChars] = '\0';

        // Convert new PRNG from a string to an int
        PRNG = atoi(stringPRNG);

        // Sleep for a pseudo random amount of time in between 30 and 90 seconds
        Sleep(PRNG % 60000 + 30000);

        // Send KEEP_ALIVE Message
        send(clientConnection, message, strnlen(message, 120), 0);
    }
}