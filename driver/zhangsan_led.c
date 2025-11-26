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

// 设备名称 - 使用组长名字命名
#define DEVICE_NAME "zhangsan_led"
#define CLASS_NAME  "led_class"

// IOCTL命令定义
#define LED_MAGIC 'L'
#define LED_SET_POWER _IOW(LED_MAGIC, 1, int)
#define LED_SET_BRIGHTNESS _IOW(LED_MAGIC, 2, int)
#define LED_GET_STATE _IOR(LED_MAGIC, 3, struct led_state)

// LED状态结构体
struct led_state {
    int power_on;
    int brightness;
    char mode[32];
};

// 驱动结构体
struct led_driver {
    dev_t dev_num;
    struct cdev cdev;
    struct class *class;
    struct device *device;
    int led_gpio;
    int button_gpio;
    int irq_number;
    struct led_state state;
    bool button_pressed;
};

static struct led_driver led_dev;

// 按键中断处理函数
static irqreturn_t button_irq_handler(int irq, void *dev_id)
{
    struct led_driver *dev = (struct led_driver *)dev_id;
    
    // 防抖处理
    msleep(20);
    
    // 检查按键状态
    if (gpio_get_value(dev->button_gpio) == 0) {
        // 按键按下，切换LED状态
        dev->state.power_on = !dev->state.power_on;
        
        // 更新LED状态
        if (dev->state.power_on) {
            gpio_set_value(dev->led_gpio, 1);
            strcpy(dev->state.mode, "default-on");
        } else {
            gpio_set_value(dev->led_gpio, 0);
            strcpy(dev->state.mode, "off");
        }
        
        printk(KERN_INFO "[LED] 按键触发: LED状态切换为 %s\n", 
               dev->state.power_on ? "ON" : "OFF");
    }
    
    return IRQ_HANDLED;
}

// 设备打开函数
static int led_open(struct inode *inode, struct file *file)
{
    file->private_data = &led_dev;
    printk(KERN_INFO "[LED] 设备已打开\n");
    return 0;
}

// 设备关闭函数
static int led_release(struct inode *inode, struct file *file)
{
    printk(KERN_INFO "[LED] 设备已关闭\n");
    return 0;
}

// IOCTL处理函数
static long led_ioctl(struct file *file, unsigned int cmd, unsigned long arg)
{
    struct led_driver *dev = (struct led_driver *)file->private_data;
    struct led_state state;
    int ret;
    
    switch (cmd) {
        case LED_SET_POWER:
            ret = copy_from_user(&dev->state.power_on, (int *)arg, sizeof(int));
            if (ret) return -EFAULT;
            
            // 更新LED状态
            gpio_set_value(dev->led_gpio, dev->state.power_on);
            if (dev->state.power_on) {
                strcpy(dev->state.mode, "default-on");
            } else {
                strcpy(dev->state.mode, "off");
            }
            
            printk(KERN_INFO "[LED] 设置电源状态: %s\n", 
                   dev->state.power_on ? "ON" : "OFF");
            break;
            
        case LED_SET_BRIGHTNESS:
            ret = copy_from_user(&dev->state.brightness, (int *)arg, sizeof(int));
            if (ret) return -EFAULT;
            
            // 简单处理：亮度大于0则开启LED，否则关闭
            if (dev->state.brightness > 0) {
                dev->state.power_on = 1;
                gpio_set_value(dev->led_gpio, 1);
                strcpy(dev->state.mode, "default-on");
            } else {
                dev->state.power_on = 0;
                gpio_set_value(dev->led_gpio, 0);
                strcpy(dev->state.mode, "off");
            }
            
            printk(KERN_INFO "[LED] 设置亮度: %d\n", dev->state.brightness);
            break;
            
        case LED_GET_STATE:
            // 读取当前GPIO状态，确保状态同步
            dev->state.power_on = gpio_get_value(dev->led_gpio);
            
            ret = copy_to_user((struct led_state *)arg, &dev->state, sizeof(struct led_state));
            if (ret) return -EFAULT;
            
            printk(KERN_INFO "[LED] 获取状态: 电源=%s, 亮度=%d, 模式=%s\n", 
                   dev->state.power_on ? "ON" : "OFF", 
                   dev->state.brightness, dev->state.mode);
            break;
            
        default:
            return -ENOTTY;
    }
    
    return 0;
}

// 文件操作结构体
static const struct file_operations fops = {
    .owner = THIS_MODULE,
    .open = led_open,
    .release = led_release,
    .unlocked_ioctl = led_ioctl,
};

// 设备树匹配表
static const struct of_device_id led_of_match[] = {
    { .compatible = "zhangsan,led", },
    {},
};
MODULE_DEVICE_TABLE(of, led_of_match);

// 平台设备探测函数
static int led_probe(struct platform_device *pdev)
{
    int ret;
    struct device_node *node = pdev->dev.of_node;
    
    printk(KERN_INFO "[LED] 开始探测LED设备...\n");
    
    // 从设备树获取GPIO信息
    led_dev.led_gpio = of_get_named_gpio(node, "led-gpios", 0);
    if (led_dev.led_gpio < 0) {
        printk(KERN_ERR "[LED] 无法获取LED GPIO\n");
        return -EINVAL;
    }
    
    led_dev.button_gpio = of_get_named_gpio(node, "button-gpios", 0);
    if (led_dev.button_gpio < 0) {
        printk(KERN_ERR "[LED] 无法获取按键GPIO\n");
        return -EINVAL;
    }
    
    printk(KERN_INFO "[LED] LED GPIO: %d, 按键GPIO: %d\n", 
           led_dev.led_gpio, led_dev.button_gpio);
    
    // 申请GPIO资源
    ret = gpio_request(led_dev.led_gpio, "led_gpio");
    if (ret) {
        printk(KERN_ERR "[LED] 无法申请LED GPIO\n");
        return ret;
    }
    
    ret = gpio_request(led_dev.button_gpio, "button_gpio");
    if (ret) {
        printk(KERN_ERR "[LED] 无法申请按键GPIO\n");
        gpio_free(led_dev.led_gpio);
        return ret;
    }
    
    // 设置GPIO方向
    ret = gpio_direction_output(led_dev.led_gpio, 0);
    if (ret) {
        printk(KERN_ERR "[LED] 无法设置LED GPIO方向\n");
        goto cleanup_gpio;
    }
    
    ret = gpio_direction_input(led_dev.button_gpio);
    if (ret) {
        printk(KERN_ERR "[LED] 无法设置按键GPIO方向\n");
        goto cleanup_gpio;
    }
    
    // 获取按键中断号
    led_dev.irq_number = gpio_to_irq(led_dev.button_gpio);
    if (led_dev.irq_number < 0) {
        printk(KERN_ERR "[LED] 无法获取按键中断号\n");
        goto cleanup_gpio;
    }
    
    // 注册中断处理函数
    ret = request_irq(led_dev.irq_number, button_irq_handler, 
                     IRQF_TRIGGER_FALLING | IRQF_ONESHOT, 
                     "button_irq", &led_dev);
    if (ret) {
        printk(KERN_ERR "[LED] 无法注册按键中断\n");
        goto cleanup_gpio;
    }
    
    // 初始化LED状态
    led_dev.state.power_on = 0;
    led_dev.state.brightness = 0;
    strcpy(led_dev.state.mode, "off");
    led_dev.button_pressed = false;
    
    // 分配设备号
    ret = alloc_chrdev_region(&led_dev.dev_num, 0, 1, DEVICE_NAME);
    if (ret < 0) {
        printk(KERN_ERR "[LED] 无法分配设备号\n");
        goto cleanup_irq;
    }
    
    // 初始化cdev
    cdev_init(&led_dev.cdev, &fops);
    led_dev.cdev.owner = THIS_MODULE;
    
    // 添加cdev到系统
    ret = cdev_add(&led_dev.cdev, led_dev.dev_num, 1);
    if (ret < 0) {
        printk(KERN_ERR "[LED] 无法添加cdev\n");
        goto cleanup_chrdev;
    }
    
    // 创建类
    led_dev.class = class_create(THIS_MODULE, CLASS_NAME);
    if (IS_ERR(led_dev.class)) {
        printk(KERN_ERR "[LED] 无法创建类\n");
        goto cleanup_cdev;
    }
    
    // 创建设备节点
    led_dev.device = device_create(led_dev.class, NULL, led_dev.dev_num, NULL, DEVICE_NAME);
    if (IS_ERR(led_dev.device)) {
        printk(KERN_ERR "[LED] 无法创建设备节点\n");
        goto cleanup_class;
    }
    
    printk(KERN_INFO "[LED] LED驱动初始化成功！设备节点: /dev/%s\n", DEVICE_NAME);
    return 0;
    
cleanup_class:
    class_destroy(led_dev.class);
cleanup_cdev:
    cdev_del(&led_dev.cdev);
cleanup_chrdev:
    unregister_chrdev_region(led_dev.dev_num, 1);
cleanup_irq:
    free_irq(led_dev.irq_number, &led_dev);
cleanup_gpio:
    gpio_free(led_dev.button_gpio);
    gpio_free(led_dev.led_gpio);
    
    return ret;
}

// 平台设备移除函数
static int led_remove(struct platform_device *pdev)
{
    printk(KERN_INFO "[LED] 移除LED设备...\n");
    
    // 清理资源
    device_destroy(led_dev.class, led_dev.dev_num);
    class_destroy(led_dev.class);
    cdev_del(&led_dev.cdev);
    unregister_chrdev_region(led_dev.dev_num, 1);
    
    free_irq(led_dev.irq_number, &led_dev);
    gpio_free(led_dev.button_gpio);
    gpio_free(led_dev.led_gpio);
    
    printk(KERN_INFO "[LED] LED驱动已移除\n");
    return 0;
}

// 平台驱动结构体
static struct platform_driver led_platform_driver = {
    .driver = {
        .name = DEVICE_NAME,
        .of_match_table = led_of_match,
    },
    .probe = led_probe,
    .remove = led_remove,
};

// 模块初始化函数
static int __init led_init(void)
{
    int ret;
    
    printk(KERN_INFO "[LED] 加载LED驱动模块...\n");
    
    // 注册平台驱动
    ret = platform_driver_register(&led_platform_driver);
    if (ret) {
        printk(KERN_ERR "[LED] 无法注册平台驱动\n");
        return ret;
    }
    
    printk(KERN_INFO "[LED] LED驱动模块加载成功\n");
    return 0;
}

// 模块退出函数
static void __exit led_exit(void)
{
    printk(KERN_INFO "[LED] 卸载LED驱动模块...\n");
    
    // 注销平台驱动
    platform_driver_unregister(&led_platform_driver);
    
    printk(KERN_INFO "[LED] LED驱动模块卸载成功\n");
}

module_init(led_init);
module_exit(led_exit);

MODULE_LICENSE("GPL");
MODULE_AUTHOR("Zhang San");
MODULE_DESCRIPTION("RK3588 LED驱动，支持按键控制");
MODULE_VERSION("1.0");
