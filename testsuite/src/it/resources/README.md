# CtrlC.exe, CtrlC.cpp

When you start Quarkus on Windows as a process without
a console window, you cannot stop it gently with TASKKILL, 
you have to use TASKKILL /F. That terminates the process immediately
and it does not let it write "stopped in" to the process output.

To mitigate this, you have to use a native Windows API with
a small tool that detaches itself from its own console,
attaches to the non-existing console of the PID you want
to stop and sends it Ctrl+C.

See the source in [CtrlC.cpp](./CtrlC.cpp)

## Example

You can test the behaviour yourself if you:
 1. open a cmd console and enter `pause` command:
 2. use TaskManager to look up the PID of your cmd
 3. use this tool as CtrlC.exe PID_NUMBER
 4. you can see the cmd behaved as if you typed Ctrl+C in it and the `pause` program is terminated

## Compile the binary

λ vcvars64
λ cl CtrlC.cpp  /Oi /MT
