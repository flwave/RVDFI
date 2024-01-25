export dfi_t_count=16742136
export dfi_config=0

cd $SPECPATH/445.gobmk/src/
$LLVMPATH/bin/clang -emit-llvm -c -o sgf/sgf_utils.bc -DSPEC_CPU -DNDEBUG -DHAVE_CONFIG_H -I. -I.. -I../include -I./include  -O3 -ffast-math       -DSPEC_CPU_LP64         sgf/sgf_utils.c
$LLVMPATH/bin/clang -emit-llvm -c -o sgf/sgftree.bc -DSPEC_CPU -DNDEBUG -DHAVE_CONFIG_H -I. -I.. -I../include -I./include  -O3 -ffast-math       -DSPEC_CPU_LP64         sgf/sgftree.c
$LLVMPATH/bin/clang -emit-llvm -c -o sgf/sgfnode.bc -DSPEC_CPU -DNDEBUG -DHAVE_CONFIG_H -I. -I.. -I../include -I./include  -O3 -ffast-math       -DSPEC_CPU_LP64         sgf/sgfnode.c
$LLVMPATH/bin/clang -emit-llvm -c -o engine/aftermath.bc -DSPEC_CPU -DNDEBUG -DHAVE_CONFIG_H -I. -I.. -I../include -I./include  -O3 -ffast-math       -DSPEC_CPU_LP64         engine/aftermath.c
$LLVMPATH/bin/clang -emit-llvm -c -o engine/board.bc -DSPEC_CPU -DNDEBUG -DHAVE_CONFIG_H -I. -I.. -I../include -I./include  -O3 -ffast-math       -DSPEC_CPU_LP64         engine/board.c
$LLVMPATH/bin/clang -emit-llvm -c -o engine/cache.bc -DSPEC_CPU -DNDEBUG -DHAVE_CONFIG_H -I. -I.. -I../include -I./include  -O3 -ffast-math       -DSPEC_CPU_LP64         engine/cache.c
$LLVMPATH/bin/clang -emit-llvm -c -o engine/combination.bc -DSPEC_CPU -DNDEBUG -DHAVE_CONFIG_H -I. -I.. -I../include -I./include  -O3 -ffast-math       -DSPEC_CPU_LP64         engine/combination.c
$LLVMPATH/bin/clang -emit-llvm -c -o engine/dragon.bc -DSPEC_CPU -DNDEBUG -DHAVE_CONFIG_H -I. -I.. -I../include -I./include  -O3 -ffast-math       -DSPEC_CPU_LP64         engine/dragon.c
$LLVMPATH/bin/clang -emit-llvm -c -o engine/filllib.bc -DSPEC_CPU -DNDEBUG -DHAVE_CONFIG_H -I. -I.. -I../include -I./include  -O3 -ffast-math       -DSPEC_CPU_LP64         engine/filllib.c
$LLVMPATH/bin/clang -emit-llvm -c -o engine/fuseki.bc -DSPEC_CPU -DNDEBUG -DHAVE_CONFIG_H -I. -I.. -I../include -I./include  -O3 -ffast-math       -DSPEC_CPU_LP64         engine/fuseki.c
$LLVMPATH/bin/clang -emit-llvm -c -o engine/genmove.bc -DSPEC_CPU -DNDEBUG -DHAVE_CONFIG_H -I. -I.. -I../include -I./include  -O3 -ffast-math       -DSPEC_CPU_LP64         engine/genmove.c
$LLVMPATH/bin/clang -emit-llvm -c -o engine/hash.bc -DSPEC_CPU -DNDEBUG -DHAVE_CONFIG_H -I. -I.. -I../include -I./include  -O3 -ffast-math       -DSPEC_CPU_LP64         engine/hash.c
$LLVMPATH/bin/clang -emit-llvm -c -o engine/influence.bc -DSPEC_CPU -DNDEBUG -DHAVE_CONFIG_H -I. -I.. -I../include -I./include  -O3 -ffast-math       -DSPEC_CPU_LP64         engine/influence.c
$LLVMPATH/bin/clang -emit-llvm -c -o engine/interface.bc -DSPEC_CPU -DNDEBUG -DHAVE_CONFIG_H -I. -I.. -I../include -I./include  -O3 -ffast-math       -DSPEC_CPU_LP64         engine/interface.c
$LLVMPATH/bin/clang -emit-llvm -c -o engine/matchpat.bc -DSPEC_CPU -DNDEBUG -DHAVE_CONFIG_H -I. -I.. -I../include -I./include  -O3 -ffast-math       -DSPEC_CPU_LP64         engine/matchpat.c
$LLVMPATH/bin/clang -emit-llvm -c -o engine/move_reasons.bc -DSPEC_CPU -DNDEBUG -DHAVE_CONFIG_H -I. -I.. -I../include -I./include  -O3 -ffast-math       -DSPEC_CPU_LP64         engine/move_reasons.c
$LLVMPATH/bin/clang -emit-llvm -c -o engine/movelist.bc -DSPEC_CPU -DNDEBUG -DHAVE_CONFIG_H -I. -I.. -I../include -I./include  -O3 -ffast-math       -DSPEC_CPU_LP64         engine/movelist.c
$LLVMPATH/bin/clang -emit-llvm -c -o engine/optics.bc -DSPEC_CPU -DNDEBUG -DHAVE_CONFIG_H -I. -I.. -I../include -I./include  -O3 -ffast-math       -DSPEC_CPU_LP64         engine/optics.c
$LLVMPATH/bin/clang -emit-llvm -c -o engine/owl.bc -DSPEC_CPU -DNDEBUG -DHAVE_CONFIG_H -I. -I.. -I../include -I./include  -O3 -ffast-math       -DSPEC_CPU_LP64         engine/owl.c
$LLVMPATH/bin/clang -emit-llvm -c -o engine/printutils.bc -DSPEC_CPU -DNDEBUG -DHAVE_CONFIG_H -I. -I.. -I../include -I./include  -O3 -ffast-math       -DSPEC_CPU_LP64         engine/printutils.c
$LLVMPATH/bin/clang -emit-llvm -c -o engine/readconnect.bc -DSPEC_CPU -DNDEBUG -DHAVE_CONFIG_H -I. -I.. -I../include -I./include  -O3 -ffast-math       -DSPEC_CPU_LP64         engine/readconnect.c
$LLVMPATH/bin/clang -emit-llvm -c -o engine/reading.bc -DSPEC_CPU -DNDEBUG -DHAVE_CONFIG_H -I. -I.. -I../include -I./include  -O3 -ffast-math       -DSPEC_CPU_LP64         engine/reading.c
$LLVMPATH/bin/clang -emit-llvm -c -o engine/score.bc -DSPEC_CPU -DNDEBUG -DHAVE_CONFIG_H -I. -I.. -I../include -I./include  -O3 -ffast-math       -DSPEC_CPU_LP64         engine/score.c
$LLVMPATH/bin/clang -emit-llvm -c -o engine/semeai.bc -DSPEC_CPU -DNDEBUG -DHAVE_CONFIG_H -I. -I.. -I../include -I./include  -O3 -ffast-math       -DSPEC_CPU_LP64         engine/semeai.c
$LLVMPATH/bin/clang -emit-llvm -c -o engine/sgfdecide.bc -DSPEC_CPU -DNDEBUG -DHAVE_CONFIG_H -I. -I.. -I../include -I./include  -O3 -ffast-math       -DSPEC_CPU_LP64         engine/sgfdecide.c
$LLVMPATH/bin/clang -emit-llvm -c -o engine/sgffile.bc -DSPEC_CPU -DNDEBUG -DHAVE_CONFIG_H -I. -I.. -I../include -I./include  -O3 -ffast-math       -DSPEC_CPU_LP64         engine/sgffile.c
$LLVMPATH/bin/clang -emit-llvm -c -o engine/shapes.bc -DSPEC_CPU -DNDEBUG -DHAVE_CONFIG_H -I. -I.. -I../include -I./include  -O3 -ffast-math       -DSPEC_CPU_LP64         engine/shapes.c
$LLVMPATH/bin/clang -emit-llvm -c -o engine/showbord.bc -DSPEC_CPU -DNDEBUG -DHAVE_CONFIG_H -I. -I.. -I../include -I./include  -O3 -ffast-math       -DSPEC_CPU_LP64         engine/showbord.c
$LLVMPATH/bin/clang -emit-llvm -c -o engine/utils.bc -DSPEC_CPU -DNDEBUG -DHAVE_CONFIG_H -I. -I.. -I../include -I./include  -O3 -ffast-math       -DSPEC_CPU_LP64         engine/utils.c
$LLVMPATH/bin/clang -emit-llvm -c -o engine/value_moves.bc -DSPEC_CPU -DNDEBUG -DHAVE_CONFIG_H -I. -I.. -I../include -I./include  -O3 -ffast-math       -DSPEC_CPU_LP64         engine/value_moves.c
$LLVMPATH/bin/clang -emit-llvm -c -o engine/worm.bc -DSPEC_CPU -DNDEBUG -DHAVE_CONFIG_H -I. -I.. -I../include -I./include  -O3 -ffast-math       -DSPEC_CPU_LP64         engine/worm.c
$LLVMPATH/bin/clang -emit-llvm -c -o engine/globals.bc -DSPEC_CPU -DNDEBUG -DHAVE_CONFIG_H -I. -I.. -I../include -I./include  -O3 -ffast-math       -DSPEC_CPU_LP64         engine/globals.c
$LLVMPATH/bin/clang -emit-llvm -c -o engine/persistent.bc -DSPEC_CPU -DNDEBUG -DHAVE_CONFIG_H -I. -I.. -I../include -I./include  -O3 -ffast-math       -DSPEC_CPU_LP64         engine/persistent.c
$LLVMPATH/bin/clang -emit-llvm -c -o engine/handicap.bc -DSPEC_CPU -DNDEBUG -DHAVE_CONFIG_H -I. -I.. -I../include -I./include  -O3 -ffast-math       -DSPEC_CPU_LP64         engine/handicap.c
$LLVMPATH/bin/clang -emit-llvm -c -o engine/surround.bc -DSPEC_CPU -DNDEBUG -DHAVE_CONFIG_H -I. -I.. -I../include -I./include  -O3 -ffast-math       -DSPEC_CPU_LP64         engine/surround.c
$LLVMPATH/bin/clang -emit-llvm -c -o interface/gtp.bc -DSPEC_CPU -DNDEBUG -DHAVE_CONFIG_H -I. -I.. -I../include -I./include  -O3 -ffast-math       -DSPEC_CPU_LP64         interface/gtp.c
$LLVMPATH/bin/clang -emit-llvm -c -o interface/main.bc -DSPEC_CPU -DNDEBUG -DHAVE_CONFIG_H -I. -I.. -I../include -I./include  -O3 -ffast-math       -DSPEC_CPU_LP64         interface/main.c
$LLVMPATH/bin/clang -emit-llvm -c -o interface/play_ascii.bc -DSPEC_CPU -DNDEBUG -DHAVE_CONFIG_H -I. -I.. -I../include -I./include  -O3 -ffast-math       -DSPEC_CPU_LP64         interface/play_ascii.c
$LLVMPATH/bin/clang -emit-llvm -c -o interface/play_gtp.bc -DSPEC_CPU -DNDEBUG -DHAVE_CONFIG_H -I. -I.. -I../include -I./include  -O3 -ffast-math       -DSPEC_CPU_LP64         interface/play_gtp.c
$LLVMPATH/bin/clang -emit-llvm -c -o interface/play_solo.bc -DSPEC_CPU -DNDEBUG -DHAVE_CONFIG_H -I. -I.. -I../include -I./include  -O3 -ffast-math       -DSPEC_CPU_LP64         interface/play_solo.c
$LLVMPATH/bin/clang -emit-llvm -c -o interface/play_test.bc -DSPEC_CPU -DNDEBUG -DHAVE_CONFIG_H -I. -I.. -I../include -I./include  -O3 -ffast-math       -DSPEC_CPU_LP64         interface/play_test.c
$LLVMPATH/bin/clang -emit-llvm -c -o patterns/connections.bc -DSPEC_CPU -DNDEBUG -DHAVE_CONFIG_H -I. -I.. -I../include -I./include  -O3 -ffast-math       -DSPEC_CPU_LP64         patterns/connections.c
$LLVMPATH/bin/clang -emit-llvm -c -o patterns/dfa.bc -DSPEC_CPU -DNDEBUG -DHAVE_CONFIG_H -I. -I.. -I../include -I./include  -O3 -ffast-math       -DSPEC_CPU_LP64         patterns/dfa.c
$LLVMPATH/bin/clang -emit-llvm -c -o patterns/helpers.bc -DSPEC_CPU -DNDEBUG -DHAVE_CONFIG_H -I. -I.. -I../include -I./include  -O3 -ffast-math       -DSPEC_CPU_LP64         patterns/helpers.c
$LLVMPATH/bin/clang -emit-llvm -c -o patterns/transform.bc -DSPEC_CPU -DNDEBUG -DHAVE_CONFIG_H -I. -I.. -I../include -I./include  -O3 -ffast-math       -DSPEC_CPU_LP64         patterns/transform.c
$LLVMPATH/bin/clang -emit-llvm -c -o patterns/owl_attackpat.bc -DSPEC_CPU -DNDEBUG -DHAVE_CONFIG_H -I. -I.. -I../include -I./include  -O3 -ffast-math       -DSPEC_CPU_LP64         patterns/owl_attackpat.c
$LLVMPATH/bin/clang -emit-llvm -c -o patterns/conn.bc -DSPEC_CPU -DNDEBUG -DHAVE_CONFIG_H -I. -I.. -I../include -I./include  -O3 -ffast-math       -DSPEC_CPU_LP64         patterns/conn.c
$LLVMPATH/bin/clang -emit-llvm -c -o patterns/patterns.bc -DSPEC_CPU -DNDEBUG -DHAVE_CONFIG_H -I. -I.. -I../include -I./include  -O3 -ffast-math       -DSPEC_CPU_LP64         patterns/patterns.c
$LLVMPATH/bin/clang -emit-llvm -c -o patterns/apatterns.bc -DSPEC_CPU -DNDEBUG -DHAVE_CONFIG_H -I. -I.. -I../include -I./include  -O3 -ffast-math       -DSPEC_CPU_LP64         patterns/apatterns.c
$LLVMPATH/bin/clang -emit-llvm -c -o patterns/dpatterns.bc -DSPEC_CPU -DNDEBUG -DHAVE_CONFIG_H -I. -I.. -I../include -I./include  -O3 -ffast-math       -DSPEC_CPU_LP64         patterns/dpatterns.c
$LLVMPATH/bin/clang -emit-llvm -c -o patterns/owl_vital_apat.bc -DSPEC_CPU -DNDEBUG -DHAVE_CONFIG_H -I. -I.. -I../include -I./include  -O3 -ffast-math       -DSPEC_CPU_LP64         patterns/owl_vital_apat.c
$LLVMPATH/bin/clang -emit-llvm -c -o patterns/eyes.bc -DSPEC_CPU -DNDEBUG -DHAVE_CONFIG_H -I. -I.. -I../include -I./include  -O3 -ffast-math       -DSPEC_CPU_LP64         patterns/eyes.c
$LLVMPATH/bin/clang -emit-llvm -c -o patterns/influence.bc -DSPEC_CPU -DNDEBUG -DHAVE_CONFIG_H -I. -I.. -I../include -I./include  -O3 -ffast-math       -DSPEC_CPU_LP64         patterns/influence.c
$LLVMPATH/bin/clang -emit-llvm -c -o patterns/barriers.bc -DSPEC_CPU -DNDEBUG -DHAVE_CONFIG_H -I. -I.. -I../include -I./include  -O3 -ffast-math       -DSPEC_CPU_LP64         patterns/barriers.c
$LLVMPATH/bin/clang -emit-llvm -c -o patterns/endgame.bc -DSPEC_CPU -DNDEBUG -DHAVE_CONFIG_H -I. -I.. -I../include -I./include  -O3 -ffast-math       -DSPEC_CPU_LP64         patterns/endgame.c
$LLVMPATH/bin/clang -emit-llvm -c -o patterns/aa_attackpat.bc -DSPEC_CPU -DNDEBUG -DHAVE_CONFIG_H -I. -I.. -I../include -I./include  -O3 -ffast-math       -DSPEC_CPU_LP64         patterns/aa_attackpat.c
$LLVMPATH/bin/clang -emit-llvm -c -o patterns/owl_defendpat.bc -DSPEC_CPU -DNDEBUG -DHAVE_CONFIG_H -I. -I.. -I../include -I./include  -O3 -ffast-math       -DSPEC_CPU_LP64         patterns/owl_defendpat.c
$LLVMPATH/bin/clang -emit-llvm -c -o patterns/fusekipat.bc -DSPEC_CPU -DNDEBUG -DHAVE_CONFIG_H -I. -I.. -I../include -I./include  -O3 -ffast-math       -DSPEC_CPU_LP64         patterns/fusekipat.c
$LLVMPATH/bin/clang -emit-llvm -c -o patterns/fuseki9.bc -DSPEC_CPU -DNDEBUG -DHAVE_CONFIG_H -I. -I.. -I../include -I./include  -O3 -ffast-math       -DSPEC_CPU_LP64         patterns/fuseki9.c
$LLVMPATH/bin/clang -emit-llvm -c -o patterns/fuseki13.bc -DSPEC_CPU -DNDEBUG -DHAVE_CONFIG_H -I. -I.. -I../include -I./include  -O3 -ffast-math       -DSPEC_CPU_LP64         patterns/fuseki13.c
$LLVMPATH/bin/clang -emit-llvm -c -o patterns/fuseki19.bc -DSPEC_CPU -DNDEBUG -DHAVE_CONFIG_H -I. -I.. -I../include -I./include  -O3 -ffast-math       -DSPEC_CPU_LP64         patterns/fuseki19.c
$LLVMPATH/bin/clang -emit-llvm -c -o patterns/josekidb.bc -DSPEC_CPU -DNDEBUG -DHAVE_CONFIG_H -I. -I.. -I../include -I./include  -O3 -ffast-math       -DSPEC_CPU_LP64         patterns/josekidb.c
$LLVMPATH/bin/clang -emit-llvm -c -o patterns/handipat.bc -DSPEC_CPU -DNDEBUG -DHAVE_CONFIG_H -I. -I.. -I../include -I./include  -O3 -ffast-math       -DSPEC_CPU_LP64         patterns/handipat.c
$LLVMPATH/bin/clang -emit-llvm -c -o utils/getopt.bc -DSPEC_CPU -DNDEBUG -DHAVE_CONFIG_H -I. -I.. -I../include -I./include  -O3 -ffast-math       -DSPEC_CPU_LP64         utils/getopt.c
$LLVMPATH/bin/clang -emit-llvm -c -o utils/getopt1.bc -DSPEC_CPU -DNDEBUG -DHAVE_CONFIG_H -I. -I.. -I../include -I./include  -O3 -ffast-math       -DSPEC_CPU_LP64         utils/getopt1.c
$LLVMPATH/bin/clang -emit-llvm -c -o utils/gg_utils.bc -DSPEC_CPU -DNDEBUG -DHAVE_CONFIG_H -I. -I.. -I../include -I./include  -O3 -ffast-math       -DSPEC_CPU_LP64         utils/gg_utils.c
$LLVMPATH/bin/clang -emit-llvm -c -o utils/random.bc -DSPEC_CPU -DNDEBUG -DHAVE_CONFIG_H -I. -I.. -I../include -I./include  -O3 -ffast-math       -DSPEC_CPU_LP64         utils/random.c
#cp -r *.bc $SCENARIO_APP_PWD
mkdir $WORKPATH/sgf
mkdir $WORKPATH/engine
mkdir $WORKPATH/interface
mkdir $WORKPATH/patterns
mkdir $WORKPATH/utils
find . -type f | grep -i .bc$ | xargs -i cp {} $WORKPATH/{}
#rm -r *.bc

cd $WORKPATH
$LLVMPATH/bin/llvm-link   sgf/sgf_utils.bc sgf/sgftree.bc sgf/sgfnode.bc engine/aftermath.bc engine/board.bc engine/cache.bc engine/combination.bc engine/dragon.bc engine/filllib.bc engine/fuseki.bc engine/genmove.bc engine/hash.bc engine/influence.bc engine/interface.bc engine/matchpat.bc engine/move_reasons.bc engine/movelist.bc engine/optics.bc engine/owl.bc engine/printutils.bc engine/readconnect.bc engine/reading.bc engine/score.bc engine/semeai.bc engine/sgfdecide.bc engine/sgffile.bc engine/shapes.bc engine/showbord.bc engine/utils.bc engine/value_moves.bc engine/worm.bc engine/globals.bc engine/persistent.bc engine/handicap.bc engine/surround.bc interface/gtp.bc interface/main.bc interface/play_ascii.bc interface/play_gtp.bc interface/play_solo.bc interface/play_test.bc patterns/connections.bc patterns/dfa.bc patterns/helpers.bc patterns/transform.bc patterns/owl_attackpat.bc patterns/conn.bc patterns/patterns.bc patterns/apatterns.bc patterns/dpatterns.bc patterns/owl_vital_apat.bc patterns/eyes.bc patterns/influence.bc patterns/barriers.bc patterns/endgame.bc patterns/aa_attackpat.bc patterns/owl_defendpat.bc patterns/fusekipat.bc patterns/fuseki9.bc patterns/fuseki13.bc patterns/fuseki19.bc patterns/josekidb.bc patterns/handipat.bc utils/getopt.bc utils/getopt1.bc utils/gg_utils.bc utils/random.bc     -o gobmk.bc

