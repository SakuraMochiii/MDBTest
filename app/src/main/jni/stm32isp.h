#ifndef _STM32ISP_H_
#define _STM32ISP_H_

int isp_open(void);
int isp_download(unsigned char * block,int block_size);
int isp_close(void);

#endif