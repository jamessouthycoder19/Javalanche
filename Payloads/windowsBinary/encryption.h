#pragma once

#include <stdint.h>
#include <stdio.h>

void encrypt(int** state, uint8_t* roundKeys, int numSubKeys);