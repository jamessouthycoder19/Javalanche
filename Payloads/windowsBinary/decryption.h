#pragma once

#include <stdint.h>
#include <stdio.h>

void decrypt(int** state, uint8_t* roundKeys, int numSubKeys);