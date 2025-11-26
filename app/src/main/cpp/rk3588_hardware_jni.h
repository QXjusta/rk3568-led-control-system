#ifndef RK3588_HARDWARE_JNI_H
#define RK3588_HARDWARE_JNI_H

#include <jni.h>

#ifdef __cplusplus
extern "C" {
#endif

// LED控制相关JNI函数
JNIEXPORT jboolean JNICALL Java_com_example_myapplication3_RK3588HardwareService_openLEDDevice(JNIEnv *, jobject);
JNIEXPORT void JNICALL Java_com_example_myapplication3_RK3588HardwareService_closeLEDDevice(JNIEnv *, jobject);
JNIEXPORT jboolean JNICALL Java_com_example_myapplication3_RK3588HardwareService_setLEDPower(JNIEnv *, jobject, jboolean);
JNIEXPORT jboolean JNICALL Java_com_example_myapplication3_RK3588HardwareService_setLEDBrightness(JNIEnv *, jobject, jint);
JNIEXPORT jobject JNICALL Java_com_example_myapplication3_RK3588HardwareService_getLEDState(JNIEnv *, jobject);

// GPIO控制相关JNI函数
JNIEXPORT jint JNICALL Java_com_example_myapplication3_RK3588HardwareService_readGPIOState(JNIEnv *, jobject, jint);

// 硬件初始化相关JNI函数
JNIEXPORT jboolean JNICALL Java_com_example_myapplication3_RK3588HardwareService_initializeHardware(JNIEnv *, jobject);
JNIEXPORT jboolean JNICALL Java_com_example_myapplication3_RK3588HardwareService_checkDeviceNode(JNIEnv *, jobject, jstring);
JNIEXPORT jboolean JNICALL Java_com_example_myapplication3_RK3588HardwareService_checkDevicePermissions(JNIEnv *, jobject, jstring);
JNIEXPORT jboolean JNICALL Java_com_example_myapplication3_RK3588HardwareService_setDevicePermissions(JNIEnv *, jobject, jstring, jint);
JNIEXPORT jboolean JNICALL Java_com_example_myapplication3_RK3588HardwareService_checkRootPermission(JNIEnv *, jobject);

// 新增的JNI函数
JNIEXPORT jstring JNICALL Java_com_example_myapplication3_RK3588HardwareService_readLEDState(JNIEnv *, jobject);
JNIEXPORT jstring JNICALL Java_com_example_myapplication3_RK3588HardwareService_readSystemInfo(JNIEnv *, jobject);

// 串口通信相关JNI函数
JNIEXPORT jboolean JNICALL Java_com_example_myapplication3_SerialPortManager_nativeOpen(JNIEnv *, jobject, jstring, jint);
JNIEXPORT void JNICALL Java_com_example_myapplication3_SerialPortManager_nativeClose(JNIEnv *, jobject);
JNIEXPORT jint JNICALL Java_com_example_myapplication3_SerialPortManager_nativeRead(JNIEnv *, jobject, jbyteArray, jint);
JNIEXPORT jint JNICALL Java_com_example_myapplication3_SerialPortManager_nativeWrite(JNIEnv *, jobject, jbyteArray, jint);
JNIEXPORT jboolean JNICALL Java_com_example_myapplication3_SerialPortManager_nativeIsOpen(JNIEnv *, jobject);

#ifdef __cplusplus
}
#endif

#endif // RK3588_HARDWARE_JNI_H