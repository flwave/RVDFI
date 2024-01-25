#include <stdio.h> 
#include <stdlib.h> 
#include <unistd.h> 

void func3(unsigned int* a,unsigned int* b,unsigned int c){
}

void func2(){
	
}

void func(int a, int b, int c){
	//asm(".word 0x0000008b");
	//unsigned long *sa=0x800200f0;
	//int i;
	//for(i=0;i<4;i++)
	//printf("in func, stack address value: %lx\n",*(sa-i));
	func2();
}

int main() 
{
	int a[4];
	int b[4];
	int c[4];
	printf("a's address %x, b's address %x, c's address %x\n",a,b,c);
	int i,j;
	for(j=0;j<1000;j++){
		for(i=0;i<4;i++)
		a[i]=0;
		for(i=0;i<4;i++)
		b[i]=0;
		for(i=0;i<4;i++)
			c[i]=0;
		
		for(i=0;i<7;i++)
		b[i]=i;
		
		for(i=0;i<4;i++)
		printf("%d\n",a[i]);
		for(i=0;i<4;i++)
		printf("%d\n",b[i]);
		for(i=0;i<4;i++)
		printf("%d\n",c[i]);
	}
	void (*fp)(int,int,int);
	fp=func;
	func(0x12,0x34,0x45);
	fp(0x12,0x34,0x45);
	//memcpy(a,b,3);
    return 0;
}


