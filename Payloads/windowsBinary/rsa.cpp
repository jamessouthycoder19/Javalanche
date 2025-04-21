/*
Javalanche Windows Executable
Purpose: Contains method doing public key RSA encryption to transfer AES symmetric key
Author: James Southcott
*/

#include "rsa.h"

void rsaEncrypt(char** plaintext_hex, const char* n_str, const char* e_str) {
    BIGNUM* plaintext = BN_new();
    BIGNUM* n = BN_new();
    BIGNUM* e = BN_new();
    BIGNUM* ciphertext = BN_new();
    BN_CTX* ctx = BN_CTX_new();

    // Convert hex plaintext string to BIGNUM
    BN_hex2bn(&plaintext, *plaintext_hex);

    // Convert decimal modulus and exponent strings to BIGNUM
    BN_dec2bn(&n, n_str);
    BN_dec2bn(&e, e_str);


    // Perform modular exponentiation: ciphertext = plaintext^e mod n
    BN_mod_exp(ciphertext, plaintext, e, n, ctx);

    // Convert the ciphertext BIGNUM back to a hex string and replace plaintext parameter
    OPENSSL_free(*plaintext_hex);
    *plaintext_hex = BN_bn2hex(ciphertext);



    // Cleanup
    BN_free(plaintext);
    BN_free(n);
    BN_free(e);
    BN_free(ciphertext);
    BN_CTX_free(ctx);
}