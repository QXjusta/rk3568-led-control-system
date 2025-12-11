#!/bin/bash

# RK3568 LED驱动镜像生成脚本

# 设置交叉编译工具链
export ARCH=arm64
export CROSS_COMPILE=aarch64-linux-gnu-

# 项目路径
PROJECT_PATH="d:\MyApplication3"
DRIVER_DIR="${PROJECT_PATH}/driver/led-class"
KERNEL_DIR="${PROJECT_PATH}/driver/kernel"

# 设备树文件
DTB_FILE="rk3568-evb1-ddr4-v10-linux.dtb"

# 编译驱动
echo "=== 编译LED驱动 ==="
cd ${DRIVER_DIR}
make ARCH=arm64 CROSS_COMPILE=aarch64-linux-gnu-

# 编译内核
echo "\n=== 编译内核 ==="
cd ${KERNEL_DIR}
make ARCH=arm64 CROSS_COMPILE=aarch64-linux-gnu- -j$(nproc)

# 编译设备树
echo "\n=== 编译设备树 ==="
make ARCH=arm64 CROSS_COMPILE=aarch64-linux-gnu- dtbs

# 生成完整镜像
echo "\n=== 生成完整镜像 ==="

# 复制驱动模块到内核模块目录
mkdir -p ${KERNEL_DIR}/drivers/leds/
cp ${DRIVER_DIR}/yuanzi_led.ko ${KERNEL_DIR}/drivers/leds/

# 生成Image.lz4（如果需要）
echo "\n=== 生成压缩内核镜像 ==="
cd ${KERNEL_DIR}
if [ ! -f arch/arm64/boot/Image.lz4 ]; then
    lz4 -k arch/arm64/boot/Image arch/arm64/boot/Image.lz4
fi

# 生成资源文件和完整镜像
echo "\n=== 生成资源文件和完整镜像 ==="
cd ${KERNEL_DIR}
mkdir -p out
./scripts/mkimg --dtb ${DTB_FILE}

# 复制生成的镜像文件到项目根目录
echo "\n=== 复制生成的镜像文件 ==="
cp ${KERNEL_DIR}/out/boot.img ${PROJECT_PATH}/
cp ${KERNEL_DIR}/out/zboot.img ${PROJECT_PATH}/
cp ${KERNEL_DIR}/out/resource.img ${PROJECT_PATH}/

# 显示生成结果
echo "\n=== 镜像生成完成 ==="
echo "生成的镜像文件："
echo "  - ${PROJECT_PATH}/boot.img"
echo "  - ${PROJECT_PATH}/zboot.img"
echo "  - ${PROJECT_PATH}/resource.img"
echo "\n编译的驱动模块："
echo "  - ${DRIVER_DIR}/yuanzi_led.ko"
echo "\n编译的设备树文件："
echo "  - ${KERNEL_DIR}/arch/arm64/boot/dts/rockchip/${DTB_FILE}"
