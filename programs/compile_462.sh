export dfi_t_count=3209429
export dfi_config=0

cd $SPECPATH/462.libquantum/src/
$LLVMPATH/bin/clang -emit-llvm -c -o classic.bc -DSPEC_CPU -DNDEBUG   -O3        -DSPEC_CPU_LP64 -DSPEC_CPU_LINUX        classic.c
$LLVMPATH/bin/clang -emit-llvm -c -o complex.bc -DSPEC_CPU -DNDEBUG   -O3        -DSPEC_CPU_LP64 -DSPEC_CPU_LINUX        complex.c
$LLVMPATH/bin/clang -emit-llvm -c -o decoherence.bc -DSPEC_CPU -DNDEBUG   -O3        -DSPEC_CPU_LP64 -DSPEC_CPU_LINUX        decoherence.c
$LLVMPATH/bin/clang -emit-llvm -c -o expn.bc -DSPEC_CPU -DNDEBUG   -O3        -DSPEC_CPU_LP64 -DSPEC_CPU_LINUX        expn.c
$LLVMPATH/bin/clang -emit-llvm -c -o gates.bc -DSPEC_CPU -DNDEBUG   -O3        -DSPEC_CPU_LP64 -DSPEC_CPU_LINUX        gates.c
$LLVMPATH/bin/clang -emit-llvm -c -o matrix.bc -DSPEC_CPU -DNDEBUG   -O3        -DSPEC_CPU_LP64 -DSPEC_CPU_LINUX        matrix.c
$LLVMPATH/bin/clang -emit-llvm -c -o measure.bc -DSPEC_CPU -DNDEBUG   -O3        -DSPEC_CPU_LP64 -DSPEC_CPU_LINUX        measure.c
$LLVMPATH/bin/clang -emit-llvm -c -o oaddn.bc -DSPEC_CPU -DNDEBUG   -O3        -DSPEC_CPU_LP64 -DSPEC_CPU_LINUX        oaddn.c
$LLVMPATH/bin/clang -emit-llvm -c -o objcode.bc -DSPEC_CPU -DNDEBUG   -O3        -DSPEC_CPU_LP64 -DSPEC_CPU_LINUX        objcode.c
$LLVMPATH/bin/clang -emit-llvm -c -o omuln.bc -DSPEC_CPU -DNDEBUG   -O3        -DSPEC_CPU_LP64 -DSPEC_CPU_LINUX        omuln.c
$LLVMPATH/bin/clang -emit-llvm -c -o qec.bc -DSPEC_CPU -DNDEBUG   -O3        -DSPEC_CPU_LP64 -DSPEC_CPU_LINUX        qec.c
$LLVMPATH/bin/clang -emit-llvm -c -o qft.bc -DSPEC_CPU -DNDEBUG   -O3        -DSPEC_CPU_LP64 -DSPEC_CPU_LINUX        qft.c
$LLVMPATH/bin/clang -emit-llvm -c -o qureg.bc -DSPEC_CPU -DNDEBUG   -O3        -DSPEC_CPU_LP64 -DSPEC_CPU_LINUX        qureg.c
$LLVMPATH/bin/clang -emit-llvm -c -o shor.bc -DSPEC_CPU -DNDEBUG   -O3        -DSPEC_CPU_LP64 -DSPEC_CPU_LINUX        shor.c
$LLVMPATH/bin/clang -emit-llvm -c -o version.bc -DSPEC_CPU -DNDEBUG   -O3        -DSPEC_CPU_LP64 -DSPEC_CPU_LINUX        version.c
$LLVMPATH/bin/clang -emit-llvm -c -o specrand.bc -DSPEC_CPU -DNDEBUG   -O3        -DSPEC_CPU_LP64 -DSPEC_CPU_LINUX        specrand.c
cp *.bc $WORKPATH
rm *.bc

cd $WORKPATH
$LLVMPATH/bin/llvm-link    classic.bc complex.bc decoherence.bc expn.bc gates.bc matrix.bc measure.bc oaddn.bc objcode.bc omuln.bc qec.bc qft.bc qureg.bc shor.bc version.bc specrand.bc    -o libquantum.bc

