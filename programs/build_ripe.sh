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


mkdir ${WORKPATH}
cd ${WORKPATH}

cp ${PROGPATH}/usr_rds_* ${WORKPATH}/


$LLVMPATH/bin/clang -fno-stack-protector -z execstack -emit-llvm -c ${PROGPATH}/ripe/ripe_attack_generator.c -o ./ripe_attack_generator.bc

echo "----------benchmark compile done----------"
#exit

$LLVMPATH/bin/clang++ -emit-llvm -c -I${RISCVCXXLIBPATH} -I ${RISCVCXXLIBPATH}/riscv64-unknown-linux-gnu/ -D DFI_MAX_CALLCOUNT=${dfi_stop_call_count} -D DFI_CONFIG=${dfi_config} -D DFI_TEST_MODE=${dfi_t_mode} -D DFI_MAX_PC=${dfi_m_pc} -D DFI_TEST_COUNT=${dfi_t_count} ${PROGPATH}/dfi_inst.cc -lstdc++ -o dfi_inst.bc 
$LLVMPATH/bin/llvm-link dfi_inst.bc ripe_attack_generator.bc -o ripe_attack_generator.bc

$LLVMPATH/bin/llvm-dis dfi_inst.bc

echo "----------link initialization done----------"

$SVFPATH/bin/wpa -ander -gen-def-set -print-def-set -print-prog-id ripe_attack_generator.bc > ripe_attack_generator.rd

$LLVMPATH/bin/llvm-dis ripe_attack_generator.wpa
cp ripe_attack_generator.wpa.ll ripe_attack_generator.ll
cp ripe_attack_generator.wpa ripe_attack_generator.bc

echo "----------static analysis done----------"

python ${PROGPATH}/instru.py ripe_attack_generator -init -sfunc dfi_inst

echo "----------added initialization function----------"

$LLVMPATH/bin/llvm-as ripe_attack_generator.ll -o ripe_attack_generator.bc

$LLVMPATH2/bin/llc -O0 -march=riscv64 -mcpu=generic-rv64 -mattr=+a,+c,+d,+f,+m ripe_attack_generator.bc -o ripe_attack_generator.s
sed -i 's/\.attribute.*//g' ripe_attack_generator.s
${COMPILECHAIN}g++ -I${RISCVCXXLIBPATH} -I ${RISCVCXXLIBPATH}/riscv64-unknown-linux-gnu/ ripe_attack_generator.s -O0 -L./ -lpthread -lstdc++ -lm -o ripe_attack_generator
${COMPILECHAIN}objdump -d ripe_attack_generator > ripe_attack_generator.od

echo "----------program compile done----------"

python ${PROGPATH}/instru.py ripe_attack_generator -map -sfunc dfi_inst

echo "----------aux IR generation done----------"

#echo "Generating IR"
$LLVMPATH/bin/llvm-as ripe_attack_generator.ll.aux -o ripe_attack_generator.bc.aux

$LLVMPATH2/bin/llc -O0 -march=riscv64 -mcpu=generic-rv64 -mattr=+a,+c,+d,+f,+m ripe_attack_generator.bc.aux -o ripe_attack_generator.s
sed -i 's/\.attribute.*//g' ripe_attack_generator.s
${COMPILECHAIN}g++ -I${RISCVCXXLIBPATH} -I ${RISCVCXXLIBPATH}/riscv64-unknown-linux-gnu/ ripe_attack_generator.s -O0 -L./ -lpthread -lstdc++ -lm -o ripe_attack_generator.aux
${COMPILECHAIN}objdump -d ripe_attack_generator.aux > ripe_attack_generator.od.aux

echo "----------aux program compilation done----------"
#clean
rm *.bc

if [ ${mode} == "noinstr" ]; then

python ${PROGPATH}/instru.py ripe_attack_generator -rds -usrrds usr_rds_ripe -sfunc dfi_inst

echo "----------no instrumentation mode done----------"

elif [ ${mode} == "roccinstr" ]; then

python ${PROGPATH}/instru.py ripe_attack_generator -roccinstr -usrrds usr_rds_ripe -sfunc dfi_inst -allfunc -timestampfunc perform_attack -debugrocc #-nofunc #-usrrds usr_rds_test #-stldroccdebug

$LLVMPATH/bin/llvm-as ripe_attack_generator.ll -o ripe_attack_generator.bc

$LLVMPATH2/bin/llc -O0 -march=riscv64 -mcpu=generic-rv64 -mattr=+a,+c,+d,+f,+m ripe_attack_generator.bc -o ripe_attack_generator.s
sed -i 's/\.attribute.*//g' ripe_attack_generator.s
echo "changing asm instrumentation"
python ${PROGPATH}/instru.py ripe_attack_generator -revasm -sfunc dfi_inst
${COMPILECHAIN}g++ -I${RISCVCXXLIBPATH} -I ${RISCVCXXLIBPATH}/riscv64-unknown-linux-gnu/  ripe_attack_generator.s -O0 -L./ -lpthread -lstdc++ -lm -o ripe_attack_generator
${COMPILECHAIN}objdump -d ripe_attack_generator > ripe_attack_generator.od

echo "----------roccinst mode done----------"

fi
