/*
 * Yuanzi LED Driver for RK3568
 * 
 * Features:
 * - GPIO-based LED control
 * - Input event support for ADC keys (KEY_VOLUMEUP)
 * - Multiple modes: default-on, heartbeat, timer
 * - Sysfs interface at /sys/class/leds/work/
 * - Character device at /dev/yuanzi_led
 * - Standard LED trigger support
 */

#include <linux/module.h>
#include <linux/kernel.h>
#include <linux/init.h>
#include <linux/fs.h>
#include <linux/cdev.h>
#include <linux/device.h>
#include <linux/gpio.h>
#include <linux/interrupt.h>
#include <linux/delay.h>
#include <linux/uaccess.h>
#include <linux/platform_device.h>
#include <linux/of.h>
#include <linux/of_gpio.h>
#include <linux/leds.h>
#include <linux/workqueue.h>
#include <linux/timer.h>
#include <linux/slab.h>
#include <linux/input.h>
#include <linux/input-event-codes.h>

// 设备名称
#define DEVICE_NAME "yuanzi_led"
#define CLASS_NAME  "led_class"
#define LED_NAME    "work"  // sysfs路径：/sys/class/leds/work/

// IOCTL命令定义
#define LED_MAGIC 'L'
#define LED_SET_POWER _IOW(LED_MAGIC, 1, int)
#define LED_SET_BRIGHTNESS _IOW(LED_MAGIC, 2, int)
#define LED_SET_MODE _IOW(LED_MAGIC, 3, char[32])
#define LED_GET_STATE _IOR(LED_MAGIC, 4, struct led_state)

// LED状态结构体
struct led_state {
    int power_on;
    int brightness;
    char mode[32];
};

// LED模式枚举
enum led_mode {
    LED_MODE_OFF = 0,
    LED_MODE_ON,
    LED_MODE_HEARTBEAT,
    LED_MODE_TIMER,
    LED_MODE_MAX
};

typedef enum led_mode led_mode_t;

// LED模式名称映射（与标准LED子系统一致）
static const char *led_mode_names[] = {
    [LED_MODE_OFF] = "none",
    [LED_MODE_ON] = "default-on",
    [LED_MODE_HEARTBEAT] = "heartbeat",
    [LED_MODE_TIMER] = "timer",
};

// 添加模块参数，允许手动指定GPIO引脚
static int led_gpio = -1;
module_param(led_gpio, int, 0644);
MODULE_PARM_DESC(led_gpio, "LED GPIO pin number");

// 驱动私有数据结构
struct led_private {
    // 字符设备相关
    dev_t dev_num;
    struct cdev cdev;
    struct class *class;
    struct device *device;
    
    // GPIO相关
    struct gpio_desc *led_gpiod;
    
    // LED子系统相关
    struct led_classdev led_cdev;
    struct work_struct led_work;
    struct timer_list heartbeat_timer;
    struct timer_list blink_timer;
    
    // 状态管理
    struct led_state state;
    led_mode_t current_mode;
    int heartbeat_phase;
    int blink_state;
    
    // 输入事件相关
    struct input_handler input_handler;
    struct notifier_block input_notifier;
};

#define pr_fmt(fmt) KBUILD_MODNAME ": " fmt

#define LED_DEBUG(fmt, args...) pr_debug(fmt, ##args)
#define LED_INFO(fmt, args...) pr_info(fmt, ##args)
#define LED_ERROR(fmt, args...) pr_err(fmt, ##args)

// 全局私有数据
static struct led_private *led_priv;

// 设置LED模式
static void set_led_mode(struct led_private *priv, led_mode_t mode)
{
    // 保存当前模式
    priv->current_mode = mode;
    strncpy(priv->state.mode, led_mode_names[mode], sizeof(priv->state.mode) - 1);
    priv->state.mode[sizeof(priv->state.mode) - 1] = '\0';
    
    LED_DEBUG("设置LED模式: %s\n", led_mode_names[mode]);
    
    // 根据模式执行不同的操作
    switch (mode) {
    case LED_MODE_OFF:
        // 关闭LED
        gpiod_set_value(priv->led_gpiod, 0);
        priv->led_cdev.brightness = 0;
        // 取消所有定时器
        del_timer_sync(&priv->heartbeat_timer);
        del_timer_sync(&priv->blink_timer);
        break;
        
    case LED_MODE_ON:
        // 常亮模式
        gpiod_set_value(priv->led_gpiod, 1);
        priv->led_cdev.brightness = 255;
        // 取消所有定时器
        del_timer_sync(&priv->heartbeat_timer);
        del_timer_sync(&priv->blink_timer);
        break;
        
    case LED_MODE_HEARTBEAT:
        // 心跳模式
        priv->heartbeat_phase = 0;
        // 初始化心跳定时器（1秒间隔）
        mod_timer(&priv->heartbeat_timer, jiffies + HZ);
        // 取消闪烁定时器
        del_timer_sync(&priv->blink_timer);
        break;
        
    case LED_MODE_TIMER:
        // 定时器模式（1秒闪烁一次）
        priv->blink_state = 0;
        // 初始化闪烁定时器（500ms间隔）
        mod_timer(&priv->blink_timer, jiffies + HZ/2);
        // 取消心跳定时器
        del_timer_sync(&priv->heartbeat_timer);
        break;
        
    default:
        LED_ERROR("未知的LED模式: %d\n", mode);
        break;
    }
    
    // 更新状态
    priv->state.power_on = (mode != LED_MODE_OFF);
}

// 心跳定时器回调
static void heartbeat_timer_callback(struct timer_list *timer)
{
    struct led_private *priv = from_timer(priv, timer, heartbeat_timer);
    
    if (priv->current_mode == LED_MODE_HEARTBEAT) {
        // 心跳模式：1秒亮，1秒暗，1秒亮（快速），1秒暗
        switch (priv->heartbeat_phase) {
        case 0:  // 亮1秒
            gpiod_set_value(priv->led_gpiod, 1);
            priv->led_cdev.brightness = 255;
            mod_timer(&priv->heartbeat_timer, jiffies + HZ);
            break;
        case 1:  // 暗1秒
            gpiod_set_value(priv->led_gpiod, 0);
            priv->led_cdev.brightness = 0;
            mod_timer(&priv->heartbeat_timer, jiffies + HZ);
            break;
        case 2:  // 亮500ms（快速）
            gpiod_set_value(priv->led_gpiod, 1);
            priv->led_cdev.brightness = 255;
            mod_timer(&priv->heartbeat_timer, jiffies + HZ/2);
            break;
        case 3:  // 暗1秒，然后重置
            gpiod_set_value(priv->led_gpiod, 0);
            priv->led_cdev.brightness = 0;
            mod_timer(&priv->heartbeat_timer, jiffies + HZ);
            priv->heartbeat_phase = -1;
            break;
        }
        
        priv->heartbeat_phase++;
    }
}

// 闪烁定时器回调
static void blink_timer_callback(struct timer_list *timer)
{
    struct led_private *priv = from_timer(priv, timer, blink_timer);
    
    if (priv->current_mode == LED_MODE_TIMER) {
        // 切换LED状态
        priv->blink_state = !priv->blink_state;
        gpiod_set_value(priv->led_gpiod, priv->blink_state ? 1 : 0);
        priv->led_cdev.brightness = priv->blink_state ? 255 : 0;
        
        // 重新启动定时器
        mod_timer(&priv->blink_timer, jiffies + HZ/2);
    }
}

// 工作队列处理函数
static void led_work_handler(struct work_struct *work)
{
    struct led_private *priv = container_of(work, struct led_private, led_work);
    
    // 切换LED模式
    led_mode_t next_mode = (priv->current_mode + 1) % LED_MODE_MAX;
    set_led_mode(priv, next_mode);
    
    LED_INFO("按键触发: LED模式切换为 %s\n", led_mode_names[next_mode]);
}

// 输入事件通知处理函数（暂时未使用）
/*
static int input_event_notifier(struct notifier_block *nb, unsigned long event, void *data)
{
    // 只处理输入事件通知
    if (event != INPUT_KOBJ_NOTIFY_EVENTS) {
        return NOTIFY_OK;
    }
    
    // 输入事件通过input_handler的event方法传递，这里不再需要
    
    return NOTIFY_OK;
}
*/

// 输入处理函数
static int led_input_connect(struct input_handler *handler, struct input_dev *dev, const struct input_device_id *id)
{
    struct led_private *priv = container_of(handler, struct led_private, input_handler);
    struct input_handle *handle;
    int error;
    
    // 创建input_handle
    handle = kzalloc(sizeof(*handle), GFP_KERNEL);
    if (!handle) {
        return -ENOMEM;
    }
    
    handle->dev = dev;
    handle->handler = handler;
    handle->name = "led_input";
    handle->private = priv;
    
    // 注册input_handle
    error = input_register_handle(handle);
    if (error) {
        kfree(handle);
        return error;
    }
    
    // 打开设备
    error = input_open_device(handle);
    if (error) {
        input_unregister_handle(handle);
        kfree(handle);
        return error;
    }
    
    LED_INFO("已连接到输入设备: %s\n", dev->name);
    
    return 0;
}

// 断开输入设备连接
static void led_input_disconnect(struct input_handle *handle)
{
    // struct led_private *priv = handle->private; // 未使用变量
    
    input_close_device(handle);
    input_unregister_handle(handle);
    kfree(handle);
    
    LED_INFO("已断开与输入设备的连接\n");
}

// 输入事件处理函数
static void led_input_event(struct input_handle *handle, unsigned int type, unsigned int code, int value)
{
    struct led_private *priv = handle->private;
    
    // 检查是否为KEY_VOLUMEUP按键的按下事件
    if (type == EV_KEY && code == KEY_VOLUMEUP && value == 1) {
        // 提交工作队列
        schedule_work(&priv->led_work);
        LED_INFO("检测到音量加按键按下事件\n");
    }
}

// 输入设备ID表（匹配所有支持KEY_VOLUMEUP的设备）
static const struct input_device_id led_input_ids[] = {
    { .evbit = { BIT_MASK(EV_KEY) }, .keybit = { [BIT_WORD(KEY_VOLUMEUP)] = BIT_MASK(KEY_VOLUMEUP) }, },
    { },
};

// 设置亮度回调
static void yuanzi_led_set_brightness(struct led_classdev *led_cdev, 
                                      enum led_brightness brightness)
{
    struct led_private *priv = container_of(led_cdev, struct led_private, led_cdev);
    
    LED_DEBUG("设置亮度: %d, 当前模式: %s\n", 
              brightness, led_mode_names[priv->current_mode]);
    
    // 如果设置为0，切换到OFF模式
    if (brightness == 0) {
        set_led_mode(priv, LED_MODE_OFF);
    } else {
        // 如果当前是OFF模式，切换到ON模式
        if (priv->current_mode == LED_MODE_OFF) {
            set_led_mode(priv, LED_MODE_ON);
        }
        // 直接设置GPIO值（只对ON模式有效）
        if (priv->current_mode == LED_MODE_ON) {
            gpiod_set_value(priv->led_gpiod, 1);
            priv->led_cdev.brightness = brightness;
        }
    }
    
    priv->state.brightness = brightness;
}

// 获取亮度回调
static enum led_brightness led_get_brightness(struct led_classdev *led_cdev)
{
    struct led_private *priv = container_of(led_cdev, struct led_private, led_cdev);
    return priv->state.brightness;
}

// ==================== 自定义trigger支持 ====================

// 自定义trigger激活函数
static int led_trigger_activate(struct led_classdev *led_cdev)
{
    struct led_private *priv = container_of(led_cdev, struct led_private, led_cdev);
    const char *trigger_name = led_cdev->trigger->name;
    
    LED_INFO("激活trigger: %s\n", trigger_name);
    
    // 根据trigger名称设置模式
    if (strcmp(trigger_name, "default-on") == 0) {
        set_led_mode(priv, LED_MODE_ON);
    } else if (strcmp(trigger_name, "heartbeat") == 0) {
        set_led_mode(priv, LED_MODE_HEARTBEAT);
    } else if (strcmp(trigger_name, "timer") == 0) {
        set_led_mode(priv, LED_MODE_TIMER);
    } else if (strcmp(trigger_name, "none") == 0) {
        set_led_mode(priv, LED_MODE_OFF);
    }
    
    return 0;
}

// 自定义trigger注销函数
static void led_trigger_deactivate(struct led_classdev *led_cdev)
{
    struct led_private *priv = container_of(led_cdev, struct led_private, led_cdev);
    
    LED_INFO("注销trigger: %s\n", led_cdev->trigger->name);
    
    // 切换到OFF模式
    set_led_mode(priv, LED_MODE_OFF);
}

// 创建自定义trigger
static struct led_trigger led_custom_trigger = {
    .name = "yuanzi_led",
    .activate = led_trigger_activate,
    .deactivate = led_trigger_deactivate,
};

// ==================== 文件操作结构体 ====================

// IOCTL命令处理函数
static long led_ioctl(struct file *file, unsigned int cmd, unsigned long arg)
{
    struct led_private *priv = (struct led_private *)file->private_data;
    int ret = 0;
    
    switch (cmd) {
    case LED_SET_POWER: {
        int power;
        ret = copy_from_user(&power, (int *)arg, sizeof(int));
        if (ret < 0) {
            return -EFAULT;
        }
        
        if (power) {
            // 开启LED，使用当前模式或默认ON模式
            if (priv->current_mode == LED_MODE_OFF) {
                set_led_mode(priv, LED_MODE_ON);
            }
        } else {
            // 关闭LED
            set_led_mode(priv, LED_MODE_OFF);
        }
        
        LED_INFO("IOCTL设置电源: %s\n", power ? "开启" : "关闭");
        break;
    }
    
    case LED_SET_BRIGHTNESS: {
        int brightness;
        ret = copy_from_user(&brightness, (int *)arg, sizeof(int));
        if (ret < 0) {
            return -EFAULT;
        }
        
        // 确保亮度在有效范围内
        if (brightness < 0) brightness = 0;
        if (brightness > 255) brightness = 255;
        
        // 设置亮度
        yuanzi_led_set_brightness(&priv->led_cdev, brightness);
        
        LED_INFO("IOCTL设置亮度: %d\n", brightness);
        break;
    }
    
    case LED_SET_MODE: {
        char mode_str[32];
        led_mode_t mode = LED_MODE_OFF;
        int i;
        
        ret = copy_from_user(mode_str, (char *)arg, sizeof(mode_str));
        if (ret < 0) {
            return -EFAULT;
        }
        
        mode_str[sizeof(mode_str)-1] = '\0';
        
        // 查找匹配的模式
        for (i = 0; i < LED_MODE_MAX; i++) {
            if (strcmp(mode_str, led_mode_names[i]) == 0) {
                mode = i;
                break;
            }
        }
        
        // 设置模式
        set_led_mode(priv, mode);
        
        LED_INFO("IOCTL设置模式: %s\n", mode_str);
        break;
    }
    
    case LED_GET_STATE: {
        // 更新当前状态
        priv->state.power_on = (priv->current_mode != LED_MODE_OFF);
        priv->state.brightness = priv->led_cdev.brightness;
        
        ret = copy_to_user((struct led_state *)arg, &priv->state, sizeof(struct led_state));
        if (ret < 0) {
            return -EFAULT;
        }
        
        LED_DEBUG("IOCTL获取状态: 电源=%d, 亮度=%d, 模式=%s\n", 
                 priv->state.power_on, priv->state.brightness, priv->state.mode);
        break;
    }
    
    default:
        return -ENOTTY;
    }
    
    return ret;
}

// 打开设备
static int led_open(struct inode *inode, struct file *file)
{
    // 设置私有数据
    file->private_data = led_priv;
    
    LED_DEBUG("设备打开\n");
    return 0;
}

// 关闭设备
static int led_release(struct inode *inode, struct file *file)
{
    LED_DEBUG("设备关闭\n");
    return 0;
}

// 文件操作结构体
static const struct file_operations fops = {
    .owner = THIS_MODULE,
    .open = led_open,
    .release = led_release,
    .unlocked_ioctl = led_ioctl,
    .compat_ioctl = led_ioctl,
};

// ==================== 设备属性文件 ====================

// debug属性文件读取函数
static ssize_t debug_show(struct device *dev, 
                          struct device_attribute *attr, char *buf)
{
    struct led_private *priv = dev_get_drvdata(dev);
    
    return sprintf(buf, "LED驱动调试信息:\n" 
                      "  设备名称: %s\n" 
                      "  GPIO引脚: %d\n" 
                      "  当前模式: %s\n" 
                      "  电源状态: %s\n" 
                      "  亮度值: %d\n", 
                   DEVICE_NAME, 
                   priv->led_gpio, 
                   led_mode_names[priv->current_mode], 
                   (priv->current_mode != LED_MODE_OFF) ? "开启" : "关闭", 
                   priv->led_cdev.brightness);
}

// debug属性文件写入函数
static ssize_t debug_store(struct device *dev, 
                           struct device_attribute *attr, 
                           const char *buf, size_t count)
{
    struct led_private *priv = dev_get_drvdata(dev);
    char cmd[32];
    
    // 解析命令
    sscanf(buf, "%31s", cmd);
    
    if (strcmp(cmd, "reset") == 0) {
        // 重置LED状态
        set_led_mode(priv, LED_MODE_OFF);
        LED_INFO("调试命令: 重置LED状态\n");
    } else if (strcmp(cmd, "toggle") == 0) {
        // 切换LED模式
        led_mode_t next_mode = (priv->current_mode + 1) % LED_MODE_MAX;
        set_led_mode(priv, next_mode);
        LED_INFO("调试命令: 切换模式为 %s\n", led_mode_names[next_mode]);
    }
    
    return count;
}

// 定义debug属性文件
static DEVICE_ATTR_RW(debug);

// ==================== 平台设备驱动 ====================

// 设备初始化函数
static int led_probe(struct platform_device *pdev)
{
    int ret;
    struct device_node *node = pdev->dev.of_node;
    
    LED_INFO("初始化LED驱动...\n");
    
    // 分配私有数据
    led_priv = kzalloc(sizeof(struct led_private), GFP_KERNEL);
    if (!led_priv) {
        LED_ERROR("无法分配私有数据\n");
        return -ENOMEM;
    }
    
    // 从设备树获取GPIO信息
    led_priv->led_gpiod = NULL;
    
    // 1. 优先从设备树获取GPIO descriptor
    if (node) {
        led_priv->led_gpiod = of_get_named_gpiod_flags(node, "led-gpios", 0, GPIOD_OUT_LOW);
        if (IS_ERR(led_priv->led_gpiod)) {
            LED_ERROR("从设备树获取GPIO失败\n");
            led_priv->led_gpiod = NULL;
        } else {
            LED_INFO("从设备树获取GPIO成功\n");
        }
    }
    
    // 2. 如果设备树获取失败或没有设备树，使用传统GPIO接口
    if (!led_priv->led_gpiod) {
        int gpio = -1;
        
        // 尝试从模块参数获取
        if (gpio_is_valid(led_gpio)) {
            gpio = led_gpio;
            LED_INFO("从模块参数获取GPIO: %d\n", gpio);
        } else {
            // 使用默认GPIO
            gpio = 15; // RK_PB7对应GPIO0_B7，数值为15
            LED_INFO("使用默认GPIO: %d (对应RK_PB7, GPIO0_B7)\n", gpio);
        }
        
        if (gpio_is_valid(gpio)) {
            led_priv->led_gpiod = gpio_to_desc(gpio);
            if (led_priv->led_gpiod) {
                int ret_gpio = gpiod_request(led_priv->led_gpiod, "yuanzi_led");
                if (ret_gpio < 0) {
                    LED_ERROR("无法请求LED GPIO\n");
                    led_priv->led_gpiod = NULL;
                } else {
                    gpiod_direction_output(led_priv->led_gpiod, 0);
                }
            }
        }
    }
    
    // 检查GPIO是否有效
    if (!led_priv->led_gpiod) {
        LED_ERROR("无法获取有效的LED GPIO\n");
        ret = -ENODEV;
        goto cleanup_priv;
    }
    
    // 初始化工作队列
    INIT_WORK(&led_priv->led_work, led_work_handler);
    
    // 初始化定时器
    timer_setup(&led_priv->heartbeat_timer, heartbeat_timer_callback, 0);
    timer_setup(&led_priv->blink_timer, blink_timer_callback, 0);
    
    // 从设备树获取可选配置
    
    // 1. LED名称（如果未指定，使用默认值）
    const char *led_name = LED_NAME;
    if (node) {
        of_property_read_string(node, "led-name", &led_name);
    }
    
    // 2. 默认亮度值（如果未指定，使用默认值0）
    u32 default_brightness = 0;
    if (node) {
        of_property_read_u32(node, "led-default-brightness", &default_brightness);
    }
    if (default_brightness > 255) {
        default_brightness = 255;
    }
    
    // 3. 默认LED模式（如果未指定，使用默认值"none"）
    const char *default_mode = "none";
    if (node) {
        of_property_read_string(node, "led-default-mode", &default_mode);
    }
    
    // 初始化LED类设备
    led_priv->led_cdev.name = led_name;
    led_priv->led_cdev.brightness = default_brightness;
    led_priv->led_cdev.max_brightness = 255;
    led_priv->led_cdev.brightness_set = yuanzi_led_set_brightness;
    led_priv->led_cdev.brightness_get = led_get_brightness;
    led_priv->led_cdev.default_trigger = default_mode;
    
    // 注册LED类设备
    ret = led_classdev_register(&pdev->dev, &led_priv->led_cdev);
    if (ret < 0) {
        LED_ERROR("无法注册LED类设备\n");
        goto cleanup_led_gpio;
    }
    
    // 注册自定义trigger
    ret = led_trigger_register(&led_custom_trigger);
    if (ret) {
        LED_INFO("无法注册自定义trigger\n");
    }
    
    // 初始化输入处理程序
    led_priv->input_handler.name = "led_input_handler";
    led_priv->input_handler.event = led_input_event;
    led_priv->input_handler.connect = led_input_connect;
    led_priv->input_handler.disconnect = led_input_disconnect;
    led_priv->input_handler.id_table = led_input_ids;
    
    // 注册输入处理程序
    ret = input_register_handler(&led_priv->input_handler);
    if (ret) {
        LED_ERROR("无法注册输入处理程序\n");
        goto cleanup_led_trigger;
    }
    
    // ==================== 初始化字符设备 ====================
    
    // 分配设备号
    ret = alloc_chrdev_region(&led_priv->dev_num, 0, 1, DEVICE_NAME);
    if (ret < 0) {
        LED_ERROR("无法分配设备号\n");
        goto cleanup_input_handler;
    }
    
    // 初始化cdev
    cdev_init(&led_priv->cdev, &fops);
    led_priv->cdev.owner = THIS_MODULE;
    
    // 添加cdev到系统
    ret = cdev_add(&led_priv->cdev, led_priv->dev_num, 1);
    if (ret < 0) {
        LED_ERROR("无法添加cdev\n");
        goto cleanup_chrdev;
    }
    
    // 创建设备类
    led_priv->class = class_create(THIS_MODULE, CLASS_NAME);
    if (IS_ERR(led_priv->class)) {
        LED_ERROR("无法创建设备类\n");
        ret = PTR_ERR(led_priv->class);
        goto cleanup_cdev;
    }
    
    // 创建设备节点
    led_priv->device = device_create(led_priv->class, NULL, led_priv->dev_num, 
                                     NULL, DEVICE_NAME);
    if (IS_ERR(led_priv->device)) {
        LED_ERROR("无法创建设备节点\n");
        ret = PTR_ERR(led_priv->device);
        goto cleanup_class;
    }
    
    // 创建设备属性文件
    ret = device_create_file(led_priv->device, &dev_attr_debug);
    if (ret) {
        LED_INFO("无法创建设备属性文件\n");
    }
    
    // 设置设备权限（666）
    ret = sysfs_chmod_file(&led_priv->device->kobj,
                          &dev_attr_debug.attr,
                          0666);
    if (ret) {
        LED_INFO("无法设置设备权限\n");
    }
    
    // 初始化状态
    led_priv->state.power_on = 0;
    led_priv->state.brightness = 0;
    strncpy(led_priv->state.mode, "none", sizeof(led_priv->state.mode) - 1);
    led_priv->state.mode[sizeof(led_priv->state.mode) - 1] = '\0';
    led_priv->current_mode = LED_MODE_OFF;
    
    // 设置平台设备数据
    platform_set_drvdata(pdev, led_priv);
    
    LED_INFO("LED驱动初始化完成！\n");
    LED_INFO("字符设备节点: /dev/%s\n", DEVICE_NAME);
    LED_INFO("sysfs节点: /sys/class/leds/%s/\n", LED_NAME);
    LED_INFO("支持的trigger模式: none, default-on, heartbeat, timer\n");
    
    return 0;
    
    // 错误处理路径
cleanup_class:
    class_destroy(led_priv->class);
cleanup_cdev:
    cdev_del(&led_priv->cdev);
cleanup_chrdev:
    unregister_chrdev_region(led_priv->dev_num, 1);
cleanup_input_handler:
    input_unregister_handler(&led_priv->input_handler);
cleanup_led_trigger:
    led_trigger_unregister(&led_custom_trigger);
cleanup_led_gpio:
    gpio_free(led_priv->led_gpio);
cleanup_priv:
    kfree(led_priv);
    
    return ret;
}

// 设备移除函数
static int led_remove(struct platform_device *pdev)
{
    struct led_private *priv = platform_get_drvdata(pdev);
    
    LED_INFO("移除LED驱动...\n");
    
    // 移除设备属性文件
    device_remove_file(priv->device, &dev_attr_debug);
    
    // 移除字符设备
    device_destroy(priv->class, priv->dev_num);
    class_destroy(priv->class);
    cdev_del(&priv->cdev);
    unregister_chrdev_region(priv->dev_num, 1);
    
    // 注销输入处理程序
    input_unregister_handler(&priv->input_handler);
    
    // 注销trigger
    led_trigger_unregister(&led_custom_trigger);
    
    // 注销LED类设备
    led_classdev_unregister(&priv->led_cdev);
    
    // 取消定时器
    del_timer_sync(&priv->heartbeat_timer);
    del_timer_sync(&priv->blink_timer);
    
    // 释放GPIO descriptor
        gpiod_put(priv->led_gpiod);
    
    // 释放私有数据
    kfree(priv);
    
    LED_INFO("LED驱动已移除！\n");
    
    return 0;
}

// 设备树匹配表
static const struct of_device_id led_of_match[] = {
    { .compatible = "yuanzi,led-driver", },
    {},
};

MODULE_DEVICE_TABLE(of, led_of_match);

// 平台驱动结构体
static struct platform_driver led_driver = {
    .probe = led_probe,
    .remove = led_remove,
    .driver = {
        .name = DEVICE_NAME,
        .of_match_table = led_of_match,
    },
};

// ==================== 直接注册字符设备支持 ====================

static int __init led_driver_init(void)
{
    int ret;
    
    LED_INFO("注册LED平台驱动...\n");
    
    // 尝试作为platform驱动注册
    ret = platform_driver_register(&led_driver);
    if (ret == 0) {
        LED_INFO("LED平台驱动注册成功！\n");
        return 0;
    }
    
    LED_ERROR("平台驱动注册失败: %d\n", ret);
    LED_INFO("如果是无设备树环境，可尝试手动加载驱动并指定GPIO引脚\n");
    LED_INFO("示例: insmod yuanzi_led.ko led_gpio=15\n");
    LED_INFO("默认GPIO: 15 (对应RK_PB7, GPIO0_B7)\n");
    
    return ret;
}

static void __exit led_driver_exit(void)
{
    LED_INFO("注销LED平台驱动...\n");
    platform_driver_unregister(&led_driver);
}

module_init(led_driver_init);
module_exit(led_driver_exit);

// 模块信息
MODULE_LICENSE("GPL");
MODULE_AUTHOR("Yuanzi LED Driver Team");
MODULE_DESCRIPTION("RK3568 Yuanzi LED驱动，支持sysfs接口和字符设备接口");
MODULE_VERSION("1.0");