export dfi_stop_call_count=0x5dc2
export dfi_config=0

cd $SPECPATH/401.bzip2/src/
$LLVMPATH/bin/clang  -emit-llvm -c -o spec.bc -DSPEC_CPU -DNDEBUG   -O3                 spec.c
$LLVMPATH/bin/clang  -emit-llvm -c -o blocksort.bc -DSPEC_CPU -DNDEBUG   -O3                 blocksort.c
$LLVMPATH/bin/clang  -emit-llvm -c -o bzip2.bc -DSPEC_CPU -DNDEBUG   -O3                 bzip2.c
$LLVMPATH/bin/clang  -emit-llvm -c -o bzlib.bc -DSPEC_CPU -DNDEBUG   -O3                 bzlib.c
$LLVMPATH/bin/clang  -emit-llvm -c -o compress.bc -DSPEC_CPU -DNDEBUG   -O3                 compress.c
$LLVMPATH/bin/clang  -emit-llvm -c -o crctable.bc -DSPEC_CPU -DNDEBUG   -O3                 crctable.c
$LLVMPATH/bin/clang  -emit-llvm -c -o decompress.bc -DSPEC_CPU -DNDEBUG   -O3                 decompress.c
$LLVMPATH/bin/clang  -emit-llvm -c -o huffman.bc -DSPEC_CPU -DNDEBUG   -O3                 huffman.c
$LLVMPATH/bin/clang  -emit-llvm -c -o randtable.bc -DSPEC_CPU -DNDEBUG   -O3                 randtable.c
cp *.bc $WORKPATH
rm *.bc

cd $WORKPATH
$LLVMPATH/bin/llvm-link  spec.bc blocksort.bc bzip2.bc bzlib.bc compress.bc crctable.bc decompress.bc huffman.bc randtable.bc                     -o bzip2.bc


