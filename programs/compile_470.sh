export dfi_stop_call_count=0xc8
export dfi_config=0

cd $SPECPATH/470.lbm/src/
$LLVMPATH/bin/clang  -emit-llvm -c -o lbm.bc -DSPEC_CPU -DNDEBUG    -O3  -std=gnu89       -DSPEC_CPU_LP64         lbm.c
$LLVMPATH/bin/clang  -emit-llvm -c -o main.bc -DSPEC_CPU -DNDEBUG    -O3  -std=gnu89       -DSPEC_CPU_LP64         main.c
cp *.bc $WORKPATH
rm *.bc

cd $WORKPATH
$LLVMPATH/bin/llvm-link lbm.bc main.bc    -o lbm.bc
