/*
Javalanche Linux Payload - AES Key Expansion
Author: James Southcott
*/

package aes

import (
	"strconv"
)

// Performs byte substitution AES layer on the state
//
// Parameters:
//
// - state: AES state to perform the layer on
func byteSub(state [][]int) {
	for i := 0; i < 4; i++ {
		for j := 0; j < 4; j++ {
			binary := strconv.FormatInt(int64(state[i][j]), 2)
			for len(binary) < 8 {
				binary = "0" + binary
			}
			x, _ := strconv.ParseInt(binary[0:4], 2, 64)
			y, _ := strconv.ParseInt(binary[4:8], 2, 64)

			state[i][j] = sBox[x][y]
		}
	}
}

// Performs shift rows AES layer on the state
//
// Parameters:
//
// - state: AES state to perform the layer on
func shiftRows(state [][]int) {
	// Shift left by 1
	tmp := state[0][1]
	state[0][1] = state[1][1]
	state[1][1] = state[2][1]
	state[2][1] = state[3][1]
	state[3][1] = tmp

	// Shift left by 2
	tmp1 := state[0][2]
	tmp2 := state[1][2]
	state[0][2] = state[2][2]
	state[1][2] = state[3][2]
	state[2][2] = tmp1
	state[3][2] = tmp2

	// Shift left by 3
	tmp = state[0][3]
	state[0][3] = state[3][3]
	state[3][3] = state[2][3]
	state[2][3] = state[1][3]
	state[1][3] = tmp
}

// Helper function called by mixColumns to do matrix multiplication on a column of an AES state
// Each column is multiplied by the matrix M:
//
// 02 03 01 01
//
// 01 02 03 01
//
// 01 01 02 03
//
// 03 01 01 02
//
// Parameters:
//
// - column: column to perform matrix multiplication on
func mixSingleColumn(column []int) {
	w0 := XTimes(column[0]^column[1]) ^ column[1] ^ column[2] ^ column[3]
	w1 := column[0] ^ XTimes(column[1]^column[2]) ^ column[2] ^ column[3]
	w2 := column[0] ^ column[1] ^ XTimes(column[2]^column[3]) ^ column[3]
	w3 := column[0] ^ column[1] ^ column[2] ^ XTimes(column[0]^column[3])

	column[0] = w0
	column[1] = w1
	column[2] = w2
	column[3] = w3
}

// Performs mix columns AES layer on the state
//
// Parameters:
//
// - state: AES state to perform the layer on
func mixColumns(state [][]int) {
	mixSingleColumn(state[0])
	mixSingleColumn(state[1])
	mixSingleColumn(state[2])
	mixSingleColumn(state[3])
}

// Performs AES Encryption on 1 128-bit state (block)
//
// Parameters:
//
// - state: 128-bit block to perform encryption on
//
// - subKeys: AES subkeys used for encryption. Can be generated with GenerateRoundKeys(masterKey string)
func Encrypt(state [][]int, subkeys []string) {
	XOR(state, subkeys[0])
	for i := 0; i < 9; i++ {
		byteSub(state)
		shiftRows(state)
		mixColumns(state)
		XOR(state, subkeys[i+1])
	}
	byteSub(state)
	shiftRows(state)
	XOR(state, subkeys[10])
}
