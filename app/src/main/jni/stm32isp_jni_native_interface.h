#ifndef _STM32ISP_JNI_NATIVE_INTERFACE_H_
#define _STM32ISP_JNI_NATIVE_INTERFACE_H_
#include <jni.h>


/*
 * Class:     jni_isp
 * Method:    ispOpen
 * Signature: ()I
 */
jint jni_ispOpen(JNIEnv * env, jclass obj);

/*
 * Class:     jni_isp
 * Method:    ispDownload
 * Signature: ([BI)I
 */
jint jni_ispDownload(JNIEnv * env , jclass obj, jbyteArray blockBuf , jint blockBufSize);

/*
 * Class:     jni_isp
 * Method:    ispClose
 * Signature: ()I
 */
 jint  jni_ispClose(JNIEnv * env, jclass obj);



#endif