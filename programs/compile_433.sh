export dfi_stop_call_count=0xb2a3b6f
export dfi_config=0

cd $SPECPATH/433.milc/src/
$LLVMPATH/bin/clang  -emit-llvm -c -o control.bc -DSPEC_CPU -DNDEBUG -I. -DFN -DFAST -DCONGRAD_TMP_VECTORS -DDSLASH_TMP_LINKS   -O3 -static -std=gnu89       -DSPEC_CPU_LP64         control.c
$LLVMPATH/bin/clang  -emit-llvm -c -o f_meas.bc -DSPEC_CPU -DNDEBUG -I. -DFN -DFAST -DCONGRAD_TMP_VECTORS -DDSLASH_TMP_LINKS   -O3 -static -std=gnu89       -DSPEC_CPU_LP64         f_meas.c
$LLVMPATH/bin/clang  -emit-llvm -c -o gauge_info.bc -DSPEC_CPU -DNDEBUG -I. -DFN -DFAST -DCONGRAD_TMP_VECTORS -DDSLASH_TMP_LINKS   -O3 -static -std=gnu89       -DSPEC_CPU_LP64         gauge_info.c
$LLVMPATH/bin/clang  -emit-llvm -c -o setup.bc -DSPEC_CPU -DNDEBUG -I. -DFN -DFAST -DCONGRAD_TMP_VECTORS -DDSLASH_TMP_LINKS   -O3 -static -std=gnu89       -DSPEC_CPU_LP64         setup.c
$LLVMPATH/bin/clang  -emit-llvm -c -o update.bc -DSPEC_CPU -DNDEBUG -I. -DFN -DFAST -DCONGRAD_TMP_VECTORS -DDSLASH_TMP_LINKS   -O3 -static -std=gnu89       -DSPEC_CPU_LP64         update.c
$LLVMPATH/bin/clang  -emit-llvm -c -o update_h.bc -DSPEC_CPU -DNDEBUG -I. -DFN -DFAST -DCONGRAD_TMP_VECTORS -DDSLASH_TMP_LINKS   -O3 -static -std=gnu89       -DSPEC_CPU_LP64         update_h.c
$LLVMPATH/bin/clang  -emit-llvm -c -o update_u.bc -DSPEC_CPU -DNDEBUG -I. -DFN -DFAST -DCONGRAD_TMP_VECTORS -DDSLASH_TMP_LINKS   -O3 -static -std=gnu89       -DSPEC_CPU_LP64         update_u.c
$LLVMPATH/bin/clang  -emit-llvm -c -o layout_hyper.bc -DSPEC_CPU -DNDEBUG -I. -DFN -DFAST -DCONGRAD_TMP_VECTORS -DDSLASH_TMP_LINKS   -O3 -static -std=gnu89       -DSPEC_CPU_LP64         layout_hyper.c
$LLVMPATH/bin/clang  -emit-llvm -c -o check_unitarity.bc -DSPEC_CPU -DNDEBUG -I. -DFN -DFAST -DCONGRAD_TMP_VECTORS -DDSLASH_TMP_LINKS   -O3 -static -std=gnu89       -DSPEC_CPU_LP64         check_unitarity.c
$LLVMPATH/bin/clang  -emit-llvm -c -o d_plaq4.bc -DSPEC_CPU -DNDEBUG -I. -DFN -DFAST -DCONGRAD_TMP_VECTORS -DDSLASH_TMP_LINKS   -O3 -static -std=gnu89       -DSPEC_CPU_LP64         d_plaq4.c
$LLVMPATH/bin/clang  -emit-llvm -c -o gaugefix2.bc -DSPEC_CPU -DNDEBUG -I. -DFN -DFAST -DCONGRAD_TMP_VECTORS -DDSLASH_TMP_LINKS   -O3 -static -std=gnu89       -DSPEC_CPU_LP64         gaugefix2.c
$LLVMPATH/bin/clang  -emit-llvm -c -o io_helpers.bc -DSPEC_CPU -DNDEBUG -I. -DFN -DFAST -DCONGRAD_TMP_VECTORS -DDSLASH_TMP_LINKS   -O3 -static -std=gnu89       -DSPEC_CPU_LP64         io_helpers.c
$LLVMPATH/bin/clang  -emit-llvm -c -o io_lat4.bc -DSPEC_CPU -DNDEBUG -I. -DFN -DFAST -DCONGRAD_TMP_VECTORS -DDSLASH_TMP_LINKS   -O3 -static -std=gnu89       -DSPEC_CPU_LP64         io_lat4.c
$LLVMPATH/bin/clang  -emit-llvm -c -o make_lattice.bc -DSPEC_CPU -DNDEBUG -I. -DFN -DFAST -DCONGRAD_TMP_VECTORS -DDSLASH_TMP_LINKS   -O3 -static -std=gnu89       -DSPEC_CPU_LP64         make_lattice.c
$LLVMPATH/bin/clang  -emit-llvm -c -o path_product.bc -DSPEC_CPU -DNDEBUG -I. -DFN -DFAST -DCONGRAD_TMP_VECTORS -DDSLASH_TMP_LINKS   -O3 -static -std=gnu89       -DSPEC_CPU_LP64         path_product.c
$LLVMPATH/bin/clang  -emit-llvm -c -o ploop3.bc -DSPEC_CPU -DNDEBUG -I. -DFN -DFAST -DCONGRAD_TMP_VECTORS -DDSLASH_TMP_LINKS   -O3 -static -std=gnu89       -DSPEC_CPU_LP64         ploop3.c
$LLVMPATH/bin/clang  -emit-llvm -c -o ranmom.bc -DSPEC_CPU -DNDEBUG -I. -DFN -DFAST -DCONGRAD_TMP_VECTORS -DDSLASH_TMP_LINKS   -O3 -static -std=gnu89       -DSPEC_CPU_LP64         ranmom.c
$LLVMPATH/bin/clang  -emit-llvm -c -o ranstuff.bc -DSPEC_CPU -DNDEBUG -I. -DFN -DFAST -DCONGRAD_TMP_VECTORS -DDSLASH_TMP_LINKS   -O3 -static -std=gnu89       -DSPEC_CPU_LP64         ranstuff.c
$LLVMPATH/bin/clang  -emit-llvm -c -o reunitarize2.bc -DSPEC_CPU -DNDEBUG -I. -DFN -DFAST -DCONGRAD_TMP_VECTORS -DDSLASH_TMP_LINKS   -O3 -static -std=gnu89       -DSPEC_CPU_LP64         reunitarize2.c
$LLVMPATH/bin/clang  -emit-llvm -c -o gauge_stuff.bc -DSPEC_CPU -DNDEBUG -I. -DFN -DFAST -DCONGRAD_TMP_VECTORS -DDSLASH_TMP_LINKS   -O3 -static -std=gnu89       -DSPEC_CPU_LP64         gauge_stuff.c
$LLVMPATH/bin/clang  -emit-llvm -c -o grsource_imp.bc -DSPEC_CPU -DNDEBUG -I. -DFN -DFAST -DCONGRAD_TMP_VECTORS -DDSLASH_TMP_LINKS   -O3 -static -std=gnu89       -DSPEC_CPU_LP64         grsource_imp.c
$LLVMPATH/bin/clang  -emit-llvm -c -o mat_invert.bc -DSPEC_CPU -DNDEBUG -I. -DFN -DFAST -DCONGRAD_TMP_VECTORS -DDSLASH_TMP_LINKS   -O3 -static -std=gnu89       -DSPEC_CPU_LP64         mat_invert.c
$LLVMPATH/bin/clang  -emit-llvm -c -o quark_stuff.bc -DSPEC_CPU -DNDEBUG -I. -DFN -DFAST -DCONGRAD_TMP_VECTORS -DDSLASH_TMP_LINKS   -O3 -static -std=gnu89       -DSPEC_CPU_LP64         quark_stuff.c
$LLVMPATH/bin/clang  -emit-llvm -c -o rephase.bc -DSPEC_CPU -DNDEBUG -I. -DFN -DFAST -DCONGRAD_TMP_VECTORS -DDSLASH_TMP_LINKS   -O3 -static -std=gnu89       -DSPEC_CPU_LP64         rephase.c
$LLVMPATH/bin/clang  -emit-llvm -c -o cmplx.bc -DSPEC_CPU -DNDEBUG -I. -DFN -DFAST -DCONGRAD_TMP_VECTORS -DDSLASH_TMP_LINKS   -O3 -static -std=gnu89       -DSPEC_CPU_LP64         cmplx.c
$LLVMPATH/bin/clang  -emit-llvm -c -o addmat.bc -DSPEC_CPU -DNDEBUG -I. -DFN -DFAST -DCONGRAD_TMP_VECTORS -DDSLASH_TMP_LINKS   -O3 -static -std=gnu89       -DSPEC_CPU_LP64         addmat.c
$LLVMPATH/bin/clang  -emit-llvm -c -o addvec.bc -DSPEC_CPU -DNDEBUG -I. -DFN -DFAST -DCONGRAD_TMP_VECTORS -DDSLASH_TMP_LINKS   -O3 -static -std=gnu89       -DSPEC_CPU_LP64         addvec.c
$LLVMPATH/bin/clang  -emit-llvm -c -o clear_mat.bc -DSPEC_CPU -DNDEBUG -I. -DFN -DFAST -DCONGRAD_TMP_VECTORS -DDSLASH_TMP_LINKS   -O3 -static -std=gnu89       -DSPEC_CPU_LP64         clear_mat.c
$LLVMPATH/bin/clang  -emit-llvm -c -o clearvec.bc -DSPEC_CPU -DNDEBUG -I. -DFN -DFAST -DCONGRAD_TMP_VECTORS -DDSLASH_TMP_LINKS   -O3 -static -std=gnu89       -DSPEC_CPU_LP64         clearvec.c
$LLVMPATH/bin/clang  -emit-llvm -c -o m_amatvec.bc -DSPEC_CPU -DNDEBUG -I. -DFN -DFAST -DCONGRAD_TMP_VECTORS -DDSLASH_TMP_LINKS   -O3 -static -std=gnu89       -DSPEC_CPU_LP64         m_amatvec.c
$LLVMPATH/bin/clang  -emit-llvm -c -o m_mat_an.bc -DSPEC_CPU -DNDEBUG -I. -DFN -DFAST -DCONGRAD_TMP_VECTORS -DDSLASH_TMP_LINKS   -O3 -static -std=gnu89       -DSPEC_CPU_LP64         m_mat_an.c
$LLVMPATH/bin/clang  -emit-llvm -c -o m_mat_na.bc -DSPEC_CPU -DNDEBUG -I. -DFN -DFAST -DCONGRAD_TMP_VECTORS -DDSLASH_TMP_LINKS   -O3 -static -std=gnu89       -DSPEC_CPU_LP64         m_mat_na.c
$LLVMPATH/bin/clang  -emit-llvm -c -o m_mat_nn.bc -DSPEC_CPU -DNDEBUG -I. -DFN -DFAST -DCONGRAD_TMP_VECTORS -DDSLASH_TMP_LINKS   -O3 -static -std=gnu89       -DSPEC_CPU_LP64         m_mat_nn.c
$LLVMPATH/bin/clang  -emit-llvm -c -o m_matvec.bc -DSPEC_CPU -DNDEBUG -I. -DFN -DFAST -DCONGRAD_TMP_VECTORS -DDSLASH_TMP_LINKS   -O3 -static -std=gnu89       -DSPEC_CPU_LP64         m_matvec.c
$LLVMPATH/bin/clang  -emit-llvm -c -o make_ahmat.bc -DSPEC_CPU -DNDEBUG -I. -DFN -DFAST -DCONGRAD_TMP_VECTORS -DDSLASH_TMP_LINKS   -O3 -static -std=gnu89       -DSPEC_CPU_LP64         make_ahmat.c
$LLVMPATH/bin/clang  -emit-llvm -c -o rand_ahmat.bc -DSPEC_CPU -DNDEBUG -I. -DFN -DFAST -DCONGRAD_TMP_VECTORS -DDSLASH_TMP_LINKS   -O3 -static -std=gnu89       -DSPEC_CPU_LP64         rand_ahmat.c
$LLVMPATH/bin/clang  -emit-llvm -c -o realtr.bc -DSPEC_CPU -DNDEBUG -I. -DFN -DFAST -DCONGRAD_TMP_VECTORS -DDSLASH_TMP_LINKS   -O3 -static -std=gnu89       -DSPEC_CPU_LP64         realtr.c
$LLVMPATH/bin/clang  -emit-llvm -c -o s_m_a_mat.bc -DSPEC_CPU -DNDEBUG -I. -DFN -DFAST -DCONGRAD_TMP_VECTORS -DDSLASH_TMP_LINKS   -O3 -static -std=gnu89       -DSPEC_CPU_LP64         s_m_a_mat.c
$LLVMPATH/bin/clang  -emit-llvm -c -o s_m_a_vec.bc -DSPEC_CPU -DNDEBUG -I. -DFN -DFAST -DCONGRAD_TMP_VECTORS -DDSLASH_TMP_LINKS   -O3 -static -std=gnu89       -DSPEC_CPU_LP64         s_m_a_vec.c
$LLVMPATH/bin/clang  -emit-llvm -c -o s_m_s_mat.bc -DSPEC_CPU -DNDEBUG -I. -DFN -DFAST -DCONGRAD_TMP_VECTORS -DDSLASH_TMP_LINKS   -O3 -static -std=gnu89       -DSPEC_CPU_LP64         s_m_s_mat.c
$LLVMPATH/bin/clang  -emit-llvm -c -o s_m_vec.bc -DSPEC_CPU -DNDEBUG -I. -DFN -DFAST -DCONGRAD_TMP_VECTORS -DDSLASH_TMP_LINKS   -O3 -static -std=gnu89       -DSPEC_CPU_LP64         s_m_vec.c
$LLVMPATH/bin/clang  -emit-llvm -c -o s_m_mat.bc -DSPEC_CPU -DNDEBUG -I. -DFN -DFAST -DCONGRAD_TMP_VECTORS -DDSLASH_TMP_LINKS   -O3 -static -std=gnu89       -DSPEC_CPU_LP64         s_m_mat.c
$LLVMPATH/bin/clang  -emit-llvm -c -o su3_adjoint.bc -DSPEC_CPU -DNDEBUG -I. -DFN -DFAST -DCONGRAD_TMP_VECTORS -DDSLASH_TMP_LINKS   -O3 -static -std=gnu89       -DSPEC_CPU_LP64         su3_adjoint.c
$LLVMPATH/bin/clang  -emit-llvm -c -o su3_dot.bc -DSPEC_CPU -DNDEBUG -I. -DFN -DFAST -DCONGRAD_TMP_VECTORS -DDSLASH_TMP_LINKS   -O3 -static -std=gnu89       -DSPEC_CPU_LP64         su3_dot.c
$LLVMPATH/bin/clang  -emit-llvm -c -o su3_rdot.bc -DSPEC_CPU -DNDEBUG -I. -DFN -DFAST -DCONGRAD_TMP_VECTORS -DDSLASH_TMP_LINKS   -O3 -static -std=gnu89       -DSPEC_CPU_LP64         su3_rdot.c
$LLVMPATH/bin/clang  -emit-llvm -c -o su3_proj.bc -DSPEC_CPU -DNDEBUG -I. -DFN -DFAST -DCONGRAD_TMP_VECTORS -DDSLASH_TMP_LINKS   -O3 -static -std=gnu89       -DSPEC_CPU_LP64         su3_proj.c
$LLVMPATH/bin/clang  -emit-llvm -c -o su3mat_copy.bc -DSPEC_CPU -DNDEBUG -I. -DFN -DFAST -DCONGRAD_TMP_VECTORS -DDSLASH_TMP_LINKS   -O3 -static -std=gnu89       -DSPEC_CPU_LP64         su3mat_copy.c
$LLVMPATH/bin/clang  -emit-llvm -c -o submat.bc -DSPEC_CPU -DNDEBUG -I. -DFN -DFAST -DCONGRAD_TMP_VECTORS -DDSLASH_TMP_LINKS   -O3 -static -std=gnu89       -DSPEC_CPU_LP64         submat.c
$LLVMPATH/bin/clang  -emit-llvm -c -o subvec.bc -DSPEC_CPU -DNDEBUG -I. -DFN -DFAST -DCONGRAD_TMP_VECTORS -DDSLASH_TMP_LINKS   -O3 -static -std=gnu89       -DSPEC_CPU_LP64         subvec.c
$LLVMPATH/bin/clang  -emit-llvm -c -o trace_su3.bc -DSPEC_CPU -DNDEBUG -I. -DFN -DFAST -DCONGRAD_TMP_VECTORS -DDSLASH_TMP_LINKS   -O3 -static -std=gnu89       -DSPEC_CPU_LP64         trace_su3.c
$LLVMPATH/bin/clang  -emit-llvm -c -o uncmp_ahmat.bc -DSPEC_CPU -DNDEBUG -I. -DFN -DFAST -DCONGRAD_TMP_VECTORS -DDSLASH_TMP_LINKS   -O3 -static -std=gnu89       -DSPEC_CPU_LP64         uncmp_ahmat.c
$LLVMPATH/bin/clang  -emit-llvm -c -o msq_su3vec.bc -DSPEC_CPU -DNDEBUG -I. -DFN -DFAST -DCONGRAD_TMP_VECTORS -DDSLASH_TMP_LINKS   -O3 -static -std=gnu89       -DSPEC_CPU_LP64         msq_su3vec.c
$LLVMPATH/bin/clang  -emit-llvm -c -o sub4vecs.bc -DSPEC_CPU -DNDEBUG -I. -DFN -DFAST -DCONGRAD_TMP_VECTORS -DDSLASH_TMP_LINKS   -O3 -static -std=gnu89       -DSPEC_CPU_LP64         sub4vecs.c
$LLVMPATH/bin/clang  -emit-llvm -c -o m_amv_4dir.bc -DSPEC_CPU -DNDEBUG -I. -DFN -DFAST -DCONGRAD_TMP_VECTORS -DDSLASH_TMP_LINKS   -O3 -static -std=gnu89       -DSPEC_CPU_LP64         m_amv_4dir.c
$LLVMPATH/bin/clang  -emit-llvm -c -o m_amv_4vec.bc -DSPEC_CPU -DNDEBUG -I. -DFN -DFAST -DCONGRAD_TMP_VECTORS -DDSLASH_TMP_LINKS   -O3 -static -std=gnu89       -DSPEC_CPU_LP64         m_amv_4vec.c
$LLVMPATH/bin/clang  -emit-llvm -c -o m_mv_s_4dir.bc -DSPEC_CPU -DNDEBUG -I. -DFN -DFAST -DCONGRAD_TMP_VECTORS -DDSLASH_TMP_LINKS   -O3 -static -std=gnu89       -DSPEC_CPU_LP64         m_mv_s_4dir.c
$LLVMPATH/bin/clang  -emit-llvm -c -o m_su2_mat_vec_n.bc -DSPEC_CPU -DNDEBUG -I. -DFN -DFAST -DCONGRAD_TMP_VECTORS -DDSLASH_TMP_LINKS   -O3 -static -std=gnu89       -DSPEC_CPU_LP64         m_su2_mat_vec_n.c
$LLVMPATH/bin/clang  -emit-llvm -c -o l_su2_hit_n.bc -DSPEC_CPU -DNDEBUG -I. -DFN -DFAST -DCONGRAD_TMP_VECTORS -DDSLASH_TMP_LINKS   -O3 -static -std=gnu89       -DSPEC_CPU_LP64         l_su2_hit_n.c
$LLVMPATH/bin/clang  -emit-llvm -c -o r_su2_hit_a.bc -DSPEC_CPU -DNDEBUG -I. -DFN -DFAST -DCONGRAD_TMP_VECTORS -DDSLASH_TMP_LINKS   -O3 -static -std=gnu89       -DSPEC_CPU_LP64         r_su2_hit_a.c
$LLVMPATH/bin/clang  -emit-llvm -c -o m_su2_mat_vec_a.bc -DSPEC_CPU -DNDEBUG -I. -DFN -DFAST -DCONGRAD_TMP_VECTORS -DDSLASH_TMP_LINKS   -O3 -static -std=gnu89       -DSPEC_CPU_LP64         m_su2_mat_vec_a.c
$LLVMPATH/bin/clang  -emit-llvm -c -o gaussrand.bc -DSPEC_CPU -DNDEBUG -I. -DFN -DFAST -DCONGRAD_TMP_VECTORS -DDSLASH_TMP_LINKS   -O3 -static -std=gnu89       -DSPEC_CPU_LP64         gaussrand.c
$LLVMPATH/bin/clang  -emit-llvm -c -o byterevn.bc -DSPEC_CPU -DNDEBUG -I. -DFN -DFAST -DCONGRAD_TMP_VECTORS -DDSLASH_TMP_LINKS   -O3 -static -std=gnu89       -DSPEC_CPU_LP64         byterevn.c
$LLVMPATH/bin/clang  -emit-llvm -c -o m_mat_hwvec.bc -DSPEC_CPU -DNDEBUG -I. -DFN -DFAST -DCONGRAD_TMP_VECTORS -DDSLASH_TMP_LINKS   -O3 -static -std=gnu89       -DSPEC_CPU_LP64         m_mat_hwvec.c
$LLVMPATH/bin/clang  -emit-llvm -c -o m_amat_hwvec.bc -DSPEC_CPU -DNDEBUG -I. -DFN -DFAST -DCONGRAD_TMP_VECTORS -DDSLASH_TMP_LINKS   -O3 -static -std=gnu89       -DSPEC_CPU_LP64         m_amat_hwvec.c
$LLVMPATH/bin/clang  -emit-llvm -c -o dslash_fn2.bc -DSPEC_CPU -DNDEBUG -I. -DFN -DFAST -DCONGRAD_TMP_VECTORS -DDSLASH_TMP_LINKS   -O3 -static -std=gnu89       -DSPEC_CPU_LP64         dslash_fn2.c
$LLVMPATH/bin/clang  -emit-llvm -c -o d_congrad5_fn.bc -DSPEC_CPU -DNDEBUG -I. -DFN -DFAST -DCONGRAD_TMP_VECTORS -DDSLASH_TMP_LINKS   -O3 -static -std=gnu89       -DSPEC_CPU_LP64         d_congrad5_fn.c
$LLVMPATH/bin/clang  -emit-llvm -c -o com_vanilla.bc -DSPEC_CPU -DNDEBUG -I. -DFN -DFAST -DCONGRAD_TMP_VECTORS -DDSLASH_TMP_LINKS   -O3 -static -std=gnu89       -DSPEC_CPU_LP64         com_vanilla.c
$LLVMPATH/bin/clang  -emit-llvm -c -o io_nonansi.bc -DSPEC_CPU -DNDEBUG -I. -DFN -DFAST -DCONGRAD_TMP_VECTORS -DDSLASH_TMP_LINKS   -O3 -static -std=gnu89       -DSPEC_CPU_LP64         io_nonansi.c
cp *.bc $WORKPATH
rm *.bc

cd $WORKPATH
$LLVMPATH/bin/llvm-link control.bc f_meas.bc gauge_info.bc setup.bc update.bc update_h.bc update_u.bc layout_hyper.bc check_unitarity.bc d_plaq4.bc gaugefix2.bc io_helpers.bc io_lat4.bc make_lattice.bc path_product.bc ploop3.bc ranmom.bc ranstuff.bc reunitarize2.bc gauge_stuff.bc grsource_imp.bc mat_invert.bc quark_stuff.bc rephase.bc cmplx.bc addmat.bc addvec.bc clear_mat.bc clearvec.bc m_amatvec.bc m_mat_an.bc m_mat_na.bc m_mat_nn.bc m_matvec.bc make_ahmat.bc rand_ahmat.bc realtr.bc s_m_a_mat.bc s_m_a_vec.bc s_m_s_mat.bc s_m_vec.bc s_m_mat.bc su3_adjoint.bc su3_dot.bc su3_rdot.bc su3_proj.bc su3mat_copy.bc submat.bc subvec.bc trace_su3.bc uncmp_ahmat.bc msq_su3vec.bc sub4vecs.bc m_amv_4dir.bc m_amv_4vec.bc m_mv_s_4dir.bc m_su2_mat_vec_n.bc l_su2_hit_n.bc r_su2_hit_a.bc m_su2_mat_vec_a.bc gaussrand.bc byterevn.bc m_mat_hwvec.bc m_amat_hwvec.bc dslash_fn2.bc d_congrad5_fn.bc com_vanilla.bc io_nonansi.bc   -o milc.bc

