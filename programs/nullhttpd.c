#include <stdio.h>
#include <stdlib.h>
#include <sys/mman.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>

#define MAX_POSTSIZE 4096

struct test_conn{
	struct test_data *dat;
	char* PostData;
};

struct test_data{
	char in_RequestMethod[5];
	int in_ContentLength;
};

struct test_conn *conn;

void ReadPOSTData(int sid);

int read_header(int sid)
{
	if (strcmp(conn[sid].dat->in_RequestMethod, "POST")==0) {
		if (conn[sid].dat->in_ContentLength<MAX_POSTSIZE) {
			ReadPOSTData(sid);
		} else {
			
		}
	}
	return 0;
}

void ReadPOSTData(int sid) {
	char *pPostData;
	int rc=0;
	int x=0;
	char *socket_data=(char*)malloc(4096*sizeof(char));
	char *dummy_data;
	int i;
	for(i=0;i<4096;i++)
	socket_data[i]=i;

	conn[sid].PostData=calloc(conn[sid].dat->in_ContentLength+1024, sizeof(char));
	dummy_data=calloc(1024, sizeof(char));
	printf("postdata addr %x, socket_data addr %x, dummy data addr %x\n",conn[sid].PostData,socket_data,dummy_data);
	pPostData=conn[sid].PostData;
	/* reading beyond PostContentLength is required for IE5.5 and NS6 (HTTP 1.1) */
	memcpy(pPostData,socket_data,1024);
	conn[sid].PostData[conn[sid].dat->in_ContentLength]='\0';
	for(i=0;i<10;i++){
		printf("%d,%d\n",i,(unsigned int)dummy_data[i]);
	}
}

int main(int argc, char *argv[])
{
	conn=(struct test_conn *)malloc(sizeof(struct test_conn));
	conn[0].dat=(struct test_data *)malloc(sizeof(struct test_data));
	conn[0].dat->in_RequestMethod[0]='P';
	conn[0].dat->in_RequestMethod[1]='O';
	conn[0].dat->in_RequestMethod[2]='S';
	conn[0].dat->in_RequestMethod[3]='T';
	conn[0].dat->in_RequestMethod[4]='\0';
	conn[0].dat->in_ContentLength=-1024;
	read_header(0);
	return 0;
}

