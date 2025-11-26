#include <jni.h>
#include <string>
#include <sys/stat.h>
#include <unistd.h>
#include <fcntl.h>
#include <errno.h>
#include <android/log.h>

#define TAG "DevicePermissions"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

/**
 * 检查设备节点权限
 */
extern "C" JNIEXPORT jboolean JNICALL
Java_com_example_myapplication3_RK3588HardwareService_checkDevicePermissions(JNIEnv *env, jobject thiz,
                                                                              jstring device_path) {
    (void)thiz; // 标记未使用参数
    
    const char *path = env->GetStringUTFChars(device_path, nullptr);
    if (path == nullptr) {
        return JNI_FALSE;
    }
    
    // 检查文件是否存在
    if (access(path, F_OK) != 0) {
        LOGE("设备节点不存在: %s", path);
        env->ReleaseStringUTFChars(device_path, path);
        return JNI_FALSE;
    }
    
    // 检查读权限
    if (access(path, R_OK) != 0) {
        LOGE("设备节点无读权限: %s", path);
        env->ReleaseStringUTFChars(device_path, path);
        return JNI_FALSE;
    }
    
    // 检查写权限
    if (access(path, W_OK) != 0) {
        LOGE("设备节点无写权限: %s", path);
        env->ReleaseStringUTFChars(device_path, path);
        return JNI_FALSE;
    }
    
    // 获取文件状态
    struct stat st;
    if (stat(path, &st) == 0) {
        LOGI("设备节点权限: %o, 用户: %d, 组: %d", st.st_mode & 0777, st.st_uid, st.st_gid);
    }
    
    LOGI("设备节点权限检查通过: %s", path);
    env->ReleaseStringUTFChars(device_path, path);
    return JNI_TRUE;
}

/**
 * 设置设备节点权限（需要root权限）
 */
extern "C" JNIEXPORT jboolean JNICALL
Java_com_example_myapplication3_RK3588HardwareService_setDevicePermissions(JNIEnv *env, jobject thiz,
                                                                            jstring device_path,
                                                                            jint mode) {
    (void)thiz; // 标记未使用参数
    
    const char *path = env->GetStringUTFChars(device_path, nullptr);
    if (path == nullptr) {
        return JNI_FALSE;
    }
    
    // 使用chmod设置权限
    if (chmod(path, mode) == 0) {
        LOGI("设备节点权限设置成功: %s -> %o", path, mode);
        env->ReleaseStringUTFChars(device_path, path);
        return JNI_TRUE;
    } else {
        LOGE("设备节点权限设置失败: %s, 错误: %s", path, strerror(errno));
        env->ReleaseStringUTFChars(device_path, path);
        return JNI_FALSE;
    }
}

/**
 * 检查root权限
 */
extern "C" JNIEXPORT jboolean JNICALL
Java_com_example_myapplication3_RK3588HardwareService_checkRootPermission(JNIEnv *env, jobject thiz) {
    (void)env;  // 标记未使用参数
    (void)thiz; // 标记未使用参数
    
    // 尝试访问需要root权限的系统文件
    if (access("/system/bin/su", F_OK) == 0) {
        LOGI("检测到root权限可用");
        return JNI_TRUE;
    }
    
    // 尝试执行需要root权限的命令
    FILE *pipe = popen("id", "r");
    if (pipe) {
        char buffer[128];
        if (fgets(buffer, sizeof(buffer), pipe) != nullptr) {
            if (strstr(buffer, "uid=0") != nullptr) {
                LOGI("当前具有root权限");
                pclose(pipe);
                return JNI_TRUE;
            }
        }
        pclose(pipe);
    }
    
    LOGE("无root权限");
    return JNI_FALSE;
}