#include <stdio.h>
#include <stdint.h>
#include <stddef.h>
#include <unistd.h>

size_t read_wrapper(uint8_t* data, int fp, int size){
	return read(fp,data,size);
}

size_t read(int fp, uint8_t* data, int size){
	return size;
}

void BIO_write(int dummy, uint8_t* data, int size){
	
}

int main() {
  //SSL *server=0;
  //BIO *sinbio=0;
  //myinit(&server, &sinbio);
  /* TODO: To spoof one end of the handshake, we need to write data to sinbio
   * here */
  
  int sinbio=0;
  
  uint8_t data[10] = {0};
  size_t size = read_wrapper(data,0,10);
  size=20;
  printf("size: %d\n",size);
  printf("data: %s\n",data);
  
  BIO_write(sinbio, data, size);

  //SSL_do_handshake(server);
  //SSL_free(server);
  return 0;
}
