/*
Javalanche Windows Executable
Purpose: Contains methods keeping sockets alive
Author: James Southcott
*/
#include "keepAlive.h"
#include "caesarCipher.h"
#include "aes.h"


/**
 * Keeps the clientSocket SOCKET alive by sending KEEP_ALIVE messages back and forth with the server. Time between messages is pseudo-random, between 30 and 90 seconds. 
 *
 * @param arg[in] - KeepAliveParams struct that contains the client socket, AES master key, and the number of rounds for aes encryption
 * @return void
 */
unsigned __stdcall sendKeepAlive(void* arg) {
    KeepAliveParams* params = (KeepAliveParams*)arg;
    SOCKET* clientSocket = params->clientSocket;
    uint8_t* masterKey = params->masterKey;
    int numRounds = params->numRounds;

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
        char keepAlive[60] = "KEEP_ALIVE";

        aesEncrypt(keepAlive, masterKey, numRounds);

        int totalBytesSent = encodeStringToUTF8(keepAlive, 16);
        char* finalBuffer = (char*)malloc(sizeof(char) * 60);
        sprintf_s(finalBuffer, 60, "%d\n%s", 16, keepAlive);

        totalBytesSent += 3;


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

        // Sleep for a pseudo random amount of time in between 120 and 240 seconds
        Sleep(PRNG % 120000 + 120000);

        // Send KEEP_ALIVE Message
        send(clientConnection, finalBuffer, totalBytesSent, 0);
    }
}