export dfi_t_count=3830632
export dfi_config=0

cd $SPECPATH/429.mcf/src/
$LLVMPATH/bin/clang  -emit-llvm -c -o mcf.bc -DSPEC_CPU -DNDEBUG  -DWANT_STDC_PROTO -O3   mcf.c
$LLVMPATH/bin/clang  -emit-llvm -c -o mcfutil.bc -DSPEC_CPU -DNDEBUG  -DWANT_STDC_PROTO -O3   mcfutil.c
$LLVMPATH/bin/clang  -emit-llvm -c -o readmin.bc -DSPEC_CPU -DNDEBUG  -DWANT_STDC_PROTO -O3   readmin.c
$LLVMPATH/bin/clang  -emit-llvm -c -o implicit.bc -DSPEC_CPU -DNDEBUG  -DWANT_STDC_PROTO -O3   implicit.c
$LLVMPATH/bin/clang  -emit-llvm -c -o pstart.bc -DSPEC_CPU -DNDEBUG  -DWANT_STDC_PROTO -O3   pstart.c
$LLVMPATH/bin/clang  -emit-llvm -c -o output.bc -DSPEC_CPU -DNDEBUG  -DWANT_STDC_PROTO -O3   output.c
$LLVMPATH/bin/clang  -emit-llvm -c -o treeup.bc -DSPEC_CPU -DNDEBUG  -DWANT_STDC_PROTO -O3   treeup.c
$LLVMPATH/bin/clang  -emit-llvm -c -o pbla.bc -DSPEC_CPU -DNDEBUG  -DWANT_STDC_PROTO -O3   pbla.c
$LLVMPATH/bin/clang  -emit-llvm -c -o pflowup.bc -DSPEC_CPU -DNDEBUG  -DWANT_STDC_PROTO -O3   pflowup.c
$LLVMPATH/bin/clang  -emit-llvm -c -o psimplex.bc -DSPEC_CPU -DNDEBUG  -DWANT_STDC_PROTO -O3   psimplex.c
$LLVMPATH/bin/clang  -emit-llvm -c -o pbeampp.bc -DSPEC_CPU -DNDEBUG  -DWANT_STDC_PROTO -O3   pbeampp.c
cp *.bc $WORKPATH
rm *.bc

cd $WORKPATH
#$LLVMPATH/bin/clang++ -emit-llvm -c -I${ARMCXXLIBPATH} -I ${ARMCXXLIBPATH}/arm-linux-gnueabihf/ $HOST_OPT_LEVEL dfi_inst.cc -lstdc++ -o dfi_inst.bc -Wall 
$LLVMPATH/bin/llvm-link  mcf.bc mcfutil.bc readmin.bc implicit.bc pstart.bc output.bc treeup.bc pbla.bc pflowup.bc psimplex.bc pbeampp.bc -o mcf.bc

