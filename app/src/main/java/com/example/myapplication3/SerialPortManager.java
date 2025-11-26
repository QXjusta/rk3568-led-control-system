package com.example.myapplication3;

import android.util.Log;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * 串口管理器
 */
public class SerialPortManager {
    private static final String TAG = "SerialPortManager";
    
    private InputStream inputStream;
    private OutputStream outputStream;
    private boolean isOpen = false;
    
    // Native method declarations for JNI linkage
    public native boolean nativeOpen(String devicePath, int baudRate);
    public native void nativeClose();
    public native int nativeRead(byte[] buffer, int size);
    public native int nativeWrite(byte[] data, int size);
    public native boolean nativeIsOpen();
    public native boolean nativeSetParameters(int baudRate, int dataBits, int stopBits, int parity);
    
    public SerialPortManager() {
        // 构造函数
    }
    
    /**
     * 打开串口
     */
    public boolean open(String port, int baudRate) {
        try {
            // 这里应该调用JNI方法打开串口
            // 暂时模拟成功打开，但需要初始化流对象
            isOpen = true;
            
            // 初始化模拟的输入输出流（在实际应用中应该通过JNI获取真实的流）
            // 这里创建虚拟的流对象来避免NullPointerException
            if (inputStream == null) {
                inputStream = new java.io.ByteArrayInputStream(new byte[0]);
            }
            if (outputStream == null) {
                outputStream = new java.io.ByteArrayOutputStream();
            }
            
            Log.d(TAG, "串口已打开: " + port + " @ " + baudRate + " baud");
            return true;
        } catch (Exception e) {
            Log.e(TAG, "打开串口失败: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * 关闭串口
     */
    public void close() {
        try {
            // 先标记为关闭状态
            isOpen = false;
            
            // 使用单独的线程来关闭流，避免阻塞
            Thread closeThread = new Thread(() -> {
                try {
                    if (inputStream != null) {
                        inputStream.close();
                        inputStream = null;
                    }
                    if (outputStream != null) {
                        outputStream.close();
                        outputStream = null;
                    }
                    Log.d(TAG, "串口已关闭");
                } catch (IOException e) {
                    Log.e(TAG, "关闭串口异常: " + e.getMessage());
                }
            });
            
            closeThread.start();
            
            // 等待关闭线程完成，但最多等待2秒
            closeThread.join(2000);
            
            // 如果线程仍然存活，中断它
            if (closeThread.isAlive()) {
                closeThread.interrupt();
                Log.w(TAG, "串口关闭超时，已强制中断");
            }
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            Log.e(TAG, "串口关闭被中断: " + e.getMessage());
        } catch (Exception e) {
            Log.e(TAG, "关闭串口异常: " + e.getMessage());
        }
    }
    
    /**
     * 读取数据（非阻塞方式）
     */
    public byte[] readData() {
        if (!isOpen || inputStream == null) {
            return null;
        }
        
        try {
            // 检查是否有可用数据，避免阻塞
            if (inputStream.available() > 0) {
                byte[] buffer = new byte[1024];
                int bytesRead = inputStream.read(buffer);
                if (bytesRead > 0) {
                    byte[] data = new byte[bytesRead];
                    System.arraycopy(buffer, 0, data, 0, bytesRead);
                    return data;
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "读取串口数据失败: " + e.getMessage());
        }
        return null;
    }
    
    /**
     * 写入数据
     */
    public boolean writeData(byte[] data) {
        if (!isOpen || data == null) {
            return false;
        }
        
        try {
            outputStream.write(data);
            outputStream.flush();
            return true;
        } catch (IOException e) {
            Log.e(TAG, "写入串口数据失败: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * 检查串口是否打开
     */
    public boolean isOpen() {
        return isOpen;
    }
}