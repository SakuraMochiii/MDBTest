LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)
LOCAL_MODULE := libjni_stm32isp
LOCAL_SRC_FILES += hal_sys_log.c
LOCAL_SRC_FILES += stm32isp_jni_native_interface.cpp
LOCAL_SRC_FILES += stm32isp_jni_register.cpp
LOCAL_SRC_FILES += stm32isp.cpp
LOCAL_LDLIBS    := -llog
LOCAL_LDFLAGS += -fPIC
LOCAL_CFLAGS += -fPIC
LOCAL_CFLAGS += -Wunknown-warning-option
include $(BUILD_SHARED_LIBRARY)

ifeq ($(TARGET_ARCH_ABI),armeabi)
include $(CLEAR_VARS)
include $(LOCAL_PATH)/prebuilt/Android.mk
endif



