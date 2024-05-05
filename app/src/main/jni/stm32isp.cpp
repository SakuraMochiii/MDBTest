#if 1
//#include <iostream>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <dirent.h>
#include <fcntl.h>
#include <unistd.h>
#include "stm32isp.h"
#include "hal_sys_log.h"
#include "stm32isp_libdrive_meth.h"

/* start */
#define STX 0x09
#define ETX 0x0D
#define MODE_REQ 					        0x00
#define MODE_RESP 					        0x01
#define CMD_GET_VERSION                     0X92
#define INBUF_SIZE                          0x120
#define OUTBUF_SIZE                         0x120
#define MAX_BLOCK_SIZE                      0x100
#define FLASH_START_ADDR                    0x08000000
/* stm32 bootloader */
#define ACK 0x79
#define NACK  0x1F
/* Global  vars */
unsigned char _inbuf[INBUF_SIZE] =      {0};
unsigned char _outbuf[OUTBUF_SIZE] =    {0};
int gFd = -1;
unsigned int gTargetAddr;

int isp_open_flag = 0; 
driveInfo_t * _pLibDrive = NULL;

/* tracer */
// WizarposBase::PlatformTrace tracer;

/*Misc functions */
unsigned char calc_lrc(unsigned char * buf,unsigned char len )
{
	unsigned char _r =0x00;
	int i;
	for(i = 0;i<len;i++)
	{
		_r  +=  buf[i];
	}
	_r = ~_r+1;
	return _r;	 	
}

int _wait_for_ack(unsigned char  * p_ack,int timeout_ms)
{
    *p_ack = 0x00;
    while(timeout_ms > 0 )
    {
        usleep(10*1000);
        timeout_ms -= 10;
        _pLibDrive->espRead(gFd,p_ack,1,10); 
        if(*p_ack != 0X00)
        {
            return 0;
        }
    }
    return -1;
}

#define addr2buf(x,buf) do{\
                            buf[0] = ((x>>24)&0xFF);\
                             buf[1] =((x>>16)&0xFF);\
                             buf[2] = ((x>>8)&0xFF); \
                             buf[3] = (x&0xFF);  } while(0)

int calc_checksum(unsigned char * data,int len,unsigned char * p_checksum)
{
    int i = 0;
    if(len < 0)
        return -1;
    if(data == NULL || p_checksum == NULL )
        return -2;
    if(len == 1)
    {
        *p_checksum = ~(*data);
        return 0;
    }
    *p_checksum = data[0];
    for(i= 1 ;i< len;i++)
    {
        *p_checksum = *p_checksum ^ data[i]; 
    }
    return 0;
}

int  get_version_cmd_frame_handler(unsigned char * _buf)
{
    int off = 0;
    unsigned char _lrc =0;
    _buf[off++] = STX;
    _buf[off++] = 3; //length
    _buf[off++] = MODE_REQ;
    _buf[off++] = CMD_GET_VERSION;
    _lrc =  calc_lrc(_buf+2,off-2);
    _buf[off++] = _lrc; 
    _buf[off++] = ETX;
    return off;
}

/* isp reset */
int isp_reset(void)
{
    int res;
    /* Reset gpio low-vol */
    _pLibDrive->setStmGpio(GPIO_RESET,GPIO_DIRECTION_OUTPUT,0);
    usleep(50);
    /* Reset gpio high-vol */
    res = _pLibDrive->setStmGpio(GPIO_RESET,GPIO_DIRECTION_OUTPUT,1);
    usleep(50*1000);
    if(res < 0)
        hal_sys_error("isp_reset failed.\n");
    return res;
}



int isp_enterBL(void)
{
    int res = -1;
    
    res = _pLibDrive->setStmGpio(GPIO_BOOT0, GPIO_DIRECTION_OUTPUT, 1);
    if(res < 0)
        hal_sys_error("isp_enterBL failed.\n");
    usleep(50);
    res = isp_reset();
    return res;
}

int isp_exitBL(void)
{
    int res = -1;
    res = _pLibDrive->setStmGpio(GPIO_BOOT0, GPIO_DIRECTION_OUTPUT, 0);
    if(res < 0)
    {
         hal_sys_error("isp_exitBL failed.\n");
         return res;
    }
    usleep(50);
    res = isp_reset();
    return res;
}
int isp_erase_flash()
{
    unsigned char _ack = 0x00;
    unsigned char check_sum;
    //send erase cmd
    _inbuf[0] = 0x43;
    _inbuf[1] = 0xBC;
    _pLibDrive->espWrite(gFd, _inbuf, 2);
    //sop->write(_inbuf,2);
    _wait_for_ack(&_ack,500);
    if(_ack != ACK)
    {
        hal_sys_error("isp_erase request refused.ack %d \n",_ack);
        return -1;
    }
    //erase all flash
    _inbuf[0] = 0xFF;
    _inbuf[1] = 0x00;
    _pLibDrive->espWrite(gFd, _inbuf, 2);
    //sop->write(_inbuf,2);
    _wait_for_ack(&_ack,500);
    if(_ack != ACK)
    {
        hal_sys_error("isp_erase ack failed.\n");
        return -2;
    }
    hal_sys_debug( "isp_erase ok.\n");
    return 0;
}

int isp_write_block(unsigned char * block,int block_size)
{
    unsigned char _ack = 0x00;
    unsigned char check_sum;
    if(block_size > MAX_BLOCK_SIZE || block_size <= 0)
    {
        hal_sys_error("isp_write_block block size %d illeagal!.\n",block_size);
        return -4;
    }
    if(gTargetAddr < FLASH_START_ADDR)
    {
        hal_sys_error("isp_write_block  target address %08x is illeagal !.\n",gTargetAddr);
        return -2;
    }
    _inbuf[0] = 0x31;
    _inbuf[1] = 0xCE;
    _pLibDrive->espWrite(gFd, _inbuf, 2);
    _wait_for_ack(&_ack,500);
    if(_ack != ACK)
    {
        hal_sys_error("isp_write_block request refused with ack %d.\n",_ack);
        return -1;
    }
    addr2buf(gTargetAddr,_inbuf);
    calc_checksum(_inbuf,4,_inbuf+4);
    _pLibDrive->espWrite(gFd,_inbuf,5);
    _wait_for_ack(&_ack,500);
    if(_ack != ACK)
    {
        hal_sys_error("isp_write_block  target address %08x is illeagal !.\n",gTargetAddr);
        return -2;
    }
    _inbuf[0] = (unsigned char)(block_size-1);
    memcpy(_inbuf+1,block,block_size);
    calc_checksum(_inbuf,block_size+1,_inbuf+block_size+1);
    _pLibDrive->espWrite(gFd,_inbuf,block_size+2);
    _wait_for_ack(&_ack,500);
    if(_ack != ACK)
    {
        hal_sys_error("isp_write_block writing failed.\n");
        return -3;
    }
    /* write done */
    gTargetAddr += block_size;
    hal_sys_dump("write block ",block,block_size);
    return 0;
}

int isp_handshake(void)
{
    unsigned char _start_byte = 0x7F;
    unsigned char _ack = 0x00;
    
    _pLibDrive->espWrite(gFd, &_start_byte,1);
    //make some delay
    _wait_for_ack(&_ack,2000);
    if(_ack  ==  ACK)
    {
        hal_sys_debug("bootloader handshake ok!\n");
        return 0;
    }
    else if(_ack  ==  NACK)
    {
        /* code */
        hal_sys_error("bootloader handshake failed!\n");
        return -1;
    }
    else
    {
        hal_sys_error("bootloader handshake timeout!\n");
        return -2;
    }
}


int isp_open(void)
{
    int res = -1;
    /* Global vars init */
    // tracer.SetAcceptor(WizarposBase::PlatformTrace::ACCEPTOR_both);
    // tracer.SetTag("stm32isp");
    gFd = -1;
    gTargetAddr = FLASH_START_ADDR;
    /* Check if open */
    if(isp_open_flag)
    {
        hal_sys_debug("isp already opened ok!\n");
        return 0;
    }
    _pLibDrive = getDriveIns();
    if(_pLibDrive == NULL  || _pLibDrive->libHandler ==  NULL)
    {
        hal_sys_error("SERIAL_EXT open failed.\n");
        return -1;
    }
    /* 1. open serial */
    gFd = _pLibDrive->espOpen("SERIAL_EXT");
    if( gFd < 0)
    {
        hal_sys_error("SERIAL_EXT open failed.\n");
        exit(0);
    }
    else
    {
        hal_sys_debug( "SERIAL_EXT open success.\n");
    }
    if(_pLibDrive->espSetBaudrate(gFd, 115200) < 0)
    {
        hal_sys_error("Set baudrate failed.\n");
        exit(0);        
    }
    /*
    * unsigned int nSel : 0 : no parity
    *                     1 : odd parity
    *                     2 : even parity
    */
    if(_pLibDrive->espSetParity(gFd,2) < 0)
    {
        hal_sys_error("Set baudrate failed.\n");
        goto _close;
    }

    /* 2.enter bootloader */
    if((res = isp_enterBL())<0)
        goto _close;
    /* 3. handshake */
    if((res = isp_handshake())<0)
        goto _close;
    usleep(10*1000);
    hal_sys_debug( "We make som delay.\n");
    /* 4. erase flash */
    if((res = isp_erase_flash())<0)
        goto _close;
    isp_open_flag = 1;
    res = 0;
    hal_sys_debug( "_jni_interface_isp_open ok.\n");
    goto _exit;
_close:
    if(gFd > 0)
        _pLibDrive->espClose(gFd);
_exit:
    return res;
}

int isp_download(unsigned char * block,int block_size)
{
    int res = -1; 
    if(isp_open_flag != 1)
    {
        res = -1;
        return res;
    }
    return isp_write_block(block,block_size);
}

int  isp_close(void)
{
    /*
    if(isp_open_flag != 1)
        return ;
        */
    /*exit bootloader */
    hal_sys_info("serial fd %d.",gFd);
    isp_exitBL();
    hal_sys_info("serial fd %d.",gFd);
    if(gFd >= 0)
    {
        if(_pLibDrive->espClose(gFd)<0)
        {
            hal_sys_error("esp close failed.");
            return -1;
        }
        else
            hal_sys_info("esp close ok.");
    }
    hal_sys_info("stm32 isp close ok.");
    gTargetAddr = FLASH_START_ADDR;
    isp_open_flag = 0;
    return 0;
}


#if 0
int main(int argc, char *argv[])
{
    _jni_interface_isp_open();
    _jni_interface_isp_download(_inbuf,100);
    _jni_interface_isp_close();
    return 0;
}



int main(int argc, char *argv[])
{
    int len;

	tracer.SetAcceptor(WizarposBase::PlatformTrace::ACCEPTOR_both);
    tracer.SetTag("stm32isp");
    int gFd = esp_open("SERIAL_EXT");
    if( gFd < 0)
    {
        hal_sys_error("SERIAL_EXT open failed.\n");
        return -1;
    }
    else
    {
        hal_sys_debug( "SERIAL_EXT open success.\n");
    }
    if(esp_set_baudrate(gFd, 115200) < 0)
    {
        hal_sys_error("Set baudrate failed.\n");
        return -1;
    }

    /* Test handshake */
    if(isp_enterBL()<0)
        goto close_exit;
    
    if(isp_handshake()<0)
        goto close_exit;
    if(isp_erase_flash()<0)
        goto close_exit;
    isp_exitBL();
    
    #if 0
    len = get_version_cmd_frame_handler(_inbuf);
    _pLibDrive->espWrite(gFd, _inbuf, len);
    if(esp_read(gFd,_outbuf,8,1000)!= 8)
    {
        hal_sys_error("Read failed.\n");
    }
    hal_sys_debug("recv",_outbuf,8);
    #endif
close_exit:
    esp_close(gFd);
    return 0;
}
#endif


#else 
#include <stdio.h>
#include <stdlib.h>
int isp_open(void)
{
    return 0;
}
int isp_download(unsigned char * block,int block_size)
{
    return 0;
}
int  isp_close(void)
{
    return 0;
}
#endif

/*
cp /mnt/e/gitProjects/q3a7/out/target/product/msm8909/system/bin/stm32isp /mnt/c/Users/xuyin/Downloads/
cp /mnt/e/gitProjects/q3a7/out/target/product/msm8909/obj/lib/libstm32isp.so /mnt/c/Users/xuyin/Downloads/
*/