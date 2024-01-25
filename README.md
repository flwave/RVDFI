# RVDFI: A RISC-V Architecture with Security Enforcement by High Performance Complete Data-Flow Integrity

## 1. About This Repository

This repository contains the implementation of RVDFI, which is a RISC-V processor with data-flow integrity (DFI) enhancement. This project is based on the [Freedom RISC-V](https://github.com/sifive/freedom), which has been archived. More details can be found in our IEEE TC 2022 paper:

Lang Feng, Jiayi Huang, Luyi Li, Haochen Zhang, Zhongfeng Wang, [RVDFI: A RISC-V Architecture with Security Enforcement by High Performance Complete Data-Flow Integrity](https://ieeexplore.ieee.org/document/9645368), *IEEE Tranactions on Computers*, 71(10):2499—2512, October 2022.

Acknowledgment: We thank Xun Jiang for his contribution to setting up the basic RISC-V platform.

Contact: Lang Feng (fenglang3@mail.sysu.edu.cn) and Jiayi Huang (hjy@hkust-gz.edu.cn).

## 2. Repository Contents

This repository can be separated into the following contents:

1. Folder `programs`: This includes an instrumentation tool (with filename `instru.py`), and some scripts to perform static analysis and generate the instrumented executable binaries (The details are in later sections).
2. Zip file `clean_instru_tool.zip`: This also contains an instrumentation tool that is modified based on that in the `programs` folder. This tool is only used for pure software-DFI [1]. It supports x86 ISA and has separate and more detailed README file in its folder. This is cleaned for the researchers who only need an instrumentation tool.
3. Folder `freedom-u-sdk`: This contains the Linux system that is tested under the RISC-V processor. The Linux system has been modified to reserve some memory for DFI.
4. Folder `SVF`: This is the static analysis tool used in the paper. Refer to the instructions inside the folder to build the tool.
5. Other folders and files: These are based on the initial Freedom project. The support of *xcvu440-u500devkit* is added. 

## 3. Environment Setup

To use the repository for DFI enhancement, various setups are needed. The setup flow might be complicated as follows.

### 3.1. GCC Compiler

Multiple GCC compilers are used in this guide for different goals. We name them Toolchains A, B, and C.

#### 3.1.1. Toolchain A

This toolchain is needed when compiling to get Freedom RISC-V processor hardware. Freedom project provided RISC-V GCC toolchain inside `rocket-chip/riscv-tools`. Read the `README.md` under `rocket-chip`. Follow the guidance of “*Setting up the RISCV environment variable*” to build the toolchain A in a folder you want. 

In detail, according to the mentioned `README.md`, you need to set the toolchain build path by doing the following:

````bash
$ export RISCV=[paht of toolchain A]
$ cd rocket-chip/riscv-tools
$ ./build.sh
````

Note, toolchain A is built under “newlib” type, without any default libraries such as “stdio.h”.

This is tested successfully under Ubuntu 16.04 and GCC 5.4.0.

#### 3.1.2. Toolchain B

This toolchain is used to compile the binaries running on the RISC-V operating system. Run the following steps:

```bash
$ cd rocket-chip/riscv-tools/riscv-gnu-toolchain
$ ./configure --prefix=[path of toolchain B]
$ make linux # If it shows "make: Nothing to be done for linux", do "make clean", and do "make linux"
```

If  `gnu/stubs-lp64.h` cannot be found during any compilation, copy `stubs-lp64d.h` in toolchain B’s `sysroot` folder to `stubs-lp64.h`, and put it into the same folder with `stubs-lp64d.h`.

### 3.2. LLVM Compiler

We need LLVM compiler for static analysis, instrumentation, and generating RISC-V binaries that run on RVDFI processor. There are 3 LLVM that are simultaneously tested under our repository, with versions 11.0.0, 7.0.0, and 6.0.0. There are reasons for the repository to use 3 versions of LLVM:

- Version 6.0.0 is needed by static analysis tool, which is the modified SVF. It needs and only supports LLVM 6.0.0. 
- Version 7.0.0 is needed for instrumentation, because our instrumentation tool needs and only supports the human-readable intermediate representation (IR) code before LLVM version 8.0.1. Besides, Version 6.0.0 does not support RISC-V toolchain, so finally LLVM 7.0.0 is selected. 
- LLVM 11.0.0 is needed to compiling and linking the final binaries, because LLVM 7.0.0 can generate RISC-V IR, but does not support generating the final binaries well.

#### 3.2.1. Toochain LLVM11

This toolchain is for compiling the RISC-V binaries running on RVDFI processor (based on toolchain B):

- Download LLVM and Clang with version >= 8.0.1 (The repository uses 11.0.0 as an example).
- Build LLVM (Please refer to LLVM website for more information). Note that the cmake command needed is as follows (Replace each enter with a space).

```bash
$ cmake -G Unix \
>		Makefiles \
>   -DCMAKE_BUILD_TYPE=Release \
>   -DBUILD_SHARED_LIBS=True \
>    -DLLVM_USE_SPLIT_DWARF=True \
>    -DLLVM_OPTIMIZED_TABLEGEN=True \
>    -DLLVM_BUILD_TESTS=True \
>    -DDEFAULT_SYSROOT=[path of toolchain B]/sysroot/usr \
>    -DGCC_INSTALL_PREFIX=“[path of toolchain B]” \
>    -DLLVM_DEFAULT_TARGET_TRIPLE=“riscv64-unknown-linux-gnu” \
>    -DLLVM_EXPERIMENTAL_TARGETS_TO_BUILD=“RISCV” \
>    [LLVM source folder’s path] \
```

#### 3.2.2. Toolchain LLVM7

This toolchain is used for generate IR. We need LLVM 7.0.0 for this. The way of build Toolchain LLVM7 is the same as LLVM11.

#### 3.2.3. Toolchain LLVM6

This toolchain is used for the static analysis tool SVF. Currently, the modified SVF only supports LLVM with version 6.0.0. We need to build such version of LLVM and toolchain LLVM6. LLVM6 does not need to be compiled under RISC-V GCC toolchain.

### 3.3. Linux System

Inside `freedom-u-sdk`, also build the RISC-V GCC toolchain. This time, you can just build to the default path:

- Inside `freedom-u-sdk`, run `./rebuild.sh` to build the Linux system
- After the binary `./work/bbl.bin` is generated, use `Win32DiskImager` (or similar tools) to load this binary into an SD card.
- If you want to embed some files inside the Linux system’s filesystem, you can copy them into `./work/buildroot_initramfs_sysroot` (The file sizes should not be large)

## 4. How to build and modify the RVDFI RISC-V hardware design?

### 4.1. Build the RVDFI hardware design

Vivado 2018.3 is needed.

Before building RVDFI, you need to set the following environment variables. Note that the following is only used for build the RVDFI hardware design. If you need to compile RISC-V software, please open another terminal and do not run the following command in the new terminal.

```bash
$ export RISCV=[path of toolchain A]
$ export PATH=${PATH}:[path of the executable of Vivado, typically /opt/Xilinx/Vivado/2018.3/bin]
$ export PATH=${PATH}:[[path of toolchain A]/bin

$ ./rebuild.sh 
# You can look inside this file for more details. The bitstream of the corresponding FGPA is generated in the new folder.
```

### 4.2. Modify the RVDFI hardware design

The DFI enhancement is implemented inside RoCC of the RISC-V core, with most of the code inside `./rocket-chip/src/main/scala/tile/LazyRoCC.scala`.

You can look inside `build_all.sh` under the root folder for more details about the configuration. This file is an example file of how to generate a series of bitstreams with different configurations.

## 5. How to instrument the software to be executed in the RVDFI RISC-V processor?

The instrumentation is basically performed by the script `build.sh` inside `programs` folder. Inside `build.sh`, the script controls the compiler to generate IR, perform instrumentation, and compiler and link the instrumented IR.

There are some ways to use `build.sh`:

```bash
$ ./build.sh [casename] [workname] -oriexit/-roccinstr/-soft
```

This means to build the case with casename by original mode without DFI/RVDFI mode/software-DFI mode. Note that there needs a file named `compile_[casename].sh` to indicate how to compile this case. There are some examples inside the folder for reference.

```bash
$ ./build.sh [casename] [workname] -analysis
```

This is to only perform static analysis and record the results, as in some cases the static analysis is slow

```bash
$ ./build.sh [casename] [workname] -oriexit/-roccinstr/-soft -record
```

This is to skip the static analysis and just read the pre-analyzed results to speed up the compiling.

The `dfi_inst.cc` file:

- This is an important file that includes the initialization codes for DFI enhancement for each binary. Currently, there are many redundant lines, and some comments are outdated.
- Each different mode of `build.sh` can make different functions in `dfi_inst.cc` being instrumented inside the first line of `main` function of the target program.

If you want to instrument ripe and other security cases, you can run such as `build_ripe.sh`. 

Note that the static analysis tool SVF is not a perfect reaching definition analysis tool. Sometimes if we need more precise reaching definition analysis results, we need to modify it manually. This option is supported by the instrumentation tool. The user needs to put the reaching definition analysis modifications inside a file, and put the filename after `-usrrds` of the instrumentation tool’s command (see `build_ripe.sh` for more details, which is also shown in the figure below).

```bash
$ python ${PROGPATH}/instru.py ripe_attack_generator -roccinstr -usrrds usr_rds_ripe
```

**Note:** although there are already some `usr_rds_xxx` files provided, the numbers inside them are not updated. We’ll update them later if possible. If you want to update them, please refer to the `README` of `clean_instru_tool.zip`, where there are instructions of how to update.

For more details, including how to modify the instrumentation rules, especially the software-DFI implementation, please check the `README` inside `clean_instru_tool.zip`. Note that `clean_instru_tool.zip` is only for software-DFI, but similar approaches can also be used in RVDFI.

### MISC:

The reaching definition for RVDFI is saved in `dfi_rds_file` file.

There are some flags, such as `dfi_t_mode`, `dfi_t_count`, etc. They can be obtained by RVDFI processor to indicate some global configurations. For example, `dfi_t_count` means at most how many functions the processor enters for an instrumented program. RVDFI processor can count the entered function number, and kill the program once the function number meets the setting. This is to ensure the program end at the same point when testing.

In the script `build.sh` inside `programs` folder, the following part is not needed. It’s used for an abandoned test. However, we have not tested the stability after removing this part, so currently it is kept.

```bash
python ${PROGPATH}/instru.py ${target} -map -sfunc dfi_inst

echo "----------aux IR generation done----------"

#echo "Generating IR"
$LLVMPATH/bin/llvm-as ${target}.ll.aux -o ${target}.bc.aux

$LLVMPATH2/bin/llc -O0 -march=riscv64 -mcpu=generic-rv64 -mattr=+a,+c,+d,+f,+m ${target}.bc.aux -o ${target}.s
sed -i 's/\.attribute.*//g' ${target}.s
${COMPILECHAIN}g++ -I${RISCVCXXLIBPATH} -I ${RISCVCXXLIBPATH}/riscv64-unknown-linux-gnu/  ${target}.s -O0 -L./ -lpthread -lstdc++ -lm -o ${target}.aux
${COMPILECHAIN}objdump -d ${target}.aux > ${target}.od.aux

echo "----------aux program compilation done----------"
```

In the script `build.sh` inside `programs` folder, the following part is to generate the IR bitcode and compile to the assembly. Note that we modify the assembly, or the compile will fail.

```bash
$LLVMPATH/bin/llvm-as ${target}.ll -o ${target}.bc

$LLVMPATH2/bin/llc -O0 -march=riscv64 -mcpu=generic-rv64 -mattr=+a,+c,+d,+f,+m ${target}.bc -o ${target}.s
sed -i 's/\.attribute.*//g' ${target}.s
echo "changing asm instrumentation"
```

## 6. How to implement RVDFI on an FPGA board?

### 6.1. Software Preparation

Following [Section 5](#5.-how-to-instrument-the-software-to-be-executed-in-the-rvdfi-risc-v-processor?) to generated the instrumented binaries for DFI verification. If you want to run a binary without instrumentation, you can compiler it by toolchain B, and it will not be enhanced by DFI during executing.

Put the files such as binaries you want inside the Linux system’s root folders (following [Section 3.3](#3.3.-linux-system)), if the file sizes are not large. Then, compile Linux system and load it inside an SD card.

If the files you want to run are large, please use another SD card to store them, and mount this SD card after the Linux system completes the boot.

### 6.2. Hardware Implementation

Following to [Section 4](#4.-how-to-build-and-modify-the-rvdfi-risc-v-hardware-design?) to generate the bitstream, and download into the FPGA. After this, insert the SD card with Linux system into the FPGA board, and reboot the system. You should be able to see the system booting.



## 7. How to check if there are DFI violations in RVDFI?

After run the instrumented program on RVDFI processor, the violations can be checked at the first 32-bit integer of the reserved memory (physical address `0x6000000`). This integer indicates how many violations the processor meets till now. This can be checked manually by writing a program with `mmap` function to read such memory, or instrument the debug function inside `dfi_inst.cc`.

For more details about how to check software-DFI’s violation, please refer to the `README` of `clean_instru_tool.zip`.

## References:

\[1\] M. Castro, M. Costa, and T. Harris, “Securing software by enforcing data-flow integrity,” in Proc. Symp. Oper. Syst. Des. Implementation, 2006, pp. 147–160.
