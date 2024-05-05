#ifndef _STM32ISP_LIBDRIVE_METH_H_
#define _STM32ISP_LIBDRIVE_METH_H_

typedef enum
{
    GPIO_BOOT0,
    GPIO_RESET,
    GPIO_MSM_2_STM,
    GPIO_STM_2_MSM,
    GPIO_FP_RST
}GPIO_TYPE;

typedef enum
{
    GPIO_DIRECTION_OUTPUT,
    GPIO_DIRECTION_INPUT
} GPIO_DIRECTION;



/* method list  */
typedef int (*drivemeth_espOpen_t)(const char *);
//int esp_close(int fd)
typedef int (*drivemeth_espClose_t)(int );
//int esp_set_parity(int fd, unsigned int nSel)
typedef int (*drivemeth_espSetParity_t)(int , unsigned int );
//int esp_set_baudrate(int fd, unsigned int nBaudrate);
typedef int (*drivemeth_espSetBaudrate_t)(int , unsigned int );
//int esp_write(int fd, unsigned char *pData, unsigned int nDataLength)
typedef int (*drivemeth_espWrite_t)(int , unsigned char *,unsigned int );
//int esp_read(int fd, unsigned char *pData, unsigned int nExpectLength, int nTimeout_Ms)
typedef int (*drivemeth_espRead_t)(int , unsigned char *,unsigned int ,int);
//int set_stm_gpio(GPIO_TYPE nGpio, GPIO_DIRECTION nDir, int nValue);
typedef int (*drivemeth_setStmGpio_t)(GPIO_TYPE , GPIO_DIRECTION , int );



typedef struct  _driveInfo
{
    /* data */
    drivemeth_espOpen_t espOpen;
    drivemeth_espClose_t espClose;
    drivemeth_espSetParity_t espSetParity;
    drivemeth_espSetBaudrate_t espSetBaudrate;
    drivemeth_espWrite_t espWrite;
    drivemeth_espRead_t espRead; 
    drivemeth_setStmGpio_t setStmGpio;
    void * libHandler;
}driveInfo_t;



driveInfo_t * getDriveIns(void);

#endif