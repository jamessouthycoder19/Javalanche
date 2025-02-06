#pragma once

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <winsock2.h>
#include <time.h>

unsigned __stdcall sendKeepAlive(SOCKET* clientSocket);