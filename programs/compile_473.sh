export dfi_stop_call_count=0x3fb96107
export dfi_config=0

cd $SPECPATH/473.astar/src/
$LLVMPATH/bin/clang++ -emit-llvm -c -std=c++98 -o CreateWay_.bc -DSPEC_CPU -DNDEBUG -DSPEC_CPU_LITTLE_ENDIAN  -O3 -fno-math-errno   -DSPEC_CPU_LP64       CreateWay_.cpp
$LLVMPATH/bin/clang++ -emit-llvm -c -std=c++98 -o Places_.bc -DSPEC_CPU -DNDEBUG -DSPEC_CPU_LITTLE_ENDIAN  -O3 -fno-math-errno   -DSPEC_CPU_LP64       Places_.cpp
$LLVMPATH/bin/clang++ -emit-llvm -c -std=c++98 -o RegBounds_.bc -DSPEC_CPU -DNDEBUG -DSPEC_CPU_LITTLE_ENDIAN  -O3 -fno-math-errno   -DSPEC_CPU_LP64       RegBounds_.cpp
$LLVMPATH/bin/clang++ -emit-llvm -c -std=c++98 -o RegMng_.bc -DSPEC_CPU -DNDEBUG -DSPEC_CPU_LITTLE_ENDIAN  -O3 -fno-math-errno   -DSPEC_CPU_LP64       RegMng_.cpp
$LLVMPATH/bin/clang++ -emit-llvm -c -std=c++98 -o Way2_.bc -DSPEC_CPU -DNDEBUG -DSPEC_CPU_LITTLE_ENDIAN  -O3 -fno-math-errno   -DSPEC_CPU_LP64       Way2_.cpp
$LLVMPATH/bin/clang++ -emit-llvm -c -std=c++98 -o WayInit_.bc -DSPEC_CPU -DNDEBUG -DSPEC_CPU_LITTLE_ENDIAN  -O3 -fno-math-errno   -DSPEC_CPU_LP64       WayInit_.cpp
$LLVMPATH/bin/clang++ -emit-llvm -c -std=c++98 -o Library.bc -DSPEC_CPU -DNDEBUG -DSPEC_CPU_LITTLE_ENDIAN  -O3 -fno-math-errno   -DSPEC_CPU_LP64       Library.cpp
$LLVMPATH/bin/clang++ -emit-llvm -c -std=c++98 -o Random.bc -DSPEC_CPU -DNDEBUG -DSPEC_CPU_LITTLE_ENDIAN  -O3 -fno-math-errno   -DSPEC_CPU_LP64       Random.cpp
$LLVMPATH/bin/clang++ -emit-llvm -c -std=c++98 -o Region_.bc -DSPEC_CPU -DNDEBUG -DSPEC_CPU_LITTLE_ENDIAN  -O3 -fno-math-errno   -DSPEC_CPU_LP64       Region_.cpp
$LLVMPATH/bin/clang++ -emit-llvm -c -std=c++98 -o RegWay_.bc -DSPEC_CPU -DNDEBUG -DSPEC_CPU_LITTLE_ENDIAN  -O3 -fno-math-errno   -DSPEC_CPU_LP64       RegWay_.cpp
$LLVMPATH/bin/clang++ -emit-llvm -c -std=c++98 -o Way_.bc -DSPEC_CPU -DNDEBUG -DSPEC_CPU_LITTLE_ENDIAN  -O3 -fno-math-errno   -DSPEC_CPU_LP64       Way_.cpp
cp *.bc $WORKPATH
rm *.bc

cd $WORKPATH
$LLVMPATH/bin/llvm-link   CreateWay_.bc Places_.bc RegBounds_.bc RegMng_.bc Way2_.bc WayInit_.bc Library.bc Random.bc Region_.bc RegWay_.bc Way_.bc      -o astar.bc
