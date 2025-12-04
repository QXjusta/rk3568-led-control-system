# LED驱动适配指南

## 1. 驱动适配核心原则

### 1.1 保持接口一致性
- **数据格式统一**：无论使用何种驱动，最终传递给应用层的数据必须保持统一的`LEDState`格式
- **接口行为一致**：新驱动必须支持与现有驱动相同的功能（电源控制、亮度调节、模式切换）
- **错误处理兼容**：错误码和异常情况处理方式应保持一致

### 1.2 分层适配策略
- **JNI层适配**：仅修改`rk3588_hardware_jni.cpp`中的驱动交互部分
- **应用层不变**：上层应用代码（Activity、Service）无需任何修改
- **双模式保留**：继续支持"自定义驱动+sysfs回退"的双控制模式

## 2. 驱动替换关键步骤

### 2.1 设备节点与指令适配
1. **更新设备节点路径**：修改`LED_DEVICE_NODE`宏定义
   ```cpp
   // 原定义
   #define LED_DEVICE_NODE "/dev/zhangsan_led"
   
   // 新驱动定义
   #define LED_DEVICE_NODE "/dev/new_led_driver"
   ```

2. **调整控制指令**：更新`ioctl`命令或通信方式
   ```cpp
   // 原命令
   #define IOCTL_GET_LED_STATE _IOR(LED_MAGIC, 0, led_state_t)
   #define IOCTL_SET_LED_POWER _IOW(LED_MAGIC, 1, int)
   
   // 新驱动命令
   #define IOCTL_GET_LED_STATE _IOR(LED_MAGIC, 10, new_led_state_t)
   #define IOCTL_SET_LED_POWER _IOW(LED_MAGIC, 11, int)
   ```

### 2.2 数据格式转换
在`getLEDState()`函数中，将新驱动返回的原始数据转换为统一的`LEDState`格式：

```cpp
// 假设新驱动返回的数据结构
struct new_led_state {
    int is_on;
    int level;
    char mode_str[20];
};

// 转换为LEDState对象
LEDState* javaLedState = (LEDState*)env->NewObject(ledStateClass, constructor);
env->SetBooleanField(javaLedState, powerOnField, new_state->is_on);
env->SetIntField(javaLedState, brightnessField, new_state->level);
env->SetObjectField(javaLedState, modeField, env->NewStringUTF(new_state->mode_str));
```

### 2.3 模式名称映射
当驱动返回的模式名称与APP预期不一致时，添加模式映射表：

```cpp
// 模式名称映射表
const std::map<std::string, std::string> modeMapping = {
    {"breathing", "heartbeat"},  // 驱动返回"breathing" -> 映射为"heartbeat"
    {"pulse", "heartbeat"},      // 驱动返回"pulse" -> 映射为"heartbeat"
    {"blink", "timer"},          // 驱动返回"blink" -> 映射为"timer"
    {"on", "default-on"}         // 驱动返回"on" -> 映射为"default-on"
};

// 应用映射
std::string modeStr(originalMode);
auto it = modeMapping.find(modeStr);
const char* finalMode = (it != modeMapping.end()) ? it->second.c_str() : originalMode;
```

## 3. 现有代码优势分析

### 3.1 智能双控制模式
- **优先使用自定义驱动**：通过设备节点直接控制，性能更好
- **自动回退到sysfs**：当设备节点不存在时，通过`/sys/class/leds/work/`路径控制
- **无缝切换**：用户无感知，保证系统可用性

### 3.2 分层架构设计
- **UI层（MainActivity）**：用户交互和显示，独立于硬件实现
- **服务层（RK3588HardwareService）**：统一硬件控制接口，隔离底层变化
- **JNI层**：处理底层硬件通信细节，易于适配新驱动

## 4. 测试与验证

### 4.1 功能验证
1. **电源控制**：验证LED开关功能正常
2. **亮度调节**：验证亮度调节范围和精度
3. **模式切换**：验证常亮、心跳、闪烁模式切换正常
4. **状态同步**：验证硬件状态变化能正确同步到UI

### 4.2 兼容性测试
1. **有驱动场景**：验证自定义驱动正常工作
2. **无驱动场景**：验证sysfs回退机制正常工作
3. **驱动切换**：验证在驱动加载/卸载过程中系统稳定性

## 5. 注意事项

### 5.1 权限处理
- 确保Android应用有足够权限访问新设备节点
- 可能需要在`AndroidManifest.xml`中添加权限声明

### 5.2 错误处理
- 保留原有的错误处理逻辑，确保用户体验一致
- 添加新驱动特有的错误码处理

### 5.3 性能优化
- 保持原有代码的性能优化策略
- 根据新驱动特性适当调整缓冲和同步机制

## 6. 结论

通过遵循上述指南，可以在**不修改任何应用层代码**的情况下完成驱动替换。关键在于保持接口一致性和数据格式统一，利用现有代码的分层架构和双控制模式优势，确保系统的稳定性和兼容性。

---

**文档版本**：1.0  
**创建日期**：2023-12-15  
**适用范围**：RK3588/RK3568 LED控制系统驱动适配  
**编写者**：AI助手