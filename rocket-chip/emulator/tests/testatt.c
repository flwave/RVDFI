#include <stdio.h> 
#include <stdlib.h> 
#include <unistd.h> 

unsigned int rdt[1024];
unsigned long rds[1024];
unsigned int debug[1024];

void func4(unsigned int a,unsigned int b,unsigned int c,unsigned int d,unsigned int e,unsigned int f,unsigned int g){
	asm(".word 0x2000040b");
}

void func3(unsigned int* a,unsigned int* b,unsigned int c){
	asm(".word 0x2000040b");
}

void func2(){
	asm(".word 0x2400018b");
}

void func(int a, int b, int c){
	//asm(".word 0x0000008b");
	//unsigned long *sa=0x800200f0;
	//int i;
	//for(i=0;i<4;i++)
	//printf("in func, stack address value: %lx\n",*(sa-i));
	asm(".word 0x2000010b");
	func2();
	asm(".word 0x2000020b");
}

int main() 
{
	/*
	rdt[0]=1;
	asm(".word 0x4000000b");
	rds[0]=1;
	asm(".word 0x8000000b");
	debug[0]=1;
	asm(".word 0xc000000b");
	int a[4];
	int b[4];
	int c[4];
	*/
	/*
	rdt[0]=1;
	asm(".word 0x4000000b");
	rds[0]=1;
	asm(".word 0x8000000b");
	debug[0]=1;
	asm(".word 0xe000000b");
	int i;
	
	rdt[0x70/4]=0x111;
	rdt[0x80/4]=0x112;
	rdt[0x90/4]=0x113;
	rdt[0x100/4]=0x1150114;
	
	asm(".word 0x0000018b");
	
	printf("-------\n");
	for(i=6;i<6+5;i++)
	printf("%x\n",debug[i]);
	printf("-------\n");
	for(i=16;i<16+6;i++)
	printf("%x\n",debug[i]);
	printf("-------\n");
	printf("rdt--- %x\n",rdt[0x70/4]);
	printf("rdt--- %x\n",rdt[0x80/4]);
	printf("rdt--- %x\n",rdt[0x90/4]);
	printf("rdt--- %x\n",rdt[0x100/4]);
	printf("rdt--- %x\n",rdt[0x102/4]);
	*/
	/*
	rdt[0]=1;
	asm(".word 0x4000000b");
	rds[0]=1;
	asm(".word 0x8000000b");
	debug[0]=1;
	asm(".word 0xe000000b");
	int temp;
	int i;
	int a[1];
	int b[1];
	a[0]=1;
	asm(".word 0x0000008b");
	b[0]=1;
	asm(".word 0x0000010b");
	
	asm(".word 0x28100e0b");
	func3(b,12,4);
	
	temp=a[0];
	asm(".word 0x0400028b");
	temp=b[0];
	asm(".word 0x0400030b");
	
	printf("-------\n");
	printf("%x\n",rds[0]);
	for(i=0;i<30;i++)
	printf("%x\n",debug[i]);
	for(i=0x58;i<0x58+8;i++)
	printf("rdt--- %x\n",rdt[i]);
	for(i=0x48;i<0x48+4;i++)
	printf("rdt--- %x\n",rdt[i]);
	*/
	
	void (*fp)(int,int,int);
	fp=func;
	printf("%x,%x,%x,%x\n",rdt,rdt+1024,rds,debug);
	printf("rds %x,%x\n",rds,&(rds[10]));
	int i;
	for(i=0;i<1024;i++){
		rdt[i]=0;
	}
	rds[0]=(36l<<32)+36;
	rds[1]=(36l<<32)+36;
	rds[2]=(36l<<32)+36;
	rds[3]=(36l<<32)+36;
	rds[4]=(36l<<32)+36;
	rds[5]=(37l<<32)+36;
	rds[6]=(39l<<32)+37;
	rds[7]=(40l<<32)+39;
	rds[8]=(42l<<32)+40;
	rds[9]=(3l<<48)|(2l<<32)|(4<<16)|1;
	rds[10]=(4<<16)|8;
	
	printf("rds %lx\n",rds[9]);
	
	printf("hello\n");
	
	rdt[0]=100;
	asm(".word 0x4000000b");
	rds[0]=100;
	asm(".word 0x8000000b");
	debug[0]=1;
	asm(".word 0xc000000b");
	int a[4];
	int b[4];
	int c[4];
	printf("a's address %x, b's address %x, c's address %x\n",a,b,c);
	int temp;
	for(i=0;i<4;i++){
		a[i]=0;
		asm(".word 0x0000008b");
	}
	for(i=0;i<4;i++){
		b[i]=0;
		asm(".word 0x0000010b");
	}
	for(i=0;i<4;i++){
		c[i]=0;
		asm(".word 0x0000018b");
	}
	for(i=0;i<4;i++){
		temp=a[i];
		asm(".word 0x0400028b");
		//printf("%d\n",a[i]);
	}
	
	for(i=0;i<7;i++){
		b[i]=i;
		asm(".word 0x0000020b");
	}
	for(i=0;i<4;i++){
		temp=a[i];
		asm(".word 0x0400028b");
		//printf("%d\n",a[i]);
	}
	for(i=0;i<4;i++){
		temp=b[i];
		asm(".word 0x0400030b");
		//printf("%d\n",b[i]);
	}
	for(i=0;i<4;i++){
		temp=c[i];
		asm(".word 0x0400038b");
		//printf("%d\n",c[i]);
	}
	for(i=0;i<4;i++){
		temp=a[i];
		asm(".word 0x0400028b");
		//printf("%d\n",a[i]);
	}
	
	asm(".word 0x2000008b");
	func(0x12,0x34,0x45);
	asm(".word 0x2000008b");
	fp(0x12,0x34,0x45);
	
	asm(".word 0x2800040b");
	func3(c,b,4);
	
	asm(".word 0x3800040b");
	func3(b,c,24);
	
	for(i=0;i<4;i++){
		temp=a[i];
		asm(".word 0x0400038b");
		//printf("%d\n",a[i]);
	}
	for(i=0;i<4;i++){
		temp=c[i];
		asm(".word 0x0400028b");
		//printf("%d\n",c[i]);
	}
	
	asm(".word 0xd000000b");
	
	printf("-------\n");
	unsigned long *debugout=debug;
	for(i=32;i<46;i++)
	printf("%x\n",debugout[i]);
	printf("-------ld taraddrs\n");
	for(i=0;i<0x7ff00;i++)
	printf("%lx -> %lx\n",i,debugout[i+100]);
	
	return 0;
}


