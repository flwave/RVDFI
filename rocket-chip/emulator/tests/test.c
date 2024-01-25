#include "rocc.h"

static inline void accum_write(int idx, unsigned long data)
{
	ROCC_INSTRUCTION_SS(0, data, idx, 0);
}

static inline unsigned long accum_read(int idx)
{
	unsigned long value;
	ROCC_INSTRUCTION_DSS(0, value, 0, idx, 1);
	return value;
}

static inline void accum_load(int idx, void *ptr)
{
	asm volatile ("fence");
	ROCC_INSTRUCTION_SS(0, (uintptr_t) ptr, idx, 2);
}

static inline void accum_add(int idx, unsigned long addend)
{
	ROCC_INSTRUCTION_SS(0, addend, idx, 3);
}

unsigned long data = 0x3421L;

int main(void)
{
	//unsigned long value;
	//ROCC_INSTRUCTION_DSS(0, value, 12, 23, 1);
	//printf("%d\n",value);
	
	short a[4],b[4];
	int i;
	
	printf("%x,%x\n",a,b);
	
	for(i=0;i<4;i++){
		a[i]=i;
		//ROCC_INSTRUCTION_SS(0, (unsigned long)(a+1), 2412, 1);
		//printf("%x\n",a+i);
	}
	
	printf("%x\n",&i);
	for(i=0;i<4;i++){
		printf("%x\n",a+i);
	}
	
	//ROCC_INSTRUCTION_SS(0, (unsigned long)(a+1), 0, 2);
	
	ROCC_INSTRUCTION_SS(1, (unsigned long)(a+1), 2412, 1);
	ROCC_INSTRUCTION_SS(1, (unsigned long)(a+1), 2413, 1);
	ROCC_INSTRUCTION_SS(1, (unsigned long)(a+1), 2414, 1);
	ROCC_INSTRUCTION_SS(1, (unsigned long)(a+1), 2415, 1);
	
	//unsigned long value;
	//ROCC_INSTRUCTION_DSS(0, value, (unsigned long)(a+1), 23, 2);
	//printf("%d\n",value);
	
	//asm(".word 0xffffff8b");
	
	//ROCC_INSTRUCTION_SS(0, (unsigned long)(a+1), 0, 2);
	
	for(i=0;i<4;i++){
		printf("%d\n",a[i]);
	}
	return 0;
}
