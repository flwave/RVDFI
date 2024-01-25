riscv64-unknown-elf-gcc -mcmodel=medany -std=gnu99 -O0 -fno-common -fno-builtin-printf -Wall -c ${1}.s -o ${1}.o
riscv64-unknown-elf-gcc -T link.ld -static -nostdlib -nostartfiles -lgcc ${1}.o crt.o syscalls.o -o ${1}.riscv
