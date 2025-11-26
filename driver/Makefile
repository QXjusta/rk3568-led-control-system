# 内核源码路径 - 需要根据实际情况修改
KERNEL_DIR := /home/ubuntu/rk3588/kernel

# 当前驱动目录
CURRENT_DIR := $(shell pwd)

# 驱动模块名称
MODULE_NAME := zhangsan_led

# 编译目标
obj-m := $(MODULE_NAME).o

# 编译命令
all:
	$(MAKE) -C $(KERNEL_DIR) M=$(CURRENT_DIR) modules

# 清理命令
clean:
	$(MAKE) -C $(KERNEL_DIR) M=$(CURRENT_DIR) clean

# 安装命令
install:
	insmod $(MODULE_NAME).ko

# 卸载命令
uninstall:
	rmmod $(MODULE_NAME)
