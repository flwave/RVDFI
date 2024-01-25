for fifostldsize in 1024 512 256 64 1
do

if [ ${fifostldsize} == 1024 ]; then
export fifofuncsize=128
elif [ ${fifostldsize} == 512 ]; then
export fifofuncsize=64
elif [ ${fifostldsize} == 256 ]; then
export fifofuncsize=32
elif [ ${fifostldsize} == 64 ]; then
export fifofuncsize=8
elif [ ${fifostldsize} == 1 ]; then
export fifofuncsize=1
fi

sed -i 's/def fifostldsize: Int = .*/def fifostldsize: Int = '$fifostldsize'/g' ./rocket-chip/src/main/scala/tile/LazyRoCC.scala
sed -i 's/def fifofuncsize: Int = .*/def fifofuncsize: Int = '$fifofuncsize'/g' ./rocket-chip/src/main/scala/tile/LazyRoCC.scala
sed -i 's/def ldopbufsize: Int = .*/def ldopbufsize: Int = 64/g' ./rocket-chip/src/main/scala/tile/LazyRoCC.scala
sed -i 's/def rdscacheaddrwidth: Int = .*/def rdscacheaddrwidth: Int = 12/g' ./rocket-chip/src/main/scala/tile/LazyRoCC.scala
sed -i 's/def rdsmapcacheaddrwidth: Int = .*/def rdsmapcacheaddrwidth: Int = 12/g' ./rocket-chip/src/main/scala/tile/LazyRoCC.scala
sed -i 's/def rdtcacheaddrwidth: Int = .*/def rdtcacheaddrwidth: Int = 14-2/g' ./rocket-chip/src/main/scala/tile/LazyRoCC.scala
./rebuild.sh
cp ./builds/xcvu440-u500devkit/obj/XCVU440Shell.bit ../XCVU440Shell_fsl${fifostldsize}_ff${fifofuncsize}_ldopt64_rdsc12_rdsmc12_rdtc12.bit
done
