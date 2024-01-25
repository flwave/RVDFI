#include <stdio.h> 
#include <stdlib.h> 
#include <unistd.h> 


int main() 
{
    int a[5]={0,0,0,0,0};
    int i;
    for(i=0;i<5;i++)
    a[i]=i*2;
    for(i=0;i<5;i++)
    printf("%d\n",a[i]);
    return 0;
}
