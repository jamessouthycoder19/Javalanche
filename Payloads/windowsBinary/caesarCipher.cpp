#include "caesarCipher.h"


/*
Name: Encrypt
Purpose: Encrypt's plain text by rotating each character by 13 (Caesar Cipher). The encrypted text is stored in the pointer that is passed as a parameter
Return: Void

*/
void encrypt(char* plainText) {
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