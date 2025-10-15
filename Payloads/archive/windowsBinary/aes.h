#pragma once

#include <string.h>
#include <stdio.h>
#include <stdlib.h>
#include <stdint.h>

int*** stringToIntArray(char* plainText, bool decrypt, int decNumBytes);

void intArrayToStringMultiple(int*** asciiArray, int numStates, char* out, bool encrypt);

uint8_t xTimes(uint8_t b);

void printState(int** state);

extern uint8_t sBox[16][16];

extern uint8_t inverseSBox[16][16];

void xorWithSubkey(int** state, uint8_t* roundKey);

void aesDecrypt(char* cipherText, uint8_t* masterKey, int numSubKeys, int numBytes);

void aesEncrypt(char* plainText, uint8_t* masterKey, int numSubKeys);

int hexStringToByteArray(const char* hexString, uint8_t* masterKey);

int encodeStringToUTF8(char* input, int numChars);

void decodeUTF8String(char* input, int numChars);