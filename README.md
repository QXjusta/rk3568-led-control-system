RK3568 / RK3588 LED 控制系统
这是一个面向 Rockchip 开发板的嵌入式 LED 控制项目，目标是在 RK3568 / RK3588 平台上实现对 work 指示灯的可视化控制、状态读取和硬件联动。项目同时包含：
Android 应用层界面
Java Service 硬件服务层
JNI/C++ 硬件访问层
Linux 内核字符设备驱动
设备树覆盖与驱动编译说明
项目的核心思路是把 LED 控制能力从底层驱动一直打通到 Android 界面，并保留 sysfs 回退机制，保证在不同开发阶段都能调试和验证。
项目特点
支持 LED 开关控制
支持亮度调节
支持三种模式切换：default-on、heartbeat、timer
支持 Android UI 与硬件状态同步
支持字符设备驱动优先、sysfs 回退的双通道控制
支持物理按键中断控制 LED
提供独立的硬件测试页面
提供 JNI 层封装，便于后续适配新驱动
系统架构
项目整体可以分为四层：
Android UI 层
入口界面位于 MainActivity
提供 Work 灯开关、亮度滑条、模式卡片、硬件测试入口

Android 服务层
RK3588HardwareService 负责统一管理硬件访问
对上提供 LED 控制、状态查询、权限检查、串口连接等接口
内部带有状态轮询和跨进程 Binder 通信逻辑

JNI / C++ 层
rk3588_hardware_jni.cpp
优先访问字符设备节点 /dev/yuanzi_led
如果字符设备不可用，则回退到 /sys/class/leds/work/
同时封装 GPIO 读取和串口基础能力

Linux 驱动层
driver/zhangsan_led.c
实现字符设备、ioctl 控制、GPIO 初始化、按键中断
创建设备节点并维护 LED 状态结构体

主要功能
1. Android 端控制
控制 work LED 开关
在常亮模式下调节亮度
切换 LED 模式
保存用户上一次选择的模式
应用启动后自动同步硬件状态
后台周期性读取硬件状态并刷新 UI
2. 硬件测试
项目包含一个独立的 HardwareTestActivity，用于：
读取 LED 当前状态
检查设备权限
读取系统信息
在独立进程下验证 Service / Binder 通信
3. 驱动与硬件联动
驱动层支持以下能力：
字符设备节点控制
LED_SET_POWER：开关控制
LED_SET_BRIGHTNESS：亮度控制
LED_GET_STATE：状态读取
GPIO 按键中断触发 LED 状态切换
目录结构
.
├─ app/                     Android 应用源码
│  ├─ src/main/java/        Java 业务代码
│  ├─ src/main/cpp/         JNI / C++ 硬件访问代码
│  └─ src/main/res/         界面资源
├─ driver/                  Linux 驱动与设备树相关文件
│  ├─ zhangsan_led.c        字符设备驱动
│  ├─ led-button-overlay.dts
│  └─ 编译安装指南.md
├─ 项目文档/                需求、记录与版本说明
├─ COMPILE_INSTRUCTIONS.txt 内核/驱动编译说明
└─ build.gradle.kts         Gradle 根配置
关键文件说明
app/src/main/java/com/example/myapplication3/MainActivity.java
主界面与用户交互逻辑

app/src/main/java/com/example/myapplication3/RK3588HardwareService.java
Android 硬件服务层

app/src/main/java/com/example/myapplication3/HardwareTestActivity.java
硬件测试页面

app/src/main/java/com/example/myapplication3/LEDState.java
LED 状态数据结构，支持跨进程传递

app/src/main/cpp/rk3588_hardware_jni.cpp
JNI 与底层设备访问实现

driver/zhangsan_led.c
Linux LED 字符设备驱动

支持的控制方式
1. App 控制
在 Android 界面中完成：
LED 开关
亮度调节
模式切换
状态查看
2. ADB / sysfs 控制
如果设备权限允许，可以直接通过命令控制：
# 打开 LED
echo 255 > /sys/class/leds/work/brightness

# 关闭 LED
echo 0 > /sys/class/leds/work/brightness

# 切换为常亮模式
echo default-on > /sys/class/leds/work/trigger

# 切换为呼吸模式
echo heartbeat > /sys/class/leds/work/trigger

# 切换为闪烁模式
echo timer > /sys/class/leds/work/trigger
3. 字符设备控制
JNI 层优先访问：
/dev/yuanzi_led
如果驱动加载正常，应用会优先通过 ioctl 与字符设备通信。
构建环境
Android 应用
Android Studio
Gradle Kotlin DSL
compileSdk = 36
minSdk = 30
targetSdk = 36
ndkVersion = 29.0.14206865
Java 11
CMake 3.22.1
驱动编译
建议环境：
Ubuntu / Debian Linux
ARM64 交叉编译工具链
对应版本的 Rockchip 内核源码
Android 应用构建
可在 Android Studio 中直接打开项目构建，也可以使用 Gradle：
./gradlew assembleDebug
Windows 下：
gradlew.bat assembleDebug
驱动编译与加载
驱动源码位于：
driver/zhangsan_led.c
基本流程如下：
准备 RK3568 / RK3588 对应内核源码
配置交叉编译器
修改 driver/Makefile 中的内核路径
编译生成 .ko 模块
编译 led-button-overlay.dts
将模块与设备树覆盖文件上传到开发板
使用 insmod 加载驱动
检查设备节点和 sysfs 路径是否创建成功
更详细说明可参考：
driver/编译安装指南.md
COMPILE_INSTRUCTIONS.txt
运行依赖与权限说明
项目中多处硬件访问依赖以下能力：
访问 /dev/yuanzi_led
访问 /sys/class/leds/work/brightness
访问 /sys/class/leds/work/trigger
某些场景下需要 root 权限或放宽节点权限
应用代码中也尝试通过 su 对 LED 节点执行 chmod 666，便于开发调试。但在正式环境中，建议使用更安全的权限方案。
当前实现中的兼容策略
从源码可以看出，项目对硬件接入做了兼容设计：
如果自定义字符设备可用，优先使用字符设备
如果字符设备不可用，回退到标准 sysfs LED 接口
如果串口不可用，但 LED 节点可访问，服务仍可认为硬件“可用”
如果 GPIO 无法真实读取，部分逻辑会进入模拟/兼容流程
这意味着它既适合课程设计或实验项目，也适合作为后续正式驱动适配的原型工程。
已知注意点
根据仓库现状，阅读源码后建议注意以下问题：
项目命名中同时出现了 RK3568 和 RK3588，实际适配目标需要再统一确认
驱动层设备名、JNI 层设备节点名、文档中的节点名存在一定不一致，需要部署前核对
AndroidManifest 中包含一些普通应用通常无法直接获得的权限，实际运行效果依赖系统环境
部分逻辑依赖 root 权限，量产部署时需要重新设计权限策略
适用场景
嵌入式课程设计
Linux 驱动开发实验
Android 与底层驱动联调
RK 平台 LED 控制原型验证
JNI 与硬件访问教学示例
后续可优化方向
统一 RK3568 / RK3588 命名与硬件目标
统一设备节点命名，如 /dev/zhangsan_led 与 /dev/yuanzi_led
完善 AIDL / Binder 接口定义，减少手写 transact
将权限处理从 root 方案改为更规范的系统集成方案
增加驱动、JNI、App 三层的联调测试文档
补充项目截图、硬件连线图和实际运行效果图
参考文档
仓库内已有较完整的辅助资料，可结合阅读：
项目文档/LED控制系统_驱动开发需求文档.md
driver/编译安装指南.md
COMPILE_INSTRUCTIONS.txt
项目文档/版本变更记录.md
