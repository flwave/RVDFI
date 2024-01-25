rm -rf ./builds/*
make -f Makefile.xcvu440-u500devkit verilog -j8
make -f Makefile.xcvu440-u500devkit mcs -j8
