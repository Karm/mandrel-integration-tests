#include <windows.h>
#include <stdio.h>

void ctrlC(int pid) {
    FreeConsole();
    if (AttachConsole(pid)) {
        SetConsoleCtrlHandler(NULL, true);
        GenerateConsoleCtrlEvent(CTRL_C_EVENT, 0);
    }
    exit(1);
}

int main(int argc, const char* argv[]) {
    if (argc != 2) {
        printf("provide a pid number");
        return 1;
    }
    int pid = atoi(argv[1]);
    printf("stopping pid %d", pid);
    ctrlC(pid);
    return 0;
}
