	.file	"testatt.c"
	.option nopic
	.globl	rdt
	.bss
	.align	3
	.type	rdt, @object
	.size	rdt, 4096
rdt:
	.zero	4096
	.globl	rds
	.align	3
	.type	rds, @object
	.size	rds, 8192
rds:
	.zero	8192
	.globl	debug
	.align	3
	.type	debug, @object
	.size	debug, 4096
debug:
	.zero	4096
	.text
	.align	1
	.globl	func4
	.type	func4, @function
func4:
	addi	sp,sp,-48
	sd	s0,40(sp)
	addi	s0,sp,48
	mv	t1,a0
	mv	a7,a1
	mv	a0,a2
	mv	a1,a3
	mv	a2,a4
	mv	a3,a5
	mv	a4,a6
	mv	a5,t1
	sw	a5,-20(s0)
	mv	a5,a7
	sw	a5,-24(s0)
	mv	a5,a0
	sw	a5,-28(s0)
	mv	a5,a1
	sw	a5,-32(s0)
	mv	a5,a2
	sw	a5,-36(s0)
	mv	a5,a3
	sw	a5,-40(s0)
	mv	a5,a4
	sw	a5,-44(s0)
 #APP
# 10 "testatt.c" 1
	.word 0x2000040b
# 0 "" 2
 #NO_APP
	nop
	ld	s0,40(sp)
	addi	sp,sp,48
	jr	ra
	.size	func4, .-func4
	.align	1
	.globl	func3
	.type	func3, @function
func3:
	addi	sp,sp,-48
	sd	s0,40(sp)
	addi	s0,sp,48
	sd	a0,-24(s0)
	sd	a1,-32(s0)
	mv	a5,a2
	sw	a5,-36(s0)
	nop
	ld	s0,40(sp)
	addi	sp,sp,48
add ra,zero,ra
	 #APP
# 14 "testatt.c" 1
	.word 0x2400040b
# 0 "" 2
 #NO_APP
	jr	ra
	.size	func3, .-func3
	.align	1
	.globl	func2
	.type	func2, @function
func2:
	addi	sp,sp,-16
	sd	s0,8(sp)
	addi	s0,sp,16
	nop
	ld	s0,8(sp)
	addi	sp,sp,16
add ra,zero,ra
	 #APP
# 18 "testatt.c" 1
	.word 0x2400018b
# 0 "" 2
 #NO_APP
	jr	ra
	.size	func2, .-func2
	.align	1
	.globl	func
	.type	func, @function
func:
	addi	sp,sp,-32
	sd	ra,24(sp)
	sd	s0,16(sp)
	addi	s0,sp,32
	mv	a5,a0
	mv	a3,a1
	mv	a4,a2
	sw	a5,-20(s0)
	mv	a5,a3
	sw	a5,-24(s0)
	mv	a5,a4
	sw	a5,-28(s0)
 #APP
# 27 "testatt.c" 1
	.word 0x2000010b
# 0 "" 2
 #NO_APP
	call	func2
	nop
	ld	ra,24(sp)
	ld	s0,16(sp)
	addi	sp,sp,32
add ra,zero,ra
	 #APP
# 29 "testatt.c" 1
	.word 0x2400020b
# 0 "" 2
 #NO_APP
	jr	ra
	.size	func, .-func
	.section	.rodata
	.align	3
.LC0:
	.string	"%x,%x,%x,%x\n"
	.align	3
.LC1:
	.string	"rds %x,%x\n"
	.align	3
.LC3:
	.string	"rds %lx\n"
	.align	3
.LC4:
	.string	"hello\n"
	.align	3
.LC5:
	.string	"a's address %x, b's address %x, c's address %x\n"
	.align	3
.LC6:
	.string	"-------\n"
	.align	3
.LC7:
	.string	"%x\n"
	.align	3
.LC8:
	.string	"-------ld taraddrs\n"
	.align	3
.LC9:
	.string	"%lx -> %lx\n"
	.text
	.align	1
	.globl	main
	.type	main, @function
main:
	addi	sp,sp,-96
	sd	ra,88(sp)
	sd	s0,80(sp)
	addi	s0,sp,96
	lla	a5,func
	sd	a5,-32(s0)
	lla	a5,rdt+4096
	lla	a4,debug
	lla	a3,rds
	mv	a2,a5
	lla	a1,rdt
	lla	a0,.LC0
	call	printf
	lla	a2,rds+80
	lla	a1,rds
	lla	a0,.LC1
	call	printf
	sw	zero,-20(s0)
	j	.L6
.L7:
	lla	a4,rdt
	lw	a5,-20(s0)
	slli	a5,a5,2
	add	a5,a4,a5
	sw	zero,0(a5)
	lw	a5,-20(s0)
	addiw	a5,a5,1
	sw	a5,-20(s0)
.L6:
	lw	a5,-20(s0)
	sext.w	a4,a5
	li	a5,1023
	ble	a4,a5,.L7
	lla	a5,rds
	li	a3,36
	li	a4,36
	slli	a3,a3,32
	add	a4,a3,a4
	sd	a4,0(a5)
	lla	a5,rds
	li	a3,36
	li	a4,36
	slli	a3,a3,32
	add	a4,a3,a4
	sd	a4,8(a5)
	lla	a5,rds
	li	a3,36
	li	a4,36
	slli	a3,a3,32
	add	a4,a3,a4
	sd	a4,16(a5)
	lla	a5,rds
	li	a3,36
	li	a4,36
	slli	a3,a3,32
	add	a4,a3,a4
	sd	a4,24(a5)
	lla	a5,rds
	li	a3,36
	li	a4,36
	slli	a3,a3,32
	add	a4,a3,a4
	sd	a4,32(a5)
	lla	a5,rds
	li	a4,37
	slli	a4,a4,32
	addi	a4,a4,36
	sd	a4,40(a5)
	lla	a5,rds
	li	a4,39
	slli	a4,a4,32
	addi	a4,a4,37
	sd	a4,48(a5)
	lla	a5,rds
	li	a4,5
	slli	a4,a4,35
	addi	a4,a4,39
	sd	a4,56(a5)
	lla	a5,rds
	li	a4,21
	slli	a4,a4,33
	addi	a4,a4,40
	sd	a4,64(a5)
	lla	a5,rds
	lla	a4,.LC2
	ld	a4,0(a4)
	sd	a4,72(a5)
	lla	a5,rds
	li	a4,262144
	addi	a4,a4,8
	sd	a4,80(a5)
	lla	a5,rds
	ld	a5,72(a5)
	mv	a1,a5
	lla	a0,.LC3
	call	printf
	lla	a0,.LC4
	call	printf
	lla	a5,rdt
	li	a4,1
	sw	a4,0(a5)
 #APP
# 133 "testatt.c" 1
	.word 0x4000000b
# 0 "" 2
 #NO_APP
	lla	a5,rds
	li	a4,1
	sd	a4,0(a5)
 #APP
# 135 "testatt.c" 1
	.word 0x8000000b
# 0 "" 2
 #NO_APP
	lla	a5,debug
	li	a4,100
	sw	a4,0(a5)
 #APP
# 137 "testatt.c" 1
	.word 0xc000000b
# 0 "" 2
 #NO_APP
	addi	a3,s0,-96
	addi	a4,s0,-80
	addi	a5,s0,-64
	mv	a2,a4
	mv	a1,a5
	lla	a0,.LC5
	call	printf
	sw	zero,-20(s0)
	j	.L8
.L9:
	lw	a5,-20(s0)
	slli	a5,a5,2
	addi	a4,s0,-16
	add	a5,a4,a5
	sw	zero,-48(a5)
 #APP
# 145 "testatt.c" 1
	.word 0x0000008b
# 0 "" 2
 #NO_APP
	lw	a5,-20(s0)
	addiw	a5,a5,1
	sw	a5,-20(s0)
.L8:
	lw	a5,-20(s0)
	sext.w	a4,a5
	li	a5,3
	ble	a4,a5,.L9
	sw	zero,-20(s0)
	j	.L10
.L11:
	lw	a5,-20(s0)
	slli	a5,a5,2
	addi	a4,s0,-16
	add	a5,a4,a5
	sw	zero,-64(a5)
 #APP
# 133 "testatt.c" 1
	.word 0xc800000b
# 0 "" 2
 #NO_APP


	lw	a5,-20(s0)
	addiw	a5,a5,1
	sw	a5,-20(s0)
.L10:
	lw	a5,-20(s0)
	sext.w	a4,a5
	li	a5,3
	ble	a4,a5,.L11
	sw	zero,-20(s0)
	j	.L12
.L13:
	lw	a5,-20(s0)
	slli	a5,a5,2
	addi	a4,s0,-16
	add	a5,a4,a5
	sw	zero,-80(a5)

	lw	a5,-20(s0)
	addiw	a5,a5,1
	sw	a5,-20(s0)
.L12:
	lw	a5,-20(s0)
	sext.w	a4,a5
	li	a5,3
	ble	a4,a5,.L13
	sw	zero,-20(s0)
	j	.L14
.L15:
	lw	a5,-20(s0)
	slli	a5,a5,2
	addi	a4,s0,-16
	add	a5,a4,a5
	lw	a5,-48(a5)
	sw	a5,-44(s0)

	lw	a5,-20(s0)
	addiw	a5,a5,1
	sw	a5,-20(s0)
.L14:
	lw	a5,-20(s0)
	sext.w	a4,a5
	li	a5,3
	ble	a4,a5,.L15
	sw	zero,-20(s0)
	j	.L16
.L17:
	lw	a5,-20(s0)
	slli	a5,a5,2
	addi	a4,s0,-16
	add	a5,a4,a5
	lw	a4,-20(s0)
	sw	a4,-64(a5)

	lw	a5,-20(s0)
	addiw	a5,a5,1
	sw	a5,-20(s0)
.L16:
	lw	a5,-20(s0)
	sext.w	a4,a5
	li	a5,6
	ble	a4,a5,.L17
	sw	zero,-20(s0)
	j	.L18
.L19:
	lw	a5,-20(s0)
	slli	a5,a5,2
	addi	a4,s0,-16
	add	a5,a4,a5
	lw	a5,-48(a5)
	sw	a5,-44(s0)

	lw	a5,-20(s0)
	addiw	a5,a5,1
	sw	a5,-20(s0)
.L18:
	lw	a5,-20(s0)
	sext.w	a4,a5
	li	a5,3
	ble	a4,a5,.L19
	sw	zero,-20(s0)
	j	.L20
.L21:
	lw	a5,-20(s0)
	slli	a5,a5,2
	addi	a4,s0,-16
	add	a5,a4,a5
	lw	a5,-64(a5)
	sw	a5,-44(s0)

	lw	a5,-20(s0)
	addiw	a5,a5,1
	sw	a5,-20(s0)
.L20:
	lw	a5,-20(s0)
	sext.w	a4,a5
	li	a5,3
	ble	a4,a5,.L21
	sw	zero,-20(s0)
	j	.L22
.L23:
	lw	a5,-20(s0)
	slli	a5,a5,2
	addi	a4,s0,-16
	add	a5,a4,a5
	lw	a5,-80(a5)
	sw	a5,-44(s0)

	lw	a5,-20(s0)
	addiw	a5,a5,1
	sw	a5,-20(s0)
.L22:
	lw	a5,-20(s0)
	sext.w	a4,a5
	li	a5,3
	ble	a4,a5,.L23
	sw	zero,-20(s0)
	j	.L24
.L25:
	lw	a5,-20(s0)
	slli	a5,a5,2
	addi	a4,s0,-16
	add	a5,a4,a5
	lw	a5,-48(a5)
	sw	a5,-44(s0)

	lw	a5,-20(s0)
	addiw	a5,a5,1
	sw	a5,-20(s0)
.L24:
	lw	a5,-20(s0)
	sext.w	a4,a5
	li	a5,3
	ble	a4,a5,.L25
	li	a2,69
	li	a1,52
	li	a0,18

	
	ld	a5,-32(s0)
	li	a2,69
	li	a1,52
	li	a0,18

	jalr	a5
	addi	a4,s0,-80
	addi	a5,s0,-96
	li	a2,4
	mv	a1,a4
	mv	a0,a5

	
	addi	a4,s0,-96
	addi	a5,s0,-80
	
	mv	a1,a4
	mv	a0,a5
	li	a2,24


	sw	zero,-20(s0)
	j	.L26
.L27:
	lw	a5,-20(s0)
	slli	a5,a5,2
	addi	a4,s0,-16
	add	a5,a4,a5
	lw	a5,-48(a5)
	sw	a5,-44(s0)

	lw	a5,-20(s0)
	addiw	a5,a5,1
	sw	a5,-20(s0)
.L26:
	lw	a5,-20(s0)
	sext.w	a4,a5
	li	a5,3
	ble	a4,a5,.L27
	sw	zero,-20(s0)
	j	.L28
.L29:
	lw	a5,-20(s0)
	slli	a5,a5,2
	addi	a4,s0,-16
	add	a5,a4,a5
	lw	a5,-80(a5)
	sw	a5,-44(s0)

	lw	a5,-20(s0)
	addiw	a5,a5,1
	sw	a5,-20(s0)
.L28:
	lw	a5,-20(s0)
	sext.w	a4,a5
	li	a5,3
	ble	a4,a5,.L29
 #APP
# 208 "testatt.c" 1
	.word 0xd000000b
# 0 "" 2
 #NO_APP
	lla	a0,.LC6
	call	printf
	lla	a5,debug
	sd	a5,-40(s0)
	sw	zero,-20(s0)
	j	.L30
.L31:
	lw	a5,-20(s0)
	slli	a5,a5,3
	ld	a4,-40(s0)
	add	a5,a4,a5
	ld	a5,0(a5)
	mv	a1,a5
	lla	a0,.LC7
	call	printf
	lw	a5,-20(s0)
	addiw	a5,a5,1
	sw	a5,-20(s0)
.L30:
	lw	a5,-20(s0)
	sext.w	a4,a5
	li	a5,14
	ble	a4,a5,.L31
	lla	a0,.LC8
	call	printf
	sw	zero,-20(s0)
	j	.L32
.L33:
	lw	a5,-20(s0)
	addi	a5,a5,100
	slli	a5,a5,3
	ld	a4,-40(s0)
	add	a5,a4,a5
	ld	a4,0(a5)
	lw	a5,-20(s0)
	mv	a2,a4
	mv	a1,a5
	lla	a0,.LC9
	call	printf
	lw	a5,-20(s0)
	addiw	a5,a5,1
	sw	a5,-20(s0)
.L32:
	lw	a5,-20(s0)
	sext.w	a4,a5
	li	a5,524288
	addi	a5,a5,-257
	ble	a4,a5,.L33
	li	a5,0
	mv	a0,a5
	ld	ra,88(sp)
	ld	s0,80(sp)
	addi	sp,sp,96
	jr	ra
	.size	main, .-main
	.section	.rodata
	.align	3
.LC2:
	.dword	844433520328705
	.ident	"GCC: (GNU) 7.2.0"
