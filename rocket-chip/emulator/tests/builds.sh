riscv64-unknown-elf-gcc -mcmodel=medany -std=gnu99 -O0 -fno-common -fno-builtin-printf -Wall -D__ASSEMBLY__=1 -c crt.S -o crt.o
riscv64-unknown-elf-gcc -mcmodel=medany -std=gnu99 -O0 -fno-common -fno-builtin-printf -Wall -c syscalls.c -o syscalls.o
riscv64-unknown-elf-gcc -mcmodel=medany -std=gnu99 -O0 -fno-common -fno-builtin-printf -Wall -S ${1}.c -o ${1}.s
