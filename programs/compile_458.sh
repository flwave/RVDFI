export dfi_t_count=17395357
export dfi_config=0

cd $SPECPATH/458.sjeng/src/
$LLVMPATH/bin/clang -emit-llvm -c -o attacks.bc -DSPEC_CPU -DNDEBUG   -O3 -ffast-math       -DSPEC_CPU_LP64         attacks.c
$LLVMPATH/bin/clang -emit-llvm -c -o book.bc -DSPEC_CPU -DNDEBUG   -O3 -ffast-math       -DSPEC_CPU_LP64         book.c
$LLVMPATH/bin/clang -emit-llvm -c -o crazy.bc -DSPEC_CPU -DNDEBUG   -O3 -ffast-math       -DSPEC_CPU_LP64         crazy.c
$LLVMPATH/bin/clang -emit-llvm -c -o draw.bc -DSPEC_CPU -DNDEBUG   -O3 -ffast-math       -DSPEC_CPU_LP64         draw.c
$LLVMPATH/bin/clang -emit-llvm -c -o ecache.bc -DSPEC_CPU -DNDEBUG   -O3 -ffast-math       -DSPEC_CPU_LP64         ecache.c
$LLVMPATH/bin/clang -emit-llvm -c -o epd.bc -DSPEC_CPU -DNDEBUG   -O3 -ffast-math       -DSPEC_CPU_LP64         epd.c
$LLVMPATH/bin/clang -emit-llvm -c -o eval.bc -DSPEC_CPU -DNDEBUG   -O3 -ffast-math       -DSPEC_CPU_LP64         eval.c
$LLVMPATH/bin/clang -emit-llvm -c -o leval.bc -DSPEC_CPU -DNDEBUG   -O3 -ffast-math       -DSPEC_CPU_LP64         leval.c
$LLVMPATH/bin/clang -emit-llvm -c -o moves.bc -DSPEC_CPU -DNDEBUG   -O3 -ffast-math       -DSPEC_CPU_LP64         moves.c
$LLVMPATH/bin/clang -emit-llvm -c -o neval.bc -DSPEC_CPU -DNDEBUG   -O3 -ffast-math       -DSPEC_CPU_LP64         neval.c
$LLVMPATH/bin/clang -emit-llvm -c -o partner.bc -DSPEC_CPU -DNDEBUG   -O3 -ffast-math       -DSPEC_CPU_LP64         partner.c
$LLVMPATH/bin/clang -emit-llvm -c -o proof.bc -DSPEC_CPU -DNDEBUG   -O3 -ffast-math       -DSPEC_CPU_LP64         proof.c
$LLVMPATH/bin/clang -emit-llvm -c -o rcfile.bc -DSPEC_CPU -DNDEBUG   -O3 -ffast-math       -DSPEC_CPU_LP64         rcfile.c
$LLVMPATH/bin/clang -emit-llvm -c -o search.bc -DSPEC_CPU -DNDEBUG   -O3 -ffast-math       -DSPEC_CPU_LP64         search.c
$LLVMPATH/bin/clang -emit-llvm -c -o see.bc -DSPEC_CPU -DNDEBUG   -O3 -ffast-math       -DSPEC_CPU_LP64         see.c
$LLVMPATH/bin/clang -emit-llvm -c -o seval.bc -DSPEC_CPU -DNDEBUG   -O3 -ffast-math       -DSPEC_CPU_LP64         seval.c
$LLVMPATH/bin/clang -emit-llvm -c -o sjeng.bc -DSPEC_CPU -DNDEBUG   -O3 -ffast-math       -DSPEC_CPU_LP64         sjeng.c
$LLVMPATH/bin/clang -emit-llvm -c -o ttable.bc -DSPEC_CPU -DNDEBUG   -O3 -ffast-math       -DSPEC_CPU_LP64         ttable.c
$LLVMPATH/bin/clang -emit-llvm -c -o utils.bc -DSPEC_CPU -DNDEBUG   -O3 -ffast-math       -DSPEC_CPU_LP64         utils.c
cp *.bc $WORKPATH
rm *.bc

cd $WORKPATH
$LLVMPATH/bin/llvm-link  attacks.bc book.bc crazy.bc draw.bc ecache.bc epd.bc eval.bc leval.bc moves.bc neval.bc partner.bc proof.bc rcfile.bc search.bc see.bc seval.bc sjeng.bc ttable.bc utils.bc     -o sjeng.bc

