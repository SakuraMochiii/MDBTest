package com.cloudpos.jniinterface;

public class Stm32ispInterface {
    static {
        String fileName = "jni_stm32isp";
        JNILoad.jniLoad(fileName);
    }


    public static native int ispOpen();

    public static native int ispDownload(byte[] blockBuffer, int blockSize);

    public static native int ispClose();
}
