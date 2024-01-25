#!/bin/bash

export mode="roccinstr"
export runmode="normal"

for var in "$@"
do
	if [ ${var} == "-oriexit" ]; then
		export mode="oriexit"
	elif [ ${var} == "-roccinstr" ]; then
		export mode="roccinstr"
	elif [ ${var} == "-soft" ]; then
		export mode="soft"
	elif [ ${var} == "-analysis" ]; then
		export runmode="analysis"
	elif [ ${var} == "-record" ]; then
		export runmode="record"
	fi
done

export COMPILECHAIN=xxx/riscv-gnu-build-linux/bin/riscv64-unknown-linux-gnu-
export RISCVCXXLIBPATH=xxx/riscv-gnu-build-linux/riscv64-unknown-linux-gnu/include/c++/7.2.0
export LLVMPATH=xxx/llvm-7.0.0/build-riscv-release
export LLVMPATH2=xxx/llvm-11.0.0/build-riscv-release
export SVFPATH=xxx/SVF/Release-build
export SPECPATH=xxx/speccpu/benchspec/CPU2006
export ROOTPATH=xxx/rvdfi
export PROGPATH=xxx/rvdfi/programs
export WORKPATH=xxx/rvdfi/run_${2}

export dfi_t_mode=43214321
export dfi_t_count=1000000000
export dfi_m_pc=0x410000
export dfi_config=0
export dfi_stop_call_count=0xffffffffffffffff

export content_do_exe="./handshake"

mkdir ${WORKPATH}
cd ${WORKPATH}

cp $PROGPATH/usr_rds_handshake ./usr_rds_handshake

$LLVMPATH/bin/clang++ -fno-stack-protector -z execstack -emit-llvm -c ${PROGPATH}/handshake.cc -o handshake.bc

echo "----------benchmark compile done----------"
#exit

$LLVMPATH/bin/clang++ -emit-llvm -c -I${RISCVCXXLIBPATH} -I ${RISCVCXXLIBPATH}/riscv64-unknown-linux-gnu/ -D DFI_MAX_CALLCOUNT=${dfi_stop_call_count} -D DFI_CONFIG=${dfi_config} -D DFI_TEST_MODE=${dfi_t_mode} -D DFI_MAX_PC=${dfi_m_pc} -D DFI_TEST_COUNT=${dfi_t_count} ${PROGPATH}/dfi_inst.cc -lstdc++ -o dfi_inst.bc 
$LLVMPATH/bin/llvm-link dfi_inst.bc handshake.bc -o handshake.bc

$LLVMPATH/bin/llvm-dis dfi_inst.bc

echo "----------link initialization done----------"

$SVFPATH/bin/wpa -ander -gen-def-set -print-def-set -print-prog-id handshake.bc > handshake.rd

$LLVMPATH/bin/llvm-dis handshake.wpa
cp handshake.wpa.ll handshake.ll
cp handshake.wpa handshake.bc

echo "----------static analysis done----------"

python ${PROGPATH}/instru.py handshake -init -sfunc dfi_inst

echo "----------added initialization function----------"

$LLVMPATH/bin/llvm-as handshake.ll -o handshake.bc

$LLVMPATH2/bin/llc -O0 -march=riscv64 -mcpu=generic-rv64 -mattr=+a,+c,+d,+f,+m handshake.bc -o handshake.s
sed -i 's/\.attribute.*//g' handshake.s
echo "changing asm instrumentation"
${COMPILECHAIN}g++ -I${RISCVCXXLIBPATH} -I ${RISCVCXXLIBPATH}/riscv64-unknown-linux-gnu/ -O0 handshake.s  -lpthread -lstdc++ -lm -o handshake
${COMPILECHAIN}objdump -d handshake > handshake.od

echo "----------program compile done----------"

python ${PROGPATH}/instru.py handshake -map -sfunc dfi_inst

echo "----------aux IR generation done----------"

#echo "Generating IR"
$LLVMPATH/bin/llvm-as handshake.ll.aux -o handshake.bc.aux

$LLVMPATH2/bin/llc -O0 -march=riscv64 -mcpu=generic-rv64 -mattr=+a,+c,+d,+f,+m handshake.bc.aux -o handshake.s
sed -i 's/\.attribute.*//g' handshake.s
echo "changing asm instrumentation"
${COMPILECHAIN}g++ -I${RISCVCXXLIBPATH} -I ${RISCVCXXLIBPATH}/riscv64-unknown-linux-gnu/ -O0 handshake.s -lpthread -lstdc++ -lm -o handshake.aux
${COMPILECHAIN}objdump -d handshake.aux > handshake.od.aux

echo "----------aux program compilation done----------"
#clean
rm *.bc

if [ ${mode} == "noinstr" ]; then

python ${PROGPATH}/instru.py handshake -rds -sfunc dfi_inst -usrrds usr_rds_handshake

echo "----------no instrumentation mode done----------"

elif [ ${mode} == "core2" ]; then

python ${PROGPATH}/instru.py handshake -core2 -sfunc dfi_inst -debugfunc -usrrds usr_rds_handshake

$LLVMPATH/bin/llvm-as handshake.ll -o handshake.bc

$LLVMPATH2/bin/llc -O0 -march=riscv64 -mcpu=generic-rv64 -mattr=+a,+c,+d,+f,+m handshake.bc -o handshake.s
sed -i 's/\.attribute.*//g' handshake.s
echo "changing asm instrumentation"
${COMPILECHAIN}g++ -I${RISCVCXXLIBPATH} -I ${RISCVCXXLIBPATH}/riscv64-unknown-linux-gnu/ -O0 handshake.s -lpthread -lstdc++ -lm -o handshake
${COMPILECHAIN}objdump -d handshake > handshake.od

echo "----------2 core mode done----------"

elif [ ${mode} == "roccinstr" ]; then

python ${PROGPATH}/instru.py handshake -roccinstr -sfunc dfi_inst -allfunc -usrrds usr_rds_handshake

$LLVMPATH/bin/llvm-as handshake.ll -o handshake.bc

$LLVMPATH2/bin/llc -O0 -march=riscv64 -mcpu=generic-rv64 -mattr=+a,+c,+d,+f,+m handshake.bc -o handshake.s
sed -i 's/\.attribute.*//g' handshake.s
echo "changing asm instrumentation"
python ${PROGPATH}/instru.py handshake -revasm -sfunc dfi_inst
${COMPILECHAIN}g++ -I${RISCVCXXLIBPATH} -I ${RISCVCXXLIBPATH}/riscv64-unknown-linux-gnu/ -O0 handshake.s -lpthread -lstdc++ -lm -o handshake
${COMPILECHAIN}objdump -d handshake > handshake.od

echo "----------roccinst mode done----------"

fi

