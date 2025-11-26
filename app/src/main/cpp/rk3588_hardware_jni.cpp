#include <jni.h>
#include <string>
#include <android/log.h>
#include <fcntl.h>
#include <unistd.h>
#include <sys/ioctl.h>
#include <errno.h>
#include <cstring>

#ifdef __cplusplus
extern "C" {
#endif

#define TAG "RK3588HardwareJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

// 设备节点路径
#define LED_DEVICE_NODE "/dev/led"
#define GPIO_DEVICE_NODE "/dev/gpio"
#define SERIAL_DEVICE_NODE "/dev/ttyS4"

// LED控制命令
#define LED_SET_POWER _IOW('L', 1, int)
#define LED_SET_BRIGHTNESS _IOW('L', 2, int)
#define LED_GET_STATE _IOR('L', 4, struct led_state)

// LED状态结构体
struct led_state {
    int power_on;
    int brightness;
    char mode[32];
};

// 全局文件描述符
static int led_fd = -1;
static int gpio_fd = -1;
static int serial_fd = -1;

// ========== LED控制JNI实现 ==========

/**
 * 打开LED设备
 */
JNIEXPORT jboolean JNICALL
Java_com_example_myapplication3_RK3588HardwareService_openLEDDevice(JNIEnv *env, jobject thiz) {
    (void)env;  // 标记未使用参数
    (void)thiz; // 标记未使用参数
    
    if (led_fd >= 0) {
        LOGI("LED设备已经打开");
        return JNI_TRUE;
    }
    
    led_fd = open(LED_DEVICE_NODE, O_RDWR);
    if (led_fd < 0) {
        LOGI("LED设备节点不存在或无法访问: %s (%s)", LED_DEVICE_NODE, strerror(errno));
        // 模拟成功打开，让应用能够继续运行
        led_fd = -2; // 使用特殊值表示模拟模式
        LOGI("LED设备模拟模式启用");
        return JNI_TRUE;
    }
    
    LOGI("LED设备打开成功");
    return JNI_TRUE;
}

/**
 * 关闭LED设备
 */
JNIEXPORT void JNICALL
Java_com_example_myapplication3_RK3588HardwareService_closeLEDDevice(JNIEnv *env, jobject thiz) {
    (void)env;  // 标记未使用参数
    (void)thiz; // 标记未使用参数
    
    if (led_fd >= 0) {
        if (led_fd != -2) {
            close(led_fd);
        }
        led_fd = -1;
        LOGI("LED设备已关闭");
    }
}

/**
 * 设置LED电源状态
 */
JNIEXPORT jboolean JNICALL
Java_com_example_myapplication3_RK3588HardwareService_setLEDPower(JNIEnv *env, jobject thiz,
                                                                    jboolean power_on) {
    (void)env;  // 标记未使用参数
    (void)thiz; // 标记未使用参数
    
    if (led_fd < 0) {
        if (led_fd == -2) {
            // 模拟模式
            LOGI("模拟设置LED电源状态: %s", power_on ? "ON" : "OFF");
            return JNI_TRUE;
        }
        LOGE("LED设备未打开");
        return JNI_FALSE;
    }
    
    int power = power_on ? 1 : 0;
    int ret = ioctl(led_fd, LED_SET_POWER, &power);
    if (ret < 0) {
        LOGE("设置LED电源状态失败: %s", strerror(errno));
        return JNI_FALSE;
    }
    
    LOGI("设置LED电源状态: %s", power_on ? "ON" : "OFF");
    return JNI_TRUE;
}

/**
 * 设置LED亮度
 */
JNIEXPORT jboolean JNICALL
Java_com_example_myapplication3_RK3588HardwareService_setLEDBrightness(JNIEnv *env, jobject thiz,
                                                                       jint brightness) {
    (void)env;  // 标记未使用参数
    (void)thiz; // 标记未使用参数
    
    if (led_fd < 0) {
        if (led_fd == -2) {
            // 模拟模式
            LOGI("模拟设置LED亮度: %d", brightness);
            return JNI_TRUE;
        }
        LOGE("LED设备未打开");
        return JNI_FALSE;
    }
    
    if (brightness < 0 || brightness > 100) {
        LOGE("亮度值无效: %d", brightness);
        return JNI_FALSE;
    }
    
    int ret = ioctl(led_fd, LED_SET_BRIGHTNESS, &brightness);
    if (ret < 0) {
        LOGE("设置LED亮度失败: %s", strerror(errno));
        return JNI_FALSE;
    }
    
    LOGI("设置LED亮度: %d", brightness);
    return JNI_TRUE;
}



/**
 * 获取LED状态
 */
JNIEXPORT jobject JNICALL
Java_com_example_myapplication3_RK3588HardwareService_getLEDState(JNIEnv *env, jobject thiz) {
    (void)thiz; // 标记未使用参数
    
    // 获取LEDState类的引用
    jclass ledStateClass = env->FindClass("com/example/myapplication3/RK3588HardwareService$LEDState");
    if (ledStateClass == nullptr) {
        LOGE("找不到LEDState类");
        return nullptr;
    }
    
    // 获取LEDState类的构造函数
    jmethodID constructor = env->GetMethodID(ledStateClass, "<init>", "()V");
    if (constructor == nullptr) {
        LOGE("找不到LEDState构造函数");
        return nullptr;
    }
    
    // 创建LEDState对象
    jobject ledState = env->NewObject(ledStateClass, constructor);
    if (ledState == nullptr) {
        LOGE("创建LEDState对象失败");
        return nullptr;
    }
    
    // 获取字段ID
    jfieldID powerOnField = env->GetFieldID(ledStateClass, "powerOn", "Z");
    jfieldID brightnessField = env->GetFieldID(ledStateClass, "brightness", "I");
    jfieldID modeField = env->GetFieldID(ledStateClass, "mode", "Ljava/lang/String;");
    jfieldID workBrightnessField = env->GetFieldID(ledStateClass, "workBrightness", "I");
    jfieldID mmc2BrightnessField = env->GetFieldID(ledStateClass, "mmc2Brightness", "I");
    jfieldID workFoundField = env->GetFieldID(ledStateClass, "workFound", "Z");
    jfieldID mmc2FoundField = env->GetFieldID(ledStateClass, "mmc2Found", "Z");
    
    if (powerOnField == nullptr || brightnessField == nullptr || 
        modeField == nullptr ||
        workBrightnessField == nullptr || mmc2BrightnessField == nullptr ||
        workFoundField == nullptr || mmc2FoundField == nullptr) {
        LOGE("获取LEDState字段ID失败");
        return nullptr;
    }
    
    // 检查两个LED设备：work和mmc2::
    const char* led_devices[] = {"work", "mmc2::"};
    int num_devices = 2;
    
    int work_brightness = 0;
    int mmc2_brightness = 0;
    bool work_found = false;
    bool mmc2_found = false;
    bool power_on = false;
    const char* work_device_mode = "unknown";
    const char* mmc2_device_mode = "unknown";
    const char* device_mode = "unknown";
    bool device_found = false;
    
    for (int i = 0; i < num_devices; i++) {
        const char* led_name = led_devices[i];
        char brightness_path[256];
        char trigger_path[256];
        
        snprintf(brightness_path, sizeof(brightness_path), "/sys/class/leds/%s/brightness", led_name);
        snprintf(trigger_path, sizeof(trigger_path), "/sys/class/leds/%s/trigger", led_name);
        
        int brightness_value = 0;
        bool current_device_found = false;
        const char* current_device_mode = "unknown";
        
        // 检查LED设备是否存在
        FILE* brightness_file = fopen(brightness_path, "r");
        if (brightness_file != NULL) {
            // 读取亮度值
            if (fscanf(brightness_file, "%d", &brightness_value) == 1) {
                current_device_found = true;
                device_found = true;
                
                // 读取触发模式
                FILE* trigger_file = fopen(trigger_path, "r");
                char trigger_content[256] = {0};
                if (trigger_file != NULL) {
                    fgets(trigger_content, sizeof(trigger_content), trigger_file);
                    fclose(trigger_file);
                    
                    // 分析触发模式
                    if (strstr(trigger_content, "[heartbeat]") != NULL) {
                        current_device_mode = "heartbeat";
                    } else if (strstr(trigger_content, "[timer]") != NULL) {
                        current_device_mode = "timer";
                    } else if (strstr(trigger_content, "[default-on]") != NULL) {
                        current_device_mode = "default-on";
                    } else if (strstr(trigger_content, "[mmc2]") != NULL) {
                        current_device_mode = "mmc2";
                        // 对于mmc2硬件控制的设备，亮度值可能不准确
                        // 我们假设当设备处于mmc2模式时，LED是活动的
                        if (i == 1) { // mmc2::设备
                            brightness_value = 255; // 设置为最大值表示活动状态
                        }
                    }
                    
                    LOGI("LED设备 %s 触发模式: %s", led_name, trigger_content);
                }
                
                // 分别记录两个设备的亮度和模式
                if (i == 0) { // work设备
                    work_brightness = brightness_value;
                    work_found = true;
                    work_device_mode = current_device_mode;
                } else { // mmc2::设备
                    mmc2_brightness = brightness_value;
                    mmc2_found = true;
                    mmc2_device_mode = current_device_mode;
                }
                
                // 只要有一个设备亮度>0，就认为电源开启
                if (brightness_value > 0) {
                    power_on = true;
                }
                
                LOGI("检测到LED设备 %s: 亮度=%d, 模式=%s", led_name, brightness_value, current_device_mode);
            }
            fclose(brightness_file);
        } else {
            LOGI("LED设备 %s 不存在或无法访问: %s", led_name, brightness_path);
        }
    }
    
    // 根据设备优先级选择要使用的模式（优先使用work设备的模式）
    if (work_found) {
        device_mode = work_device_mode;
    } else if (mmc2_found) {
        device_mode = mmc2_device_mode;
    }
    
    // 使用work设备的亮度作为主要显示值（如果work设备存在）
    int brightness_value = work_found ? work_brightness : (mmc2_found ? mmc2_brightness : 0);
    
    // 设置LEDState对象的字段值
    env->SetBooleanField(ledState, powerOnField, power_on);
    env->SetIntField(ledState, brightnessField, brightness_value);
    env->SetIntField(ledState, workBrightnessField, work_brightness);
    env->SetIntField(ledState, mmc2BrightnessField, mmc2_brightness);
    env->SetBooleanField(ledState, workFoundField, work_found);
    env->SetBooleanField(ledState, mmc2FoundField, mmc2_found);
    
    // 设置模式
    jstring modeStr = env->NewStringUTF(device_found ? device_mode : "设备未找到");
    
    env->SetObjectField(ledState, modeField, modeStr);
    
    // 释放本地引用
    env->DeleteLocalRef(modeStr);
    env->DeleteLocalRef(ledStateClass);
    
    return ledState;
}

/**
 * 读取GPIO状态（用于物理按键监听）
 */
JNIEXPORT jint JNICALL
Java_com_example_myapplication3_RK3588HardwareService_readGPIOState(JNIEnv *env, jobject thiz,
                                                                   jint gpio_pin) {
    (void)env;  // 标记未使用参数
    (void)thiz; // 标记未使用参数
    (void)gpio_pin; // 标记未使用参数
    
    static bool gpio_warning_logged = false; // 静态变量记录是否已记录警告
    
    if (gpio_fd < 0) {
        // 尝试打开GPIO设备
        gpio_fd = open(GPIO_DEVICE_NODE, O_RDWR);
        if (gpio_fd < 0) {
            // 只在第一次失败时记录警告，避免重复日志
            if (!gpio_warning_logged) {
                LOGI("GPIO设备节点不存在或无法访问: %s (%s) - 启用模拟模式", GPIO_DEVICE_NODE, strerror(errno));
                gpio_warning_logged = true;
            }
            // 返回模拟值而不是错误，让应用能够继续运行
            return (gpio_pin % 2) == 0 ? 0 : 1; // 根据引脚号返回模拟值
        }
    }
    
    // 实际实现需要根据GPIO驱动接口读取指定引脚状态
    // 这里返回模拟值
    return (gpio_pin % 2) == 0 ? 0 : 1; // 0表示低电平，1表示高电平
}

/**
 * 初始化硬件设备
 */
JNIEXPORT jboolean JNICALL
Java_com_example_myapplication3_RK3588HardwareService_initializeHardware(JNIEnv *env, jobject thiz) {
    LOGI("初始化硬件设备");
    
    // 如果设备已经初始化，直接返回成功
    if (led_fd >= 0) {
        LOGI("硬件设备已经初始化");
        return JNI_TRUE;
    }
    
    // 打开LED设备
    if (!Java_com_example_myapplication3_RK3588HardwareService_openLEDDevice(env, thiz)) {
        LOGE("初始化LED设备失败");
        return JNI_FALSE;
    }
    
    // 检查设备权限
    if (led_fd >= 0) {
        // 设置设备为非阻塞模式
        int flags = fcntl(led_fd, F_GETFL, 0);
        fcntl(led_fd, F_SETFL, flags | O_NONBLOCK);
        LOGI("LED设备权限设置完成");
    }
    
    return JNI_TRUE;
}

/**
 * 检查设备节点是否存在
 */
JNIEXPORT jboolean JNICALL
Java_com_example_myapplication3_RK3588HardwareService_checkDeviceNode(JNIEnv *env, jobject thiz,
                                                                      jstring device_path) {
    (void)thiz; // 标记未使用参数
    
    const char *path = env->GetStringUTFChars(device_path, nullptr);
    if (path == nullptr) {
        return JNI_FALSE;
    }
    
    int fd = open(path, O_RDONLY);
    if (fd >= 0) {
        close(fd);
        env->ReleaseStringUTFChars(device_path, path);
        LOGI("设备节点存在: %s", path);
        return JNI_TRUE;
    }
    
    env->ReleaseStringUTFChars(device_path, path);
    LOGI("设备节点不存在: %s", path);
    return JNI_FALSE;
}

// 声明在device_permissions.cpp中定义的函数
extern "C" JNIEXPORT jboolean JNICALL
Java_com_example_myapplication3_RK3588HardwareService_checkDevicePermissions(JNIEnv *env, jobject thiz,
                                                                              jstring device_path);

extern "C" JNIEXPORT jboolean JNICALL
Java_com_example_myapplication3_RK3588HardwareService_setDevicePermissions(JNIEnv *env, jobject thiz,
                                                                            jstring device_path, jint mode);

extern "C" JNIEXPORT jboolean JNICALL
Java_com_example_myapplication3_RK3588HardwareService_checkRootPermission(JNIEnv *env, jobject thiz);

/**
 * 检查设备权限
 */


// ========== 串口通信JNI实现 ==========

#include <termios.h>

/**
 * 打开串口
 */
JNIEXPORT jboolean JNICALL
Java_com_example_myapplication3_SerialPortManager_nativeOpen(JNIEnv *env, jobject thiz,
                                                            jstring port, jint baud_rate) {
    (void)thiz; // 标记未使用参数
    
    // 如果串口已经打开，先关闭
    if (serial_fd >= 0) {
        close(serial_fd);
        serial_fd = -1;
        LOGI("关闭已存在的串口连接");
    }
    
    const char *port_str = env->GetStringUTFChars(port, nullptr);
    if (port_str == nullptr) {
        return JNI_FALSE;
    }
    
    // 打开串口设备
    serial_fd = open(port_str, O_RDWR | O_NOCTTY | O_NONBLOCK);
    if (serial_fd < 0) {
        LOGE("打开串口失败: %s, 错误: %s", port_str, strerror(errno));
        env->ReleaseStringUTFChars(port, port_str);
        return JNI_FALSE;
    }
    
    // 配置串口参数
    struct termios options;
    tcgetattr(serial_fd, &options);
    
    // 设置波特率
    speed_t speed;
    switch (baud_rate) {
        case 9600: speed = B9600; break;
        case 19200: speed = B19200; break;
        case 38400: speed = B38400; break;
        case 57600: speed = B57600; break;
        case 115200: speed = B115200; break;
        default: speed = B115200; break;
    }
    cfsetispeed(&options, speed);
    cfsetospeed(&options, speed);
    
    // 设置数据位、停止位、校验位
    options.c_cflag &= ~PARENB;   // 无校验
    options.c_cflag &= ~CSTOPB;   // 1个停止位
    options.c_cflag &= ~CSIZE;    // 清除数据位掩码
    options.c_cflag |= CS8;       // 8个数据位
    
    // 设置其他参数
    options.c_cflag |= (CLOCAL | CREAD);  // 本地连接，启用接收
    options.c_lflag &= ~(ICANON | ECHO | ECHOE | ISIG);  // 原始模式
    options.c_oflag &= ~OPOST;   // 原始输出
    
    // 设置超时
    options.c_cc[VMIN] = 0;       // 最小字符数
    options.c_cc[VTIME] = 10;     // 超时时间（0.1秒）
    
    // 应用配置
    if (tcsetattr(serial_fd, TCSANOW, &options) != 0) {
        LOGE("配置串口参数失败: %s", strerror(errno));
        close(serial_fd);
        serial_fd = -1;
        env->ReleaseStringUTFChars(port, port_str);
        return JNI_FALSE;
    }
    
    env->ReleaseStringUTFChars(port, port_str);
    LOGI("串口打开成功: %s @ %d baud", port_str, baud_rate);
    return JNI_TRUE;
}

/**
 * 关闭串口
 */
JNIEXPORT void JNICALL
Java_com_example_myapplication3_SerialPortManager_nativeClose(JNIEnv *env, jobject thiz) {
    (void)env;  // 标记未使用参数
    (void)thiz; // 标记未使用参数
    
    if (serial_fd >= 0) {
        close(serial_fd);
        serial_fd = -1;
        LOGI("串口已关闭");
    }
}

/**
 * 读取串口数据
 */
JNIEXPORT jint JNICALL
Java_com_example_myapplication3_SerialPortManager_nativeRead(JNIEnv *env, jobject thiz,
                                                           jbyteArray buffer, jint size) {
    (void)thiz; // 标记未使用参数
    
    if (serial_fd < 0) {
        LOGE("串口未打开");
        return -1;
    }
    
    jbyte *buf = env->GetByteArrayElements(buffer, nullptr);
    if (buf == nullptr) {
        LOGE("获取缓冲区失败");
        return -1;
    }
    
    int bytes_read = read(serial_fd, buf, size);
    env->ReleaseByteArrayElements(buffer, buf, 0);
    
    if (bytes_read < 0) {
        if (errno != EAGAIN) {
            LOGE("读取串口数据失败: %s", strerror(errno));
        }
        return -1;
    }
    
    LOGI("读取串口数据: %d 字节", bytes_read);
    return bytes_read;
}

/**
 * 写入串口数据
 */
JNIEXPORT jint JNICALL
Java_com_example_myapplication3_SerialPortManager_nativeWrite(JNIEnv *env, jobject thiz,
                                                             jbyteArray data, jint size) {
    (void)thiz; // 标记未使用参数
    
    if (serial_fd < 0) {
        LOGE("串口未打开");
        return -1;
    }
    
    jbyte *data_ptr = env->GetByteArrayElements(data, nullptr);
    if (data_ptr == nullptr) {
        LOGE("获取数据指针失败");
        return -1;
    }
    
    int bytes_written = write(serial_fd, data_ptr, size);
    env->ReleaseByteArrayElements(data, data_ptr, 0);
    
    if (bytes_written < 0) {
        LOGE("写入串口数据失败: %s", strerror(errno));
        return -1;
    }
    
    LOGI("写入串口数据: %d 字节", bytes_written);
    return bytes_written;
}

/**
 * 检查串口是否打开
 */
JNIEXPORT jboolean JNICALL
Java_com_example_myapplication3_SerialPortManager_nativeIsOpen(JNIEnv *env, jobject thiz) {
    (void)env;  // 标记未使用参数
    (void)thiz; // 标记未使用参数
    
    return serial_fd >= 0 ? JNI_TRUE : JNI_FALSE;
}

/**
 * 设置串口参数
 */
JNIEXPORT jboolean JNICALL
Java_com_example_myapplication3_SerialPortManager_nativeSetParameters(JNIEnv *env, jobject thiz,
                                                                     jint baud_rate, jint data_bits,
                                                                     jint stop_bits, jint parity) {
    (void)env;  // 标记未使用参数
    (void)thiz; // 标记未使用参数
    
    if (serial_fd < 0) {
        LOGE("串口未打开");
        return JNI_FALSE;
    }
    
    struct termios options;
    if (tcgetattr(serial_fd, &options) != 0) {
        LOGE("获取串口参数失败: %s", strerror(errno));
        return JNI_FALSE;
    }
    
    // 设置波特率
    speed_t speed;
    switch (baud_rate) {
        case 9600: speed = B9600; break;
        case 19200: speed = B19200; break;
        case 38400: speed = B38400; break;
        case 57600: speed = B57600; break;
        case 115200: speed = B115200; break;
        default: speed = B115200; break;
    }
    cfsetispeed(&options, speed);
    cfsetospeed(&options, speed);
    
    // 设置数据位
    options.c_cflag &= ~CSIZE;
    switch (data_bits) {
        case 5: options.c_cflag |= CS5; break;
        case 6: options.c_cflag |= CS6; break;
        case 7: options.c_cflag |= CS7; break;
        case 8: options.c_cflag |= CS8; break;
        default: options.c_cflag |= CS8; break;
    }
    
    // 设置停止位
    if (stop_bits == 2) {
        options.c_cflag |= CSTOPB;
    } else {
        options.c_cflag &= ~CSTOPB;
    }
    
    // 设置校验位
    switch (parity) {
        case 0: // 无校验
            options.c_cflag &= ~PARENB;
            break;
        case 1: // 奇校验
            options.c_cflag |= PARENB;
            options.c_cflag |= PARODD;
            break;
        case 2: // 偶校验
            options.c_cflag |= PARENB;
            options.c_cflag &= ~PARODD;
            break;
        default:
            options.c_cflag &= ~PARENB;
            break;
    }
    
    // 应用配置
    if (tcsetattr(serial_fd, TCSANOW, &options) != 0) {
        LOGE("设置串口参数失败: %s", strerror(errno));
        return JNI_FALSE;
    }
    
    LOGI("设置串口参数: baud=%d, data=%d, stop=%d, parity=%d",
         baud_rate, data_bits, stop_bits, parity);
    return JNI_TRUE;
}

// ========== HardwareReader JNI实现 ==========

/**
 * 读取LED状态
 */
JNIEXPORT jstring JNICALL
Java_com_example_myapplication3_HardwareReader_readLEDState(JNIEnv *env, jobject thiz) {
    (void)thiz; // 标记未使用参数
    
    // 检查LED设备状态
    char buffer[256];
    
    // 直接读取work LED的状态（设备上实际存在的LED）
    FILE* fp = fopen("/sys/class/leds/work/brightness", "r");
    if (fp != nullptr) {
        int brightness;
        if (fscanf(fp, "%d", &brightness) == 1) {
            // 检查LED的触发模式
            FILE* trigger_fp = fopen("/sys/class/leds/work/trigger", "r");
            if (trigger_fp != nullptr) {
                char trigger_content[512];
                if (fgets(trigger_content, sizeof(trigger_content), trigger_fp) != nullptr) {
                    // 检查是否处于特殊模式（如heartbeat、timer等）
                    if (strstr(trigger_content, "[heartbeat]") != nullptr) {
                        snprintf(buffer, sizeof(buffer), "LED状态(work): 心跳模式闪烁中 (亮度值=%d)", brightness);
                    } else if (strstr(trigger_content, "[timer]") != nullptr) {
                        snprintf(buffer, sizeof(buffer), "LED状态(work): 定时器模式闪烁中 (亮度值=%d)", brightness);
                    } else if (strstr(trigger_content, "[default-on]") != nullptr) {
                        snprintf(buffer, sizeof(buffer), "LED状态(work): 默认开启 (亮度值=%d)", brightness);
                    } else {
                        snprintf(buffer, sizeof(buffer), "LED状态(work): 亮度=%d", brightness);
                    }
                } else {
                    snprintf(buffer, sizeof(buffer), "LED状态(work): 亮度=%d", brightness);
                }
                fclose(trigger_fp);
            } else {
                snprintf(buffer, sizeof(buffer), "LED状态(work): 亮度=%d", brightness);
            }
            fclose(fp);
            return env->NewStringUTF(buffer);
        }
        fclose(fp);
    }
    
    // 如果无法读取，返回错误信息
    snprintf(buffer, sizeof(buffer), "LED状态: 无法读取work LED设备");
    return env->NewStringUTF(buffer);
}

/**
 * 检查设备权限
 */
JNIEXPORT jboolean JNICALL
Java_com_example_myapplication3_HardwareReader_checkDevicePermissions(JNIEnv *env, jobject thiz, jstring devicePath) {
    (void)thiz; // 标记未使用参数
    
    const char* path = env->GetStringUTFChars(devicePath, nullptr);
    if (path == nullptr) {
        return JNI_FALSE;
    }
    
    // 检查文件是否存在且有读取权限
    if (access(path, R_OK) == 0) {
        LOGI("设备权限检查通过: %s", path);
        env->ReleaseStringUTFChars(devicePath, path);
        return JNI_TRUE;
    }
    
    LOGI("设备权限检查失败: %s", path);
    env->ReleaseStringUTFChars(devicePath, path);
    return JNI_FALSE;
}

/**
 * 读取系统信息
 */
JNIEXPORT jstring JNICALL
Java_com_example_myapplication3_HardwareReader_readSystemInfo(JNIEnv *env, jobject thiz) {
    (void)thiz; // 标记未使用参数
    
    char buffer[1024];
    
    // 读取系统信息
    FILE* fp = fopen("/proc/version", "r");
    if (fp != nullptr) {
        char version[256];
        if (fgets(version, sizeof(version), fp) != nullptr) {
            snprintf(buffer, sizeof(buffer), "系统信息: %s", version);
            fclose(fp);
            return env->NewStringUTF(buffer);
        }
        fclose(fp);
    }
    
    // 返回错误信息
    snprintf(buffer, sizeof(buffer), "系统信息: 无法读取系统版本信息");
    return env->NewStringUTF(buffer);
}

/**
 * 控制work LED设备
 * 由于需要root权限，此方法提供替代方案和状态检查
 */
JNIEXPORT jboolean JNICALL
Java_com_example_myapplication3_RK3588HardwareService_controlWorkLED(JNIEnv *env, jobject thiz,
                                                                      jboolean enable) {
    (void)thiz; // 标记未使用参数
    
    const char* led_name = "work";
    char trigger_path[256];
    char brightness_path[256];
    
    snprintf(trigger_path, sizeof(trigger_path), "/sys/class/leds/%s/trigger", led_name);
    snprintf(brightness_path, sizeof(brightness_path), "/sys/class/leds/%s/brightness", led_name);
    
    LOGI("尝试控制work LED设备: %s", enable ? "开启" : "关闭");
    
    // 首先检查设备是否存在
    FILE* check_file = fopen(brightness_path, "r");
    if (check_file == NULL) {
        LOGI("work设备不存在或无法访问");
        return JNI_FALSE;
    }
    fclose(check_file);
    
    // 尝试通过修改触发模式来控制
    FILE* trigger_file = fopen(trigger_path, "w");
    if (trigger_file != NULL) {
        if (enable) {
            // 尝试设置为default-on模式
            if (fprintf(trigger_file, "default-on") > 0) {
                LOGI("成功设置work设备为default-on模式");
                fclose(trigger_file);
                return JNI_TRUE;
            }
        } else {
            // 尝试设置为none模式（关闭）
            if (fprintf(trigger_file, "none") > 0) {
                LOGI("成功设置work设备为none模式");
                fclose(trigger_file);
                
                // 同时设置亮度为0确保关闭
                FILE* brightness_file = fopen(brightness_path, "w");
                if (brightness_file != NULL) {
                    fprintf(brightness_file, "0");
                    fclose(brightness_file);
                }
                return JNI_TRUE;
            }
        }
        fclose(trigger_file);
    }
    
    // 如果触发模式控制失败，尝试直接控制亮度
    FILE* brightness_file = fopen(brightness_path, "w");
    if (brightness_file != NULL) {
        if (fprintf(brightness_file, "%d", enable ? 255 : 0) > 0) {
            LOGI("成功通过亮度控制work设备: %s", enable ? "开启" : "关闭");
            fclose(brightness_file);
            return JNI_TRUE;
        }
        fclose(brightness_file);
    }
    
    // 如果控制失败，提供详细的错误信息和替代方案
    LOGI("控制work设备失败，需要root权限");
    LOGI("替代方案: 1. 使用root权限运行应用 2. 通过系统命令控制");
    
    return JNI_FALSE;
}

/**
 * 设置Work LED设备亮度
 * 支持0-255范围的亮度调节
 */
JNIEXPORT jboolean JNICALL
Java_com_example_myapplication3_RK3588HardwareService_setWorkLEDBrightness(JNIEnv *env, jobject thiz,
                                                                            jint brightness) {
    (void)thiz; // 标记未使用参数
    
    const char* led_name = "work";
    char brightness_path[256];
    
    snprintf(brightness_path, sizeof(brightness_path), "/sys/class/leds/%s/brightness", led_name);
    
    LOGI("尝试设置work LED设备亮度: %d", brightness);
    
    // 验证亮度值范围
    if (brightness < 0 || brightness > 255) {
        LOGE("亮度值无效: %d (有效范围: 0-255)", brightness);
        return JNI_FALSE;
    }
    
    // 首先检查设备是否存在
    FILE* check_file = fopen(brightness_path, "r");
    if (check_file == NULL) {
        LOGI("work设备不存在或无法访问");
        return JNI_FALSE;
    }
    fclose(check_file);
    
    // 尝试直接控制亮度
    FILE* brightness_file = fopen(brightness_path, "w");
    if (brightness_file != NULL) {
        if (fprintf(brightness_file, "%d", brightness) > 0) {
            LOGI("成功设置work设备亮度: %d", brightness);
            fclose(brightness_file);
            return JNI_TRUE;
        }
        fclose(brightness_file);
    }
    
    // 如果控制失败，提供详细的错误信息和替代方案
    LOGI("设置work设备亮度失败，需要root权限");
    LOGI("替代方案: 1. 使用root权限运行应用 2. 通过系统命令控制");
    
    return JNI_FALSE;
}

/**
 * 设置Work LED设备模式
 * 支持常亮、呼吸灯、闪烁等模式
 */
JNIEXPORT jboolean JNICALL
Java_com_example_myapplication3_RK3588HardwareService_setWorkLEDMode(JNIEnv *env, jobject thiz,
                                                                      jstring mode) {
    (void)thiz; // 标记未使用参数
    
    const char* led_name = "work";
    char trigger_path[256];
    
    snprintf(trigger_path, sizeof(trigger_path), "/sys/class/leds/%s/trigger", led_name);
    
    // 将Java字符串转换为C字符串
    const char* mode_str = env->GetStringUTFChars(mode, NULL);
    if (mode_str == NULL) {
        LOGE("无法获取模式字符串");
        return JNI_FALSE;
    }
    
    LOGI("尝试设置work LED设备模式: %s", mode_str);
    
    // 首先检查设备是否存在
    char brightness_path[256];
    snprintf(brightness_path, sizeof(brightness_path), "/sys/class/leds/%s/brightness", led_name);
    FILE* check_file = fopen(brightness_path, "r");
    if (check_file == NULL) {
        LOGI("work设备不存在或无法访问");
        env->ReleaseStringUTFChars(mode, mode_str);
        return JNI_FALSE;
    }
    fclose(check_file);
    
    // 根据模式字符串设置对应的触发模式
    const char* trigger_mode = "none"; // 默认模式
    
    if (strcmp(mode_str, "default-on") == 0) {
        trigger_mode = "default-on"; // 常亮模式
    } else if (strcmp(mode_str, "heartbeat") == 0) {
        trigger_mode = "heartbeat"; // 呼吸灯模式（使用heartbeat作为呼吸效果）
    } else if (strcmp(mode_str, "timer") == 0) {
        trigger_mode = "timer"; // 闪烁模式（使用timer作为闪烁效果）
    } else {
        LOGI("未知模式: %s，使用默认模式", mode_str);
    }
    
    LOGI("设置work设备触发模式为: %s", trigger_mode);
    
    // 尝试设置触发模式
    FILE* trigger_file = fopen(trigger_path, "w");
    if (trigger_file != NULL) {
        if (fprintf(trigger_file, "%s", trigger_mode) > 0) {
            LOGI("成功设置work设备模式为: %s", trigger_mode);
            fclose(trigger_file);
            
            // 如果设置为常亮模式，确保亮度不为0
            if (strcmp(trigger_mode, "default-on") == 0) {
                FILE* brightness_file = fopen(brightness_path, "w");
                if (brightness_file != NULL) {
                    fprintf(brightness_file, "255");
                    fclose(brightness_file);
                }
            }
            
            env->ReleaseStringUTFChars(mode, mode_str);
            return JNI_TRUE;
        }
        fclose(trigger_file);
    }
    
    // 如果控制失败，提供详细的错误信息和替代方案
    LOGI("设置work设备模式失败，需要root权限");
    LOGI("替代方案: 1. 使用root权限运行应用 2. 通过系统命令控制");
    
    env->ReleaseStringUTFChars(mode, mode_str);
    return JNI_FALSE;
}

// ========== JNI注册函数 ==========

// JNI方法注册表
static JNINativeMethod nativeMethods[] = {
    // RK3588HardwareService方法
    {"openLEDDevice", "()Z", (void*)Java_com_example_myapplication3_RK3588HardwareService_openLEDDevice},
    {"closeLEDDevice", "()V", (void*)Java_com_example_myapplication3_RK3588HardwareService_closeLEDDevice},
    {"setLEDPower", "(Z)Z", (void*)Java_com_example_myapplication3_RK3588HardwareService_setLEDPower},
    {"setLEDBrightness", "(I)Z", (void*)Java_com_example_myapplication3_RK3588HardwareService_setLEDBrightness},
    {"getLEDState", "()Lcom/example/myapplication3/RK3588HardwareService$LEDState;", (void*)Java_com_example_myapplication3_RK3588HardwareService_getLEDState},
    {"readGPIOState", "(I)I", (void*)Java_com_example_myapplication3_RK3588HardwareService_readGPIOState},
    {"initializeHardware", "()Z", (void*)Java_com_example_myapplication3_RK3588HardwareService_initializeHardware},
    {"checkDeviceNode", "(Ljava/lang/String;)Z", (void*)Java_com_example_myapplication3_RK3588HardwareService_checkDeviceNode},
    {"checkDevicePermissions", "(Ljava/lang/String;)Z", (void*)Java_com_example_myapplication3_RK3588HardwareService_checkDevicePermissions},
    {"setDevicePermissions", "(Ljava/lang/String;I)Z", (void*)Java_com_example_myapplication3_RK3588HardwareService_setDevicePermissions},
    {"checkRootPermission", "()Z", (void*)Java_com_example_myapplication3_RK3588HardwareService_checkRootPermission},
    {"controlWorkLED", "(Z)Z", (void*)Java_com_example_myapplication3_RK3588HardwareService_controlWorkLED},
    {"setWorkLEDBrightness", "(I)Z", (void*)Java_com_example_myapplication3_RK3588HardwareService_setWorkLEDBrightness},
    {"setWorkLEDMode", "(Ljava/lang/String;)Z", (void*)Java_com_example_myapplication3_RK3588HardwareService_setWorkLEDMode},
    
    // HardwareReader方法
    {"readLEDState", "()Ljava/lang/String;", (void*)Java_com_example_myapplication3_HardwareReader_readLEDState},
    {"checkDevicePermissions", "(Ljava/lang/String;)Z", (void*)Java_com_example_myapplication3_HardwareReader_checkDevicePermissions},
    {"readSystemInfo", "()Ljava/lang/String;", (void*)Java_com_example_myapplication3_HardwareReader_readSystemInfo}
};

static JNINativeMethod serialMethods[] = {
    {"nativeOpen", "(Ljava/lang/String;I)Z", (void*)Java_com_example_myapplication3_SerialPortManager_nativeOpen},
    {"nativeClose", "()V", (void*)Java_com_example_myapplication3_SerialPortManager_nativeClose},
    {"nativeRead", "([BI)I", (void*)Java_com_example_myapplication3_SerialPortManager_nativeRead},
    {"nativeWrite", "([BI)I", (void*)Java_com_example_myapplication3_SerialPortManager_nativeWrite},
    {"nativeIsOpen", "()Z", (void*)Java_com_example_myapplication3_SerialPortManager_nativeIsOpen},
    {"nativeSetParameters", "(IIII)Z", (void*)Java_com_example_myapplication3_SerialPortManager_nativeSetParameters}
};

/**
 * JNI库加载时调用的函数
 */
JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void* reserved) {
    (void)reserved; // 标记未使用参数
    
    JNIEnv* env;
    if (vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK) {
        LOGE("JNI_OnLoad: 获取JNIEnv失败");
        return JNI_ERR;
    }
    
    LOGI("JNI_OnLoad: 原生库加载成功");
    
    // 注册RK3588HardwareService类的JNI方法
    jclass serviceClass = env->FindClass("com/example/myapplication3/RK3588HardwareService");
    if (serviceClass == nullptr) {
        LOGE("JNI_OnLoad: 找不到RK3588HardwareService类");
        return JNI_ERR;
    }
    
    // 只注册RK3588HardwareService类的方法（前14个方法）
    if (env->RegisterNatives(serviceClass, nativeMethods, 14) < 0) {
        LOGE("JNI_OnLoad: 注册RK3588HardwareService JNI方法失败");
        return JNI_ERR;
    }
    
    // 注册HardwareReader类的JNI方法
    jclass readerClass = env->FindClass("com/example/myapplication3/HardwareReader");
    if (readerClass == nullptr) {
        LOGE("JNI_OnLoad: 找不到HardwareReader类");
        return JNI_ERR;
    }
    
    // 注册HardwareReader类的方法（从第14个方法开始，共3个方法）
    if (env->RegisterNatives(readerClass, &nativeMethods[14], 3) < 0) {
        LOGE("JNI_OnLoad: 注册HardwareReader JNI方法失败");
        return JNI_ERR;
    }
    
    // 注册SerialPortManager类的JNI方法
    jclass serialClass = env->FindClass("com/example/myapplication3/SerialPortManager");
    if (serialClass == nullptr) {
        LOGE("JNI_OnLoad: 找不到SerialPortManager类");
        return JNI_ERR;
    }
    
    if (env->RegisterNatives(serialClass, serialMethods, sizeof(serialMethods)/sizeof(serialMethods[0])) < 0) {
        LOGE("JNI_OnLoad: 注册SerialPortManager JNI方法失败");
        return JNI_ERR;
    }
    
    LOGI("JNI_OnLoad: 所有JNI函数注册完成");
    
    return JNI_VERSION_1_6;
}

/**
 * JNI库卸载时调用的函数
 */
JNIEXPORT void JNICALL JNI_OnUnload(JavaVM* vm, void* reserved) {
    (void)reserved; // 标记未使用参数
    
    // 关闭所有打开的设备
    if (led_fd >= 0) {
        close(led_fd);
        led_fd = -1;
    }
    if (gpio_fd >= 0) {
        close(gpio_fd);
        gpio_fd = -1;
    }
    if (serial_fd >= 0) {
        close(serial_fd);
        serial_fd = -1;
    }
    
    LOGI("JNI_OnUnload: 原生库卸载完成");
}

#ifdef __cplusplus
}
#endif