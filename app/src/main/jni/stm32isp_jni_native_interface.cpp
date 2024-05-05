#include <jni.h>
#include <dlfcn.h>
#include "hal_sys_log.h"
#include "stm32isp.h"
#include "stm32isp_libdrive_meth.h"
/*
JNIEXPORT jint JNICALL Java_jni_1isp_ispOpen(JNIEnv *, jclass);
*/


driveInfo_t gDriveInfo = {0};

driveInfo_t * getDriveIns(void)
{
    return &gDriveInfo;
}
/*
JNIEXPORT jint JNICALL Java_jni_1isp_ispOpen(JNIEnv *, jclass);
*/
jint jni_ispOpen(JNIEnv * env, jclass  obj)
{
    jint res = -1 ;
    char *methodName;
    hal_sys_info("Enter jni_openIsp.");
    driveInfo_t *pDriveInfo = getDriveIns();
    pDriveInfo->libHandler = dlopen("/system/lib/libwizarposDriver.so", RTLD_LAZY);
    if(pDriveInfo->libHandler == NULL)
    {
        hal_sys_error("%s", dlerror());
        return -1;
    }
    if(NULL == (pDriveInfo->espOpen =  (drivemeth_espOpen_t)dlsym(pDriveInfo->libHandler, "esp_open")) || \
    NULL == (pDriveInfo->espClose =  (drivemeth_espClose_t)dlsym(pDriveInfo->libHandler, "esp_close")) || \
    NULL == (pDriveInfo->espSetParity =  (drivemeth_espSetParity_t)dlsym(pDriveInfo->libHandler, "esp_set_parity")) || \
    NULL == (pDriveInfo->espSetBaudrate =  (drivemeth_espSetBaudrate_t)dlsym(pDriveInfo->libHandler, "esp_set_baudrate")) || \
    NULL == (pDriveInfo->espWrite =  (drivemeth_espWrite_t)dlsym(pDriveInfo->libHandler, "esp_write")) || \
    NULL == (pDriveInfo->espRead =  (drivemeth_espRead_t)dlsym(pDriveInfo->libHandler, "esp_read")) || \
    NULL == (pDriveInfo->setStmGpio =  (drivemeth_setStmGpio_t)dlsym(pDriveInfo->libHandler, "set_stm_gpio"))  \
    )
    {
        hal_sys_error("can't find method failed, %s",dlerror());
        goto jni_ispOpen_clean;
    }


    res =  isp_open();
    hal_sys_info("Leave jni_openIsp.");
    // return res ;
    jni_ispOpen_clean:
    dlclose(pDriveInfo->libHandler);
    pDriveInfo->libHandler = NULL;
    return res;

}

/*
JNIEXPORT jint JNICALL Java_jni_1isp_ispDownload (JNIEnv *, jclass, jbyteArray, jint);
*/

jint jni_ispDownload(JNIEnv * env , jclass obj, jbyteArray blockBuf , jint blockBufSize)
{
    jint res = -1;
    hal_sys_info("Enter jni_ispDownload.");
    jbyte* _blockBuf = env->GetByteArrayElements(blockBuf, NULL);
    res = isp_download((unsigned char *)_blockBuf,blockBufSize);
    env->ReleaseByteArrayElements(blockBuf, _blockBuf, 0);
    hal_sys_info("Exit jni_ispDownload.");
    return res;
}


/*
 * Class:     jni_isp
 * Method:    ispClose
 * Signature: ()I
 */
jint  jni_ispClose(JNIEnv * env, jclass obj)
{
    jint res = -1;
    driveInfo_t *pDriveInfo = getDriveIns();
    hal_sys_info("Enter jni_ispClose.");
    res = isp_close();
    dlclose(pDriveInfo->libHandler);
    pDriveInfo->libHandler = NULL;
    hal_sys_info("Exit jni_ispClose.");
    return res;
}






