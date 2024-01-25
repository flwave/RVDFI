#include <stdio.h>
#include <iostream>
#include <unistd.h>
#include <cstring>
#include <iomanip>
#include <stdlib.h>
#include <sys/mman.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <pthread.h> 

void **dfi_reg;
unsigned short *dfi_reg_s;
unsigned int *dfi_reg_t;//for testing cycles
unsigned int *dfi_reg_0;
unsigned int *dfi_reg_1;
unsigned int *dfi_reg_2;
unsigned int *dfi_reg_3;
unsigned int *dfi_reg_4;
unsigned int *dfi_reg_5;
unsigned int *dfi_reg_6;
unsigned int *dfi_reg_7;
unsigned int *dfi_p;
int dfi_start;
int dfi_vio;
volatile unsigned int dfi_reg_signal;

void *vmem;

volatile unsigned int dfi_user_start;

#define DFI_LATENCY_CHECK 0

void dfi_func_signal(){
	dfi_reg_3[2]=22332233;
}

void init_roccinstr_dfi(){
	std::cout<<"init regions"<<std::endl;
	int fp=open("/dev/mem",O_RDWR|O_SYNC);
	if(fp<0){
		std::cout<<"cannot open /dev/mem"<<std::endl;
		exit(0);
	}
	vmem=mmap(NULL,0x6000000,PROT_READ|PROT_WRITE,MAP_SHARED,fp,0xf4084000);
	if(vmem==NULL){
		std::cout<<"cannot map physical address"<<std::endl;
		exit(0);
	}
	
	int i;
	dfi_reg=(void**)malloc(8*sizeof(void*));
	dfi_reg[0]=vmem;
	dfi_reg[1]=(void*)(((char*)vmem)+0x100000*4);
	dfi_reg[2]=(void*)(((char*)vmem)+0x100000*(64+4));
	/*dfi_reg[0]=(void*)malloc(1024*1024*sizeof(unsigned int));//for trace
	dfi_reg[1]=(void*)malloc(1024*1024*64);//for reaching definition table
	dfi_reg[2]=(void*)malloc(1024*1024*4*sizeof(unsigned int));//for reaching definition set
	dfi_reg[3]=(void*)malloc(1024*sizeof(unsigned int));//for info
	dfi_reg[4]=(void*)malloc(1024*sizeof(unsigned int));//for output*/
	dfi_reg_0=(unsigned int*)(dfi_reg[0]);
	dfi_reg_1=(unsigned int*)(dfi_reg[1]);
	dfi_reg_2=(unsigned int*)(dfi_reg[2]);
	dfi_reg_3=(unsigned int*)(dfi_reg[3]);
	dfi_reg_4=(unsigned int*)(dfi_reg[4]);
	dfi_reg_s=(unsigned short*)(dfi_reg[1]);
	
	memset(vmem,0,0x6000000);
	
	std::cout<<std::hex<<"regions init done"<<std::endl;
	
	FILE *fptr;
	char ch;
    fptr = fopen("dfi_rds_file", "r");
	if (fptr == NULL){
		printf("Cannot open reachine definition set file\n");
		exit(0);
	}
	i=0;
	unsigned int max_file_p=0;
	ch = fgetc(fptr);
	while (i<4){
		((char*)dfi_reg[2])[i]=ch;
		ch = fgetc(fptr);
		i++;
	}
	max_file_p=((unsigned int*)dfi_reg[2])[0];
	max_file_p=2*max_file_p;
    while (i<max_file_p){
        ((char*)dfi_reg[2])[i]=ch;
        ch = fgetc(fptr);
        i++;
    }
    max_file_p=2*(((unsigned int*)dfi_reg[2])[i/4-1]);
    while (i<max_file_p){
        ((char*)dfi_reg[2])[i]=ch;
        ch = fgetc(fptr);
        i++;
    }
    fclose(fptr);
	
	std::cout<<std::hex<<"rds load done, total "<<i<<" bytes"<<std::endl;
	
	dfi_reg_1[0]=12345678;//rdt
	asm(".word 0x4000000b");
	dfi_reg_2[0]=23456789;//rds
	asm(".word 0x8000000b");
	((unsigned long*)dfi_reg_0)[0]=DFI_MAX_CALLCOUNT;
	for(i=0;i<100000;i++);
	#if DFI_CONFIG
	asm(".word 0xe000000b");
	#else
	asm(".word 0xc000000b");
	#endif
	std::cout<<std::hex<<"max call count "<<((unsigned long*)dfi_reg_0)[0]<<std::endl;
	std::cout<<std::hex<<"pointers indication done"<<std::endl;
	
	/*
	dfi_reg_3[2]=22332233;
	dfi_reg_3[2]=DFI_MAX_PC;
	dfi_reg_3[2]=DFI_TEST_MODE;
	dfi_reg_3[2]=DFI_TEST_COUNT;
	*/
	std::cout<<std::hex<<"reg rdt vaddr "<<(unsigned long)(dfi_reg_1)<<" to "<<(unsigned long)(&(((char *)dfi_reg[1])[1024*1024*64-1]))<<std::endl;
	std::cout<<std::hex<<"reg rds vaddr "<<(unsigned long)(dfi_reg_2)<<" to "<<(unsigned long)(&(((char *)dfi_reg[2])[1024*1024*4*sizeof(unsigned int)-1]))<<std::endl;
	std::cout<<std::hex<<"debug buffer vaddr "<<(unsigned long)(dfi_reg_0)<<" to "<<(unsigned long)(&(((char *)dfi_reg[0])[1024*1024*sizeof(unsigned int)-1]))<<std::endl;
	std::cout<<"init dfi end"<<std::endl;
}

void init_noinstrument_dfi(){
	std::cout<<"init regions"<<std::endl;
	int i;
	dfi_reg=(void**)malloc(8*sizeof(void*));
	dfi_reg[0]=(void*)malloc(1024*1024*sizeof(unsigned int));//for trace
	dfi_reg[1]=(void*)malloc(1024*1024*64);//for reaching definition table
	dfi_reg[2]=(void*)malloc(1024*1024*4*sizeof(unsigned int));//for reaching definition set
	dfi_reg[3]=(void*)malloc(1024*sizeof(unsigned int));//for info
	dfi_reg[4]=(void*)malloc(1024*sizeof(unsigned int));//for output
	dfi_reg_0=(unsigned int*)(dfi_reg[0]);
	dfi_reg_1=(unsigned int*)(dfi_reg[1]);
	dfi_reg_2=(unsigned int*)(dfi_reg[2]);
	dfi_reg_3=(unsigned int*)(dfi_reg[3]);
	dfi_reg_4=(unsigned int*)(dfi_reg[4]);
	dfi_reg_s=(unsigned short*)(dfi_reg[1]);
	
	FILE *fptr;
	char ch;
    fptr = fopen("dfi_rds_file", "r");
	if (fptr == NULL){
		printf("Cannot open reachine definition set file\n");
		exit(0);
	}
	i=0;
	unsigned int max_file_p=0;
	ch = fgetc(fptr);
	while (i<4){
		((char*)dfi_reg[2])[i]=ch;
		ch = fgetc(fptr);
		i++;
	}
	max_file_p=((unsigned int*)dfi_reg[2])[0];
	max_file_p=4*(max_file_p&0xfffff);
    while (i<max_file_p){
        ((char*)dfi_reg[2])[i]=ch;
        ch = fgetc(fptr);
        i++;
    }
    fclose(fptr);
	
	dfi_reg_1[0]=12345678;//rdt
	dfi_reg_1[0]=12345678;//rdt
	//dfi_reg_signal=12312312;
	dfi_reg_2[0]=23456789;//rds
	dfi_reg_2[0]=23456789;//rds
	
	dfi_reg_3[2]=22332233;
	dfi_reg_3[2]=22332233;
	dfi_reg_3[2]=DFI_MAX_PC;
	dfi_reg_3[2]=DFI_MAX_PC;
	dfi_reg_3[2]=DFI_TEST_MODE;
	dfi_reg_3[2]=DFI_TEST_MODE;
	dfi_reg_3[2]=DFI_TEST_COUNT;
	dfi_reg_3[2]=DFI_TEST_COUNT;
	//std::cout<<std::hex<<"reg signal vaddr "<<(unsigned int)(&dfi_reg_signal)<<std::endl;
	std::cout<<std::hex<<"reg rdt vaddr "<<(unsigned long)dfi_reg_1<<std::endl;
	std::cout<<std::hex<<"reg rds vaddr "<<(unsigned long)(dfi_reg_2)<<std::endl;
	std::cout<<"init dfi end"<<std::endl;
}

void init_soft_dfi(){
	std::cout<<"init soft dfi"<<std::endl;
	int fp=open("/dev/mem",O_RDWR|O_SYNC);
	if(fp<0){
		std::cout<<"cannot open /dev/mem"<<std::endl;
		exit(0);
	}
	vmem=mmap(NULL,0x6000000,PROT_READ|PROT_WRITE,MAP_SHARED,fp,0xf4084000);
	if(vmem==NULL){
		std::cout<<"cannot map physical address"<<std::endl;
		exit(0);
	}
	
	//dfi_reg=(void**)malloc(8*sizeof(void*));
	unsigned long i,j;
	std::cout<<"init ready to allocate registers"<<std::endl;
	//dfi_reg[0]=(void *)malloc(128*1024*1024*2);
	//dfi_reg_s=(unsigned short*)(dfi_reg[0]);
	dfi_reg_s=(unsigned short*)vmem;
	dfi_vio=0;
	asm(".word 0x4000000b");
	asm(".word 0x8000000b");
	((unsigned long*)dfi_reg_s)[0]=DFI_MAX_CALLCOUNT;
	for(i=0;i<100000;i++);
	#if DFI_CONFIG
	asm(".word 0xe000000b");
	#else
	asm(".word 0xc000000b");
	#endif
	printf("%x\n",(unsigned long)__builtin_frame_address(0));
	std::cout<<std::hex<<"max call count "<<((unsigned long*)dfi_reg_s)[0]<<std::endl;
	std::cout<<"init dfi end"<<std::endl;
}

void init_ori_dfi(){
	std::cout<<"init regions"<<std::endl;
	int fp=open("/dev/mem",O_RDWR|O_SYNC);
	if(fp<0){
		std::cout<<"cannot open /dev/mem"<<std::endl;
		exit(0);
	}
	vmem=mmap(NULL,0x6000000,PROT_READ|PROT_WRITE,MAP_SHARED,fp,0xf4084000);
	if(vmem==NULL){
		std::cout<<"cannot map physical address"<<std::endl;
		exit(0);
	}
	
	int i;
	dfi_reg=(void**)malloc(8*sizeof(void*));
	dfi_reg[0]=vmem;
	dfi_reg[1]=(void*)(((char*)vmem)+0x100000*4);
	dfi_reg[2]=(void*)(((char*)vmem)+0x100000*(64+4));
	dfi_reg_0=(unsigned int*)(dfi_reg[0]);
	dfi_reg_1=(unsigned int*)(dfi_reg[1]);
	dfi_reg_2=(unsigned int*)(dfi_reg[2]);
	dfi_reg_3=(unsigned int*)(dfi_reg[3]);
	dfi_reg_4=(unsigned int*)(dfi_reg[4]);
	
	std::cout<<std::hex<<"regions init done"<<std::endl;
	
	dfi_reg_1[0]=12345678;//rdt
	asm(".word 0x4000000b");
	dfi_reg_2[0]=23456789;//rds
	asm(".word 0x8000000b");
	((unsigned long*)dfi_reg_0)[0]=DFI_MAX_CALLCOUNT;
	for(i=0;i<100000;i++);
	#if DFI_CONFIG
	asm(".word 0xe000000b");
	#else
	asm(".word 0xc000000b");
	#endif
	std::cout<<std::hex<<"max call count "<<((unsigned long*)dfi_reg_0)[0]<<std::endl;
	
	std::cout<<std::hex<<"pointers indication done"<<std::endl;
	
	std::cout<<std::hex<<"reg rdt vaddr "<<(unsigned long)(dfi_reg_1)<<" to "<<(unsigned long)(&(((char *)dfi_reg[1])[1024*1024*64-1]))<<std::endl;
	std::cout<<std::hex<<"reg rds vaddr "<<(unsigned long)(dfi_reg_2)<<" to "<<(unsigned long)(&(((char *)dfi_reg[2])[1024*1024*4*sizeof(unsigned int)-1]))<<std::endl;
	std::cout<<std::hex<<"debug buffer vaddr "<<(unsigned long)(dfi_reg_0)<<" to "<<(unsigned long)(&(((char *)dfi_reg[0])[1024*1024*sizeof(unsigned int)-1]))<<std::endl;
	std::cout<<"init dfi end"<<std::endl;
}

void dfi_rocc_debug(){
	printf("DEBUG: rocc debug begin\n");
	asm(".word 0xd000000b");
	int i;
	for(i=0;i<100000;i++);
	/*
    for(i=0;i<4*300;i++){
		if(i%4==0)
		printf("+++ %d\n",i/4);
		printf("%x\n",dfi_reg_0[i]);
	}
	for(i=4*150000;i<4*150100;i++){
		if(i%4==0)
		printf("+++ %d\n",i/4);
		printf("%x\n",dfi_reg_0[i]);
	}
	*/
	unsigned long *debug;
	debug=(unsigned long*)dfi_reg_0;
	printf("-------\n");
	for(i=0;i<60;i++)
	printf("%lx -> %lx\n",i,debug[i]);
	exit(0);
	/*
	printf(">>>>>>\n");
	for(i=0;i<0x200/2;i++){
		printf("%x -> %x\n",i,dfi_reg_1[i]);
	}*/
	/*
	printf("-------ld taraddrs\n");
	for(i=0;i<0x7ff00;i++)
	printf("%lx -> %lx\n",i,debug[i+100]);
	*/
}

void dfi_debug(){
	std::cout<<"DFI DEBUG INFO"<<std::endl;
	//dfi_pim->wait_for_completion();
	for(int i=0;i<100000;i++)
	for(int j=0;j<500;j++);
	for(int i=0;i<100000;i++);
	for(int i=0;i<100000;i++);
	std::cout<<"trace---------"<<std::endl;
	unsigned int prev_id,prev_addr,dfi_p,addr,id;
	dfi_p=0;
	prev_id=0;
	prev_addr=0;
	for(int i=0;i<20;i++){
		std::cout<<std::hex<<"t "<<dfi_reg_0[i]<<std::endl;
	}
	for(int i=0;i<40000;i++){
		//std::cout<<std::hex<<dfi_reg_0[i]<<std::endl;
		std::cout<<std::dec<<dfi_p<<": ";
		if((dfi_reg_0[dfi_p]>>30)&1){
			for(int j=0;j<2;j++){
				std::cout<<std::hex<<"c "<<dfi_reg_0[dfi_p];
				if(dfi_reg_0[dfi_p]>>(14+15*j)&0x1){
					std::cout<<" read ";
				}else{
					std::cout<<" write ";
				}
				if((dfi_reg_0[dfi_p]>>(13+15*j))&1){
					id=prev_id-((dfi_reg_0[dfi_p]>>(8+15*j))&0x1f);
				}else{
					id=prev_id+((dfi_reg_0[dfi_p]>>(8+15*j))&0x1f);
				}
				std::cout<<std::dec<<"id: "<<id;
				prev_id=id;
				if((dfi_reg_0[dfi_p]>>(7+15*j))&1){
					addr=prev_addr-(((dfi_reg_0[dfi_p]>>(3+15*j))&0xf)<<(((dfi_reg_0[dfi_p]>>(15*j))&0x7)*4));
				}else{
					addr=prev_addr+(((dfi_reg_0[dfi_p]>>(3+15*j))&0xf)<<(((dfi_reg_0[dfi_p]>>(15*j))&0x7)*4));
				}
				std::cout<<std::hex<<" addr: "<<(addr<<2);
				prev_addr=addr;
				std::cout<<std::dec<<" last write: "<<((short*)dfi_reg[1])[addr&0x1ffffff]<<std::endl;
			}
			dfi_p++;
		}else if(((dfi_reg_0[dfi_p]>>29)&3)==0){
			std::cout<<std::hex<<"n "<<dfi_reg_0[dfi_p];
			if((dfi_reg_0[dfi_p]>>16)&1){
				std::cout<<" read ";
			}else{
				std::cout<<" write ";
			}
			std::cout<<std::dec<<"id: "<<(dfi_reg_0[dfi_p]&0xffff);
			std::cout<<std::hex<<" addr: "<<((dfi_reg_0[dfi_p+1]&0x7fffffff)<<2);
			std::cout<<std::dec<<" last write: "<<((short*)dfi_reg[1])[dfi_reg_0[dfi_p+1]&0x1ffffff]<<std::endl;
			prev_id=(dfi_reg_0[dfi_p]&0xffff);
			prev_addr=dfi_reg_0[dfi_p+1];
			dfi_p+=2;
		}else{
			std::cout<<std::hex<<"l ";
			unsigned int dfi_lib_p=0;
			unsigned long dfi_lib_len=0;
			std::cout<<std::dec<<"id: "<<(dfi_reg_0[dfi_p]&0xffff);
			dfi_lib_p++;
			if((dfi_reg_0[dfi_p]>>18)&1){
				std::cout<<std::hex<<" read addr: "<<((dfi_reg_0[dfi_p+dfi_lib_p]&0x7fffffff)<<2);
				std::cout<<std::dec<<" last write: "<<((short*)dfi_reg[1])[dfi_reg_0[dfi_p+dfi_lib_p]&0x1ffffff];
				dfi_lib_p++;
			}
			if((dfi_reg_0[dfi_p]>>19)&1){
				std::cout<<std::hex<<" write addr: "<<((dfi_reg_0[dfi_p+dfi_lib_p]&0x7fffffff)<<2);
				std::cout<<std::dec<<" last write: "<<((short*)dfi_reg[1])[dfi_reg_0[dfi_p+dfi_lib_p]&0x1ffffff];
				dfi_lib_p++;
			}
			if(((dfi_reg_0[dfi_p]>>16)&3)==1){
				dfi_lib_len=(dfi_reg_0[dfi_p+dfi_lib_p]&0x7fffffff);
				dfi_lib_p+=1;
			}else if(((dfi_reg_0[dfi_p]>>16)&3)==2){
				dfi_lib_len=(dfi_reg_0[dfi_p+dfi_lib_p]&0x7fffffff)|((dfi_reg_0[dfi_p+dfi_lib_p+1]&1)<<31);
				dfi_lib_len|=((dfi_reg_0[dfi_p+dfi_lib_p+1]&0x7fffffff)>>1)<<32;
				dfi_lib_p+=2;
			}else{
				dfi_lib_len=(dfi_reg_0[dfi_p+dfi_lib_p]&0x7fffffff)|((dfi_reg_0[dfi_p+dfi_lib_p+1]&1)<<31);
				dfi_lib_len|=(((dfi_reg_0[dfi_p+dfi_lib_p+1]&0x7fffffff)>>1)|((dfi_reg_0[dfi_p+dfi_lib_p+2]&3)<<30))<<32;
				dfi_lib_p+=3;
			}
			std::cout<<std::hex<<" length: "<<dfi_lib_len<<std::endl;
			dfi_p+=dfi_lib_p;
		}
	}
	
	std::cout<<"rds---------"<<std::endl;
	int rdt_len=((int*)dfi_reg[2])[0]&0xffff;
	for(int i=0;i<rdt_len;i++){
		std::cout<<i<<": ";
		for(int j=((int*)dfi_reg[2])[i]&0xffff;j<(((int*)dfi_reg[2])[i]>>16);j++){
			std::cout<<((int*)dfi_reg[2])[j]<<" ";
		}
		std::cout<<std::endl;
	}
	std::cout<<"reg 4---------"<<std::endl;
	for(int i=0;i<25;i++){
		std::cout<<std::dec<<i<<": "<<dfi_reg_4[i]<<std::endl;
	}
	
}

void dfi_rocc_stlddebug(unsigned int info){
	std::cout<<"rocc: now processing "<<std::hex<<info<<std::endl;
	int i;
	for(i=0;i<100000;i++);
}
