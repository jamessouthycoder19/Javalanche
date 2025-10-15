/*
Javalanche Linux Payload - RSA functions
Author: James Southcott
*/

package rsa

import (
	"math/big"
)

// Performs RSA encryption
//
// Parameters:
//
// - n: n RSA Public key, used as modulus
//
// - e: e RSA public key, used as exponent
//
// - plaintext: Base-16 String to be encrypted
//
// Returns:
//
// - string: plaintext^e mod n
func RsaEncrypt(n string, e string, plaintext string) string {
	// Allocate  memory for the big integers
	nInt := new(big.Int)
	eInt := new(big.Int)
	plainTextInt := new(big.Int)
	cipherTextInt := new(big.Int)

	// Set the big integers to their values
	nInt.SetString(n, 10)
	eInt.SetString(e, 10)
	plainTextInt.SetString(plaintext, 16)

	// Do RSA encryption on the plain text
	cipherTextInt.Exp(plainTextInt, eInt, nInt)

	// Return the cipher text
	return cipherTextInt.Text(16)
}
