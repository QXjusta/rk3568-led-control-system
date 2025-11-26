cmake_minimum_required(VERSION 3.18.1)

# 设置项目名称
project("rk3588_hardware")

# 添加JNI库
find_library(log-lib log)
find_library(android-lib android)

# 创建共享库
add_library(
        rk3588_hardware
        SHARED
        rk3588_hardware_jni.cpp
        device_permissions.cpp
)

# 链接库
target_link_libraries(
        rk3588_hardware
        ${log-lib}
        ${android-lib}
)

# 设置C++标准
set(CMAKE_CXX_STANDARD 11)
set(CMAKE_CXX_STANDARD_REQUIRED true)

# 添加编译选项
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -Wall -Wextra")