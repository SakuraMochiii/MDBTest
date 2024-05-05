#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <assert.h>
#include <jni.h>
#include "hal_sys_log.h"
#include "stm32isp_jni_native_interface.h"
#define JAVA_CLASS_STRING   "com/cloudpos/jniinterface/Stm32ispInterface"


/**/

/*
 * Class:     jni_isp
 * Method:    ispOpen
 * Signature: ()I
 */
/*
 * Class:     jni_isp
 * Method:    ispDownload
 * Signature: ([BI)I
 */
/*
 * Class:     jni_isp
 * Method:    ispClose
 * Signature: ()I
 */


JNINativeMethod _nativeMethodsList[] = {
		{"ispOpen",     "()I",    (void *) jni_ispOpen},
		{"ispDownload", "([BI)I", (void *) jni_ispDownload},
		{"ispClose",    "()I",    (void *) jni_ispClose}
};


static int
_registerNativeMethods(JNIEnv *env, const char *className, JNINativeMethod *nativeMethList,jint nativeMethNum) {
	jclass clazz;
	/* find class */
	clazz = env->FindClass(className);
	if (clazz == NULL) {
		hal_sys_error("FindClass failed.");
		return JNI_ERR;
	}

	if ((env->RegisterNatives(clazz, nativeMethList,nativeMethNum)) < 0) {
		hal_sys_error("RegisterNatives Failed.");
		return JNI_ERR;
	}
	return JNI_OK;
}


JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved) {
	JNIEnv *env = NULL;
	jint nResult = -1;

	if (vm->GetEnv((void **) &env, JNI_VERSION_1_4) != JNI_OK) {
		hal_sys_error("serial number JNI_OnLoad(), failed in GetEnv()");
		return -1;
	}

	assert(env != NULL);
	if (_registerNativeMethods(env, JAVA_CLASS_STRING, _nativeMethodsList,sizeof(_nativeMethodsList)/sizeof(_nativeMethodsList[0])) != JNI_OK) {
		hal_sys_error("_registerNativeMethods failed.");
		return -1;
	}

	hal_sys_debug("JNI_OnLoad ok.");
	// if(!register_native_for_all_class(env))
	// 	return -1;

	return JNI_VERSION_1_4;
}


