/*
Javalanche Windows Executable
Purpose: Contains both Main method, as well as Service Methods for the exeuctable to run as a service
Author: James Southcott
*/
#include "aes.h"
#include "keyExpansion.h"
#include "encryption.h"
#include "decryption.h"


/**
 * Helper function used by many AES functions to do n * x within GF(2^8)
 *
 * @param n[in] - n, where n exists in GF(2^8). n will be multiplied by x
 * @return n * x modulo AES irreducible polynomial
 */
uint8_t xTimes(uint8_t b) {
    return (b & 0x80) ? ((b << 1) ^ 0x1B) : (b << 1);
}

uint8_t sBox[16][16] = {
    {99, 124, 119, 123, 242, 107, 111, 197, 48, 1, 103, 43, 254, 215, 171, 118},
    {202, 130, 201, 125, 250, 89, 71, 240, 173, 212, 162, 175, 156, 164, 114, 192},
    {183, 253, 147, 38, 54, 63, 247, 204, 52, 165, 229, 241, 113, 216, 49, 21},
    {4, 199, 35, 195, 24, 150, 5, 154, 7, 18, 128, 226, 235, 39, 178, 117},
    {9, 131, 44, 26, 27, 110, 90, 160, 82, 59, 214, 179, 41, 227, 47, 132},
    {83, 209, 0, 237, 32, 252, 177, 91, 106, 203, 190, 57, 74, 76, 88, 207},
    {208, 239, 170, 251, 67, 77, 51, 133, 69, 249, 2, 127, 80, 60, 159, 168},
    {81, 163, 64, 143, 146, 157, 56, 245, 188, 182, 218, 33, 16, 255, 243, 210},
    {205, 12, 19, 236, 95, 151, 68, 23, 196, 167, 126, 61, 100, 93, 25, 115},
    {96, 129, 79, 220, 34, 42, 144, 136, 70, 238, 184, 20, 222, 94, 11, 219},
    {224, 50, 58, 10, 73, 6, 36, 92, 194, 211, 172, 98, 145, 149, 228, 121},
    {231, 200, 55, 109, 141, 213, 78, 169, 108, 86, 244, 234, 101, 122, 174, 8},
    {186, 120, 37, 46, 28, 166, 180, 198, 232, 221, 116, 31, 75, 189, 139, 138},
    {112, 62, 181, 102, 72, 3, 246, 14, 97, 53, 87, 185, 134, 193, 29, 158},
    {225, 248, 152, 17, 105, 217, 142, 148, 155, 30, 135, 233, 206, 85, 40, 223},
    {140, 161, 137, 13, 191, 230, 66, 104, 65, 153, 45, 15, 176, 84, 187, 22}
};

uint8_t inverseSBox[16][16] = {
    {82, 9, 106, 213, 48, 54, 165, 56, 191, 64, 163, 158, 129, 243, 215, 251},
    {124, 227, 57, 130, 155, 47, 255, 135, 52, 142, 67, 68, 196, 222, 233, 203},
    {84, 123, 148, 50, 166, 194, 35, 61, 238, 76, 149, 11, 66, 250, 195, 78},
    {8, 46, 161, 102, 40, 217, 36, 178, 118, 91, 162, 73, 109, 139, 209, 37},
    {114, 248, 246, 100, 134, 104, 152, 22, 212, 164, 92, 204, 93, 101, 182, 146},
    {108, 112, 72, 80, 253, 237, 185, 218, 94, 21, 70, 87, 167, 141, 157, 132},
    {144, 216, 171, 0, 140, 188, 211, 10, 247, 228, 88, 5, 184, 179, 69, 6},
    {208, 44, 30, 143, 202, 63, 15, 2, 193, 175, 189, 3, 1, 19, 138, 107},
    {58, 145, 17, 65, 79, 103, 220, 234, 151, 242, 207, 206, 240, 180, 230, 115},
    {150, 172, 116, 34, 231, 173, 53, 133, 226, 249, 55, 232, 28, 117, 223, 110},
    {71, 241, 26, 113, 29, 41, 197, 137, 111, 183, 98, 14, 170, 24, 190, 27},
    {252, 86, 62, 75, 198, 210, 121, 32, 154, 219, 192, 254, 120, 205, 90, 244},
    {31, 221, 168, 51, 136, 7, 199, 49, 177, 18, 16, 89, 39, 128, 236, 95},
    {96, 81, 127, 169, 25, 181, 74, 13, 45, 229, 122, 159, 147, 201, 156, 239},
    {160, 224, 59, 77, 174, 42, 245, 176, 200, 235, 187, 60, 131, 83, 153, 97},
    {23, 43, 4, 126, 186, 119, 214, 38, 225, 105, 20, 99, 85, 33, 12, 125}
};


/**
 * xor the current 128-bit AES state with a 128-bit roundkey. 
 *
 * @param state[in, out] - 128-bit AES state
 * @param roundKey[in] - 128-bit roundkey
 * @return void
 */
void xorWithSubkey(int** state, uint8_t* roundKey) {
    for (int i = 0; i < 4; i++) {
        for (int j = 0; j < 4; j++) {
            state[i][j] = state[i][j] ^ roundKey[i * 4 + j];
        }
    }
}


/**
 * Takes a string, and turns it into an array of AES states (3d int array) ready for encryption
 *
 * @param plainText[in] - String to be turned into array of AES states
 * @param decrypt[in] - true if decryption is occuring, false if encrypting is occuring
 * @param decNumBytes[in] - length of the plainText. This value is only necessary when decrypt is set to true
 * @return 3d integer array. return[0] is AES state #1, return[1] is AES state #2, etc.
 */
int*** stringToIntArray(char* plainText, bool decrypt, int decNumBytes) {
    // Determine the number of states to be created, and how long the plain text is

    int lengthPlainText;
    int numStates;
    if (!decrypt) {
        lengthPlainText = strnlen(plainText, (size_t)10000);
        numStates = lengthPlainText / 16 + 1;
    }
    else {
        lengthPlainText = decNumBytes;
        numStates = decNumBytes / 16;
    }

    // Initialize the 3d array, each 2d array is a aes 128-bit state
    int*** states = (int***)malloc(numStates * sizeof(int**));
    for (int i = 0; i < numStates; i++) {
        states[i] = (int**)malloc(4 * sizeof(int*));
        for (int j = 0; j < 4; j++) {
            states[i][j] = (int*)malloc(4 * sizeof(int));
        }
    }


    // Iterate through and populate the new arrays
    int index = 0;
    for (int numArray = 0; numArray < numStates; numArray++) {
        for (int i = 0; i < 4; i++) {
            for (int j = 0; j < 4; j++) {
                if (index < lengthPlainText) {
                    // Enter normal converted byte
                    states[numArray][i][j] = (unsigned char)plainText[index];
                }
                else if (index == lengthPlainText) {
                    // The first byte after the normal bytes are done being entered is 128, aka 10000000
                    // This final 1 signifies that all the 0's after (and the final 1), are not a part of
                    // the decoded text
                    states[numArray][i][j] = 128;
                }
                else {
                    states[numArray][i][j] = 0;
                }
                index++;
            }
        }
    }
    return states;
}


/**
 * Takes in multiple AES states (3d int array), and returns the string value of these states
 *
 * @param asciiArray[in] - 3d int array of AES states
 * @param numStates[in] - number of AES states to be converted
 * @param outBuf[out] - buffer that the string of the AES states will be stored in.
 * @param encrypt[in] - true if encryption is occuring, false if decryption is occuring
 * @return void
 */
void intArrayToStringMultiple(int*** asciiArray, int numStates, char* outBuf, bool encrypt) {
    int num = ((16 * numStates) + 1);
    char* result = (char*)malloc(((16 * numStates) + 1) * sizeof(signed char));

    // First, iterate backwards through this 3d array, to find the final bit (it will look something like 128 0 0 0 0)
    // This final bit signifies the place to stop decoding text, as the rest of it is just filler
    int totalChars = numStates * 16;
    int count = 0;

    // outerloop bascially just tells the break statement to break out of all 3 for loops when the correct value is found
    for (int i = numStates - 1; i > -1; i--) {
        for (int j = 3; j > -1; j--) {
            for (int k = 3; k > -1; k--) {
                count++;
                if ((asciiArray[i][j][k] == 128) && !encrypt) {
                    k = 0;
                    j = 0;
                    i = 0;
                }
            }
        }
    }

    // Subtract the total number of bytes + the 128 byte that we found
    totalChars -= count;

    // Iterate through the arrays, and append characters until the final 1 is found (determined earlier)
    count = 0;

    for (int i = 0; i < numStates; i++) {
        for (int j = 0; j < 4; j++) {
            for (int k = 0; k < 4; k++) {
                int num = (i * 16) + (j * 4) + k;

                result[count] = (signed char)asciiArray[i][j][k];
                count++;
                if (count == totalChars) {
                    result[count] = '\0';
                    k = 3;
                    j = 3;
                    i = numStates - 1;
                }
            }
        }
    }
    memcpy(outBuf, result, (16 * numStates) + 1);
    free(result);
}


/**
 * Performs AES encryption on the plain text. 128-bit, 192-bit, and 256-bit keys are supported. Currently, the only supported mode is ECB mode.
 *
 * @param plainText[in, out] - plainText to be encrypted. This buffer will store the cipher text when the function is done
 * @param masterKey[in] - 16 byte (128-bit), 24 byte (192-bit), or 32 byte (256-bit) key for AES.
 * @param numSubKeys[in] - 11, 13, or 15, depending on the length of masterKey.
 * @return void
 */
void aesEncrypt(char* plainText, uint8_t* masterKey, int numSubKeys) {
    uint8_t* roundKeys = (uint8_t*)malloc(sizeof(uint8_t) * 16 * numSubKeys);

    // Perform key expansion
    keyExpansion(masterKey, roundKeys, numSubKeys);

    // Figure out the number of states
    int numStates = strnlen(plainText, rsize_t(10000)) / 16 + 1;

    // Encode from text to 3d array, each array is a 2d aes state
    int*** states = stringToIntArray(plainText, false, 0);
    
    // Encrypt state
    for (int i = 0; i < numStates; i++) {
        encrypt(states[i], roundKeys, 11);
    }

    // Convert the integers back to a string to send
    char* decodedText = (char*)malloc(((16 * numStates) + 1) * sizeof(signed char));
    intArrayToStringMultiple(states, numStates, decodedText, true);


    for (int i = 0; i < numStates; i++) {
        for (int j = 0; j < 4; j++) {
            free(states[i][j]);
        }
        free(states[i]);
    }
    free(states);

    // Copy this memory back into the plaintext buffer
    memcpy(plainText, decodedText, rsize_t((16 * numStates) + 1));
    free(decodedText);
    free(roundKeys);
}


/**
 * Performs AES Decryption on the plain text. 128-bit, 192-bit, and 256-bit keys are supported. Currently, the only supported mode is ECB mode.
 *
 * @param cipherText[in, out] - cipherText to be decrypted. This buffer will store the plain text when the function is done
 * @param masterKey[in] - 16 byte (128-bit), 24 byte (192-bit), or 32 byte (256-bit) key for AES.
 * @param numSubKeys[in] - 11, 13, or 15, depending on the length of masterKey
 * @param numBytes[in] - length of the cipher text
 * @return void
 */
void aesDecrypt(char* cipherText, uint8_t* masterKey, int numSubkeys, int numBytes) {
    uint8_t* roundKeys = (uint8_t*)malloc(sizeof(uint8_t) * 16 * numSubkeys);

    // Perform key expansion
    keyExpansion(masterKey, roundKeys, numSubkeys);

    // Figure out the number of states
    int numStates = numBytes / 16;

    // Encode from text to 3d array, each array is a 2d aes state
    int*** states = stringToIntArray(cipherText, true, numBytes);

    // Decrypt states
    for (int i = 0; i < numStates; i++) {
        decrypt(states[i], roundKeys, 11);
    }

    char* decodedText = (char*)malloc(((16 * numStates) + 1) * sizeof(signed char));
    intArrayToStringMultiple(states, numStates, decodedText, false);


    for (int i = 0; i < numStates; i++) {
        for (int j = 0; j < 4; j++) {
            free(states[i][j]);
        }
        free(states[i]);
    }
    free(states);

    // Copy this memory back into the plaintext buffer
    memcpy(cipherText, decodedText, rsize_t((16 * numStates) + 1));
    free(decodedText);
    free(roundKeys);
}


/**
 * Takes in a string of length n, where n = 16, 24, or 32. Converts this string, into an int array[n], continaing all of the values from the string in hex. Used to take plaintext AES key, and turn it into an int array usable by aesEncrypt and aesDecrypt.
 *
 * @param hexString[in] - string to be turned into the array. Should be of length 16, 24, or 32
 * @param masterKey[out] - int array that will hold the hexString as an int array, should be able to hold the same amount of data as hexString.
 * @return numRounds. if the length of hexString n = 16, then numRounds = 11, n = 24 then numRounds = 13, n = 32, then numRounds = 15. This value is also passed to aesEncrypt and aesDecrypt
 */
int hexStringToByteArray(const char* hexString, uint8_t* masterKey) {
    size_t length = strlen(hexString) / 2; // Calculate the number of bytes
    for (size_t i = 0; i < length; ++i) {
        char byteString[3] = { hexString[i * 2], hexString[i * 2 + 1], '\0' }; // Two hex chars + null terminator
        masterKey[i] = (uint8_t)strtol(byteString, NULL, 16); // Convert hex to byte
    }

    int keySizeInBytes = strnlen(hexString, 65) / 2;
    int numRounds;
    if (keySizeInBytes == 16) {
        numRounds = 11;
    }
    else if (keySizeInBytes == 24) {
        numRounds = 13;
    }
    else if (keySizeInBytes == 32) {
        numRounds = 15;
    }
    else {
        numRounds = 0;
    }

    return numRounds;
}


/**
 * Takes a non-encoded string, and encodes the string according to UTF8
 *
 * @param input[in, out] - string to be encoded. this string will store the encoded text when the function is done
 * @param numChars[in] - length of the input buffer
 * @return new length of the input buffer
 */
int encodeStringToUTF8(char* input, int numChars) {
    size_t length = numChars;
    size_t outputSize = length * 2 + 1; // Each byte could take up to 2 bytes in UTF-8
    char* output = (char*)malloc(outputSize);
    size_t outputIndex = 0;

    for (size_t i = 0; i < length; i++) {
        unsigned char byte = input[i];
        if (byte < 128) {
            // Single-byte UTF-8 (ASCII)
            output[outputIndex++] = byte;
        }
        else {
            // Multi-byte UTF-8 for extended ASCII
            output[outputIndex++] = 0xC0 | (byte >> 6);   // First byte: 110xxxxx
            output[outputIndex++] = 0x80 | (byte & 0x3F); // Second byte: 10xxxxxx
        }
    }
    output[outputIndex] = '\0'; // Null-terminate the string
    memcpy(input, output, outputIndex + 1);
    free(output);
    return outputIndex + 1;
}


/**
 * Takes a UTF8 encoded string, and decodes it
 *
 * @param input[in, out] - string to be decoded. this string will store the decoded text when the function is done
 * @param numChars[in] - length of the input buffer
 * @return void
 */
void decodeUTF8String(char* input, int numChars) {
    size_t length = numChars;
    char* output = (char*)malloc(length + 1); // Allocate enough memory
    size_t outputIndex = 0;

    for (size_t i = 0; i < length; i++) {
        if ((input[i] & 0x80) == 0) {
            // Single-byte UTF-8 (ASCII)
            output[outputIndex++] = input[i];
        }
        else if ((input[i] & 0xE0) == 0xC0) {
            // Multi-byte UTF-8
            unsigned char firstByte = input[i++];
            unsigned char secondByte = input[i];
            unsigned char decodedByte = ((firstByte & 0x1F) << 6) | (secondByte & 0x3F);
            output[outputIndex++] = decodedByte;
        }
    }

    output[outputIndex] = '\0'; // Null-terminate the string
    memcpy(input, output, outputIndex);
}


/**
 * Prints the current AES state
 *
 * @param state[in] - the AES state to be printed
 * @return void
 */
void printState(int** state) {
    for (int i = 0; i < 4; i++) {
        printf("[%d, %d, %d, %d], ", state[i][0], state[i][1], state[i][2], state[i][3]);
    }
    printf("\n");
}