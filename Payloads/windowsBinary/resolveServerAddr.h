#pragma once

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <winsock2.h>
#include <ws2tcpip.h>
#include <time.h>
#pragma comment(lib,"ws2_32.lib")

int resolveBeaconServerIPAddr(char* ipAddressBuf);