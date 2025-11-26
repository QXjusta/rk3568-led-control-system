package com.example.myapplication3;

import android.util.Log;

/**
 * 硬件读取工具类
 * 用于读取RK3588开发板的硬件状态
 */
public class HardwareReader {
    private static final String TAG = "HardwareReader";
    
    // 加载原生库
    static {
        try {
            System.loadLibrary("rk3588_hardware");
            Log.d(TAG, "原生库加载成功");
        } catch (UnsatisfiedLinkError e) {
            Log.e(TAG, "原生库加载失败: " + e.getMessage());
        }
    }
    
    /**
     * 读取LED状态
     */
    public native String readLEDState();
    
    /**
     * 检查设备权限
     */
    public native boolean checkDevicePermissions(String devicePath);
    
    /**
     * 读取系统信息
     */
    public native String readSystemInfo();
}