#!/bin/bash

export target=${1}

export mode="oriexit"
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


mkdir ${WORKPATH}
cd ${WORKPATH}


if [ ${target} == "401" ]; then
export target="bzip2"
export content_do_exe="./bzip2 control"
export spec_file="${SPECPATH}/401.bzip2/data/test/input/control"
elif [ ${target} == "429" ]; then
export target="mcf"
export content_do_exe="./mcf ./inp.in"
export spec_file="${SPECPATH}/429.mcf/data/test/input/inp.in"
elif [ ${target} == "433" ]; then
export target="milc"
export content_do_exe="./run_433"
export spec_file="${PROGPATH}/run_433"
elif [ ${target} == "445" ]; then
export target="gobmk"
export content_do_exe="./gobmk ./connection1.sgf"
export spec_file="${SPECPATH}/445.gobmk/data/all/input/games/connection1.sgf"
elif [ ${target} == "456" ]; then
export target="hmmer"
export content_do_exe="./hmmer --fixed 0 --mean 500 --num 5000 --sd 350 --seed 0 ./retro.hmm"
export spec_file="${SPECPATH}/456.hmmer/data/ref/input/retro.hmm"
elif [ ${target} == "458" ]; then
export target="sjeng"
export content_do_exe="./sjeng ./test.txt"
export spec_file="${SPECPATH}/458.sjeng/data/test/input/test.txt "
elif [ ${target} == "462" ]; then
export target="libquantum"
export content_do_exe="./libquantum 100 33"
export spec_file=""
elif [ ${target} == "470" ]; then
export target="lbm"
export content_do_exe="./lbm 3000 reference.dat 0 0 ./100_100_130_cf_a.of"
export spec_file="${SPECPATH}/470.lbm/data/test/input/100_100_130_cf_a.of"
elif [ ${target} == "473" ]; then
export target="astar"
export content_do_exe="./astar ./rivers.cfg"
export spec_file="${SPECPATH}/473.astar/data/test/input/rivers.cfg ${SPECPATH}/473.astar/data/test/input/rivers.bin"
else
export target="test"
export content_do_exe="./test"
export spec_file=""
fi

for sfile in $spec_file; do
    cp -r ${sfile} ./
done

cp ${PROGPATH}/usr_rds_* ${WORKPATH}/

echo "target is" ${target}

export dfi_t_mode=43214321
export dfi_t_count=1000000
export dfi_m_pc=0x600000
export dfi_config=0
export dfi_stop_call_count=0xffffffffffffffff
#export dfi_stop_call_count=0x0

source ${PROGPATH}/compile_${1}.sh

if [ ${mode} == "ori" ]; then
	$LLVMPATH2/bin/llc -O0 -march=riscv64 -mcpu=generic-rv64 -mattr=+a,+c,+d,+f,+m ${target}.bc -o ${target}.s
	sed -i 's/\.attribute.*//g' ${target}.s
	${COMPILECHAIN}g++ -I${RISCVCXXLIBPATH} -I ${RISCVCXXLIBPATH}/riscv64-unknown-linux-gnu/  ${target}.s -O0 -L./ -lpthread -lstdc++ -lm -o ${target}
	${COMPILECHAIN}objdump -d ${target} > ${target}.od
	echo "----------original running, benchmark compile done----------"
	exit
fi

echo "----------benchmark compile done----------"

$LLVMPATH/bin/clang++ -emit-llvm -c -I${RISCVCXXLIBPATH} -I ${RISCVCXXLIBPATH}/riscv64-unknown-linux-gnu/ -D DFI_MAX_CALLCOUNT=${dfi_stop_call_count} -D DFI_CONFIG=${dfi_config} -D DFI_TEST_MODE=${dfi_t_mode} -D DFI_MAX_PC=${dfi_m_pc} -D DFI_TEST_COUNT=${dfi_t_count} ${PROGPATH}/dfi_inst.cc -lstdc++ -o dfi_inst.bc 
$LLVMPATH/bin/llvm-link ${target}.bc dfi_inst.bc  -o ${target}.bc

$LLVMPATH/bin/llvm-dis dfi_inst.bc

echo "----------link initialization done----------"

if [ ${runmode} != "record" ]; then

$SVFPATH/bin/wpa -ander -gen-def-set -print-def-set -print-prog-id ${target}.bc > ${target}.rd

echo "----------static analysis done----------"

else

echo "COPY RECORD"

cp ${PROGPATH}/record/${target}.rd ${target}.rd
cp ${PROGPATH}/record/${target}.wpa ${target}.wpa

echo "----------load static analysis done----------"

fi

if [ ${runmode} == "analysis" ]; then
cp ${target}.rd ${PROGPATH}/record/
cp ${target}.wpa ${PROGPATH}/record/
exit
fi

$LLVMPATH/bin/llvm-dis ${target}.wpa
cp ${target}.wpa.ll ${target}.ll
cp ${target}.wpa ${target}.bc

python ${PROGPATH}/instru.py ${target} -init -sfunc dfi_inst

echo "----------added initialization function----------"

$LLVMPATH/bin/llvm-as ${target}.ll -o ${target}.bc

$LLVMPATH2/bin/llc -O0 -march=riscv64 -mcpu=generic-rv64 -mattr=+a,+c,+d,+f,+m ${target}.bc -o ${target}.s
sed -i 's/\.attribute.*//g' ${target}.s
${COMPILECHAIN}g++ -I${RISCVCXXLIBPATH} -I ${RISCVCXXLIBPATH}/riscv64-unknown-linux-gnu/  ${target}.s -O0 -L./ -lpthread -lstdc++ -lm -o ${target}
${COMPILECHAIN}objdump -d ${target} > ${target}.od

echo "----------program compile done----------"

python ${PROGPATH}/instru.py ${target} -map -sfunc dfi_inst

echo "----------aux IR generation done----------"

#echo "Generating IR"
$LLVMPATH/bin/llvm-as ${target}.ll.aux -o ${target}.bc.aux

$LLVMPATH2/bin/llc -O0 -march=riscv64 -mcpu=generic-rv64 -mattr=+a,+c,+d,+f,+m ${target}.bc.aux -o ${target}.s
sed -i 's/\.attribute.*//g' ${target}.s
${COMPILECHAIN}g++ -I${RISCVCXXLIBPATH} -I ${RISCVCXXLIBPATH}/riscv64-unknown-linux-gnu/  ${target}.s -O0 -L./ -lpthread -lstdc++ -lm -o ${target}.aux
${COMPILECHAIN}objdump -d ${target}.aux > ${target}.od.aux

echo "----------aux program compilation done----------"

#clean
rm *.bc

if [ ${mode} == "noinstr" ]; then

python ${PROGPATH}/instru.py ${target} -rds -sfunc dfi_inst

echo "----------no instrumentation mode done----------"

elif [ ${mode} == "oriexit" ]; then

python ${PROGPATH}/instru.py ${target} -ori -sfunc dfi_inst

$LLVMPATH/bin/llvm-as ${target}.ll -o ${target}.bc

$LLVMPATH2/bin/llc -O0 -march=riscv64 -mcpu=generic-rv64 -mattr=+a,+c,+d,+f,+m ${target}.bc -o ${target}.s
sed -i 's/\.attribute.*//g' ${target}.s
echo "changing asm instrumentation"
${COMPILECHAIN}g++ -I${RISCVCXXLIBPATH} -I ${RISCVCXXLIBPATH}/riscv64-unknown-linux-gnu/  ${target}.s -O0 -L./ -lpthread -lstdc++ -lm -o ${target}
${COMPILECHAIN}objdump -d ${target} > ${target}.od

echo "----------ori mode done----------"

elif [ ${mode} == "soft" ]; then

python ${PROGPATH}/instru.py ${target} -soft -sfunc dfi_inst

$LLVMPATH/bin/llvm-as ${target}.ll -o ${target}.bc

$LLVMPATH2/bin/llc -O0 -march=riscv64 -mcpu=generic-rv64 -mattr=+a,+c,+d,+f,+m ${target}.bc -o ${target}.s
sed -i 's/\.attribute.*//g' ${target}.s
${COMPILECHAIN}g++ -I${RISCVCXXLIBPATH} -I ${RISCVCXXLIBPATH}/riscv64-unknown-linux-gnu/  ${target}.s -O0 -L./ -lpthread -lstdc++ -lm -o ${target}
${COMPILECHAIN}objdump -d ${target} > ${target}.od

echo "----------soft mode done----------"

elif [ ${mode} == "roccinstr" ]; then

python ${PROGPATH}/instru.py ${target} -roccinstr -sfunc dfi_inst -nofunc -debugrocc #-nofunc #-usrrds usr_rds_test #-stldroccdebug

$LLVMPATH/bin/llvm-as ${target}.ll -o ${target}.bc

$LLVMPATH2/bin/llc -O0 -march=riscv64 -mcpu=generic-rv64 -mattr=+a,+c,+d,+f,+m ${target}.bc -o ${target}.s
sed -i 's/\.attribute.*//g' ${target}.s
echo "changing asm instrumentation"
python ${PROGPATH}/instru.py ${target} -revasm -sfunc dfi_inst
${COMPILECHAIN}g++ -I${RISCVCXXLIBPATH} -I ${RISCVCXXLIBPATH}/riscv64-unknown-linux-gnu/  ${target}.s -O0 -L./ -lpthread -lstdc++ -lm -o ${target}
${COMPILECHAIN}objdump -d ${target} > ${target}.od

echo "----------roccinst mode done----------"

fi
