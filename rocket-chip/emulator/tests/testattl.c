#include <stdio.h> 
#include <stdlib.h> 
#include <unistd.h> 

int main() 
{
	short rdt[1024];
	unsigned int rds[1024];
	unsigned int debug[1024];
	printf("%x,%x,%x,%x\n",rdt,rdt+1024,rds,debug);
	int i,j;
	for(i=0;i<1024;i++){
		rdt[i]=0;
	}
	rds[0]=(8<<16)+8;
	rds[1]=(8<<16)+8;
	rds[2]=(8<<16)+8;
	rds[3]=(8<<16)+8;
	rds[4]=(8<<16)+8;
	rds[5]=(9<<16)+8;
	rds[6]=(11<<16)+9;
	rds[7]=(12<<16)+11;
	rds[8]=1;
	rds[9]=2;
	rds[10]=4;
	rds[11]=3;
	rdt[0]=1;
	asm(".word 0x8000000b");
	rds[0]=1;
	asm(".word 0x4000000b");
	debug[0]=1;
	asm(".word 0x2000000b");
	int a[4];
	int b[4];
	int c[4];
	printf("a's address %x, b's address %x, c's address %x\n",a,b,c);
	int temp;
	for(j=0;j<1000;j++){
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
		printf("------------------------- %d\n",j);
		printf("%x,%x,%x\n",rdt[0x58],rdt[0x48],rdt[0x38]);
		printf("%x\n",rds[0]);
	}
	return 0;
}


