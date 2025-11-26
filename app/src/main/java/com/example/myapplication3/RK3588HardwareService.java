package com.example.myapplication3;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * RK3588硬件通信服务
 * 提供串口方式与RK3588开发板通信
 */
public class RK3588HardwareService extends Service {
    private static final String TAG = "RK3588HardwareService";
    
    // 静态代码块中加载原生库（更可靠的方式）
    static {
        try {
            Log.d(TAG, "开始加载原生库...");
            System.loadLibrary("rk3588_hardware");
            Log.d(TAG, "原生库加载成功（静态代码块）");
        } catch (UnsatisfiedLinkError e) {
            Log.e(TAG, "原生库加载失败（静态代码块）: " + e.getMessage());
            Log.e(TAG, "请检查以下可能的原因：");
            Log.e(TAG, "1. 原生库文件是否存在于APK中");
            Log.e(TAG, "2. 原生库是否针对正确的CPU架构编译");
            Log.e(TAG, "3. 原生库依赖的其他库是否可用");
            e.printStackTrace();
        } catch (Exception e) {
            Log.e(TAG, "加载原生库时发生异常: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    // 通信模式
    public static final int MODE_SERIAL = 1;
    
    // 默认配置
    private static final String DEFAULT_SERIAL_PORT = "/dev/ttyS4";
    private static final int DEFAULT_BAUD_RATE = 115200;
    
    // 通信状态
    private AtomicBoolean isConnected = new AtomicBoolean(false);
    private AtomicBoolean isRunning = new AtomicBoolean(false);
    private int currentMode = MODE_SERIAL;
    
    // 通信组件
    private SerialPortManager serialPortManager;
    private ExecutorService executorService;
    
    // 回调接口
    private HardwareCallback hardwareCallback;
    
    // 状态监听器
    private HardwareStateListener stateListener;
    private Thread stateMonitorThread;
    private volatile boolean shouldMonitorState = false;
    private int gpioState = 0; // GPIO状态
    
    // Binder
    private final IBinder binder = new HardwareBinder();
    
    public interface HardwareCallback {
        void onConnectionStatusChanged(boolean connected);
        void onDataReceived(String data);
        void onError(String error);
        void onHardwareStateChanged(LEDState state);
    }
    
    /**
     * 硬件状态监听器接口
     */
    public interface HardwareStateListener {
        void onGPIOStateChanged(boolean state);
        void onConnectionLost(String reason);
        void onHardwareError(String error);
    }
    
    public class HardwareBinder extends Binder {
        public RK3588HardwareService getService() {
            // 安全检查：确保服务正在运行
            if (!isRunning.get()) {
                Log.w(TAG, "尝试获取已停止的服务实例");
                return null;
            }
            return RK3588HardwareService.this;
        }
    }
    
    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "开始创建硬件通信服务");
        
        try {
            // 初始化线程池
            executorService = Executors.newSingleThreadExecutor();
            
            // 初始化串口管理器
            serialPortManager = new SerialPortManager();
            
            // 初始化状态标志
            isRunning.set(true);
            isConnected.set(false);
            shouldMonitorState = false;
            
            // 原生库已在静态代码块中加载
            Log.d(TAG, "硬件通信服务创建成功");
        } catch (Exception e) {
            Log.e(TAG, "硬件通信服务创建失败: " + e.getMessage());
            // 如果初始化失败，确保资源清理
            if (executorService != null) {
                executorService.shutdownNow();
            }
            throw new RuntimeException("硬件通信服务初始化失败", e);
        }
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "硬件通信服务启动");
        return START_STICKY;
    }
    
    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "服务被绑定");
        // 检查服务是否正在运行
        if (!isRunning.get()) {
            Log.w(TAG, "服务绑定但服务未运行");
        }
        return binder;
    }
    
    @Override
    public void onDestroy() {
        Log.d(TAG, "开始销毁硬件通信服务");
        
        // 设置停止标志
        isRunning.set(false);
        isConnected.set(false);
        shouldMonitorState = false;
        
        // 停止状态监控
        stopStateMonitoring();
        
        // 简单直接的资源清理
        try {
            if (serialPortManager != null) {
                serialPortManager.close();
            }
        } catch (Exception e) {
            Log.e(TAG, "关闭串口异常: " + e.getMessage());
        }
        
        // 关闭线程池
        if (executorService != null) {
            try {
                executorService.shutdown();
                if (!executorService.awaitTermination(1, java.util.concurrent.TimeUnit.SECONDS)) {
                    executorService.shutdownNow();
                }
            } catch (Exception e) {
                Log.e(TAG, "关闭线程池异常: " + e.getMessage());
            }
        }
        
        // 调用父类方法
        super.onDestroy();
        Log.d(TAG, "硬件通信服务销毁完成");
    }
    
    /**
     * 连接硬件设备
     */
    public void connect(int mode, String address, int port) {
        // 检查服务是否正在运行
        if (!isRunning.get()) {
            Log.w(TAG, "服务未运行，无法连接硬件设备");
            return;
        }
        
        // 如果已经连接，先断开
        if (isConnected.get()) {
            disconnect();
        }
        
        currentMode = mode;
        
        // 使用同步方式执行连接，避免异步线程导致的Binder问题
        try {
            if (mode == MODE_SERIAL) {
                connectSerial(address, port);
            } else {
                throw new IllegalArgumentException("不支持的连接模式: " + mode);
            }
            
            // 只有在连接成功后才启动状态监控
            if (isConnected.get()) {
                startStateMonitoring();
            }
        } catch (Exception e) {
            Log.e(TAG, "连接失败: " + e.getMessage());
            notifyError("连接失败: " + e.getMessage());
            isConnected.set(false);
        }
    }
    
    /**
     * 串口连接
     */
    public void connectSerial(String port, int baudRate) {
        try {
            if (serialPortManager == null) {
                serialPortManager = new SerialPortManager();
            }
            
            if (serialPortManager.open(port, baudRate)) {
                isConnected.set(true);
                notifyConnectionStatus(true);
                Log.d(TAG, "串口连接成功: " + port + " @ " + baudRate + " baud");
                
                // 开始监听数据
                startDataListening();
            } else {
                throw new RuntimeException("串口打开失败");
            }
        } catch (Exception e) {
            throw new RuntimeException("串口连接失败", e);
        }
    }
    

    
    /**
     * 断开所有连接
     */
    public void disconnectAll() {
        Log.d(TAG, "开始断开所有连接");
        
        // 设置状态标志
        isRunning.set(false);
        isConnected.set(false);
        
        // 停止状态监控
        stopStateMonitoring();
        
        // 简单直接的资源清理
        try {
            if (serialPortManager != null) {
                serialPortManager.close();
                Log.d(TAG, "串口连接已断开");
            }
        } catch (Exception e) {
            Log.e(TAG, "断开串口异常: " + e.getMessage());
        }
        
        notifyConnectionStatus(false);
        Log.d(TAG, "所有连接已断开");
    }
    
    /**
     * 自动重连机制
     */
    public void autoReconnect() {
        new Thread(() -> {
            int retryCount = 0;
            final int maxRetries = 3;
            final long retryInterval = 5000; // 5秒重试间隔
            
            while (retryCount < maxRetries && !Thread.currentThread().isInterrupted()) {
                try {
                    // 尝试重新连接
                    if (isConnected.get() && serialPortManager != null && !serialPortManager.isOpen()) {
                        Log.i(TAG, "尝试重新连接串口 (第" + (retryCount + 1) + "次)");
                        // 这里需要保存之前的连接参数，实际应用中应该存储这些参数
                        String savedPort = "/dev/ttyS1"; // 示例端口
                        int savedBaudRate = 115200; // 示例波特率
                        
                        connectSerial(savedPort, savedBaudRate);
                        if (isConnected.get()) {
                            Log.i(TAG, "串口重连成功");
                            break;
                        }
                    }
                    

                    
                    retryCount++;
                    if (retryCount < maxRetries) {
                        Thread.sleep(retryInterval);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    Log.e(TAG, "重连尝试失败: " + e.getMessage());
                    retryCount++;
                    if (retryCount < maxRetries) {
                        try {
                            Thread.sleep(retryInterval);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                }
            }
            
            if (retryCount >= maxRetries) {
                Log.w(TAG, "自动重连失败，已达到最大重试次数");
                if (stateListener != null) {
                    stateListener.onHardwareError("自动重连失败，请检查硬件连接");
                }
            }
        }).start();
    }
    
    /**
     * 错误处理 - 硬件操作异常
     */
    private void handleHardwareError(String operation, Exception e) {
        String errorMsg = operation + "失败: " + e.getMessage();
        Log.e(TAG, errorMsg);
        
        // 记录错误日志
        Log.e(TAG, "错误: " + errorMsg);
        
        // 通知状态监听器
        if (stateListener != null) {
            stateListener.onHardwareError(errorMsg);
        }
        
        // 根据错误类型决定是否尝试恢复
        if (e instanceof IOException) {
            // 如果是IO异常，可能是连接问题，尝试自动重连
            Log.i(TAG, "检测到IO异常，启动自动重连");
            autoReconnect();
        }
    }
    
    /**
     * 发送控制命令（带错误处理）
     */
    public boolean sendControlCommand(String command) {
        try {
            if (isConnected.get() && serialPortManager != null) {
                byte[] data = command.getBytes("UTF-8");
                boolean result = serialPortManager.writeData(data);
                if (result) {
                    Log.d(TAG, "串口命令发送成功: " + command);
                    return true;
                } else {
                    throw new IOException("串口写入不完整");
                }
            } else {
                throw new IOException("无可用连接");
            }
        } catch (Exception e) {
            handleHardwareError("发送控制命令", e);
            return false;
        }
    }
    
    /**
     * 设置状态监听器
     */
    public void setStateListener(HardwareStateListener listener) {
        this.stateListener = listener;
        if (listener != null && !shouldMonitorState) {
            startStateMonitoring();
        }
    }
    
    /**
     * 启动状态监控
     */
    private void startStateMonitoring() {
        // 检查硬件是否已正确初始化，如果没有则延迟启动
        if (!isHardwareInitialized()) {
            Log.w(TAG, "硬件未正确初始化，延迟启动状态监控");
            // 延迟5秒后重试
            new Thread(() -> {
                try {
                    Thread.sleep(5000);
                    if (isConnected.get() && shouldMonitorState) {
                        startStateMonitoring();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }).start();
            return;
        }
        
        shouldMonitorState = true;
        stateMonitorThread = new Thread(() -> {
            while (shouldMonitorState) {
                try {
                    // 只有在硬件连接成功后才开始监控GPIO状态
                    if (isConnected.get()) {
                        // 监控GPIO状态
                        int currentGPIOState = readGPIOState(0); // 监控GPIO0
                        if (currentGPIOState != gpioState) {
                            gpioState = currentGPIOState;
                            if (stateListener != null) {
                                stateListener.onGPIOStateChanged(gpioState != 0);
                            }
                        }
                        
                        // 检查连接状态
                        if (serialPortManager != null && !serialPortManager.isOpen()) {
                            isConnected.set(false);
                            if (stateListener != null) {
                                stateListener.onConnectionLost("串口连接已断开");
                            }
                        }
                        

                    }
                    
                    Thread.sleep(1000); // 每秒检查一次
                } catch (Exception e) {
                    Log.e(TAG, "状态监控异常: " + e.getMessage());
                    try {
                        Thread.sleep(5000); // 异常时等待5秒
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        });
        stateMonitorThread.start();
        Log.d(TAG, "状态监控已启动");
    }
    
    /**
     * 检查硬件是否已正确初始化
     */
    private boolean isHardwareInitialized() {
        try {
            // 尝试读取一个简单的硬件状态来验证初始化状态
            int testState = readGPIOState(0);
            // 如果读取成功且没有异常，说明硬件已正确初始化
            return true;
        } catch (Exception e) {
            Log.w(TAG, "硬件未正确初始化: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * 停止状态监控
     */
    private void stopStateMonitoring() {
        shouldMonitorState = false;
        if (stateMonitorThread != null) {
            try {
                // 先中断线程
                stateMonitorThread.interrupt();
                
                // 等待线程结束，最多等待3秒
                if (stateMonitorThread.isAlive()) {
                    stateMonitorThread.join(3000);
                }
                
                // 如果线程仍然存活，强制中断
                if (stateMonitorThread.isAlive()) {
                    Log.w(TAG, "状态监控线程未能在3秒内停止，可能需要强制处理");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                Log.e(TAG, "停止状态监控时被中断: " + e.getMessage());
            } catch (Exception e) {
                Log.e(TAG, "停止状态监控异常: " + e.getMessage());
            } finally {
                stateMonitorThread = null;
            }
        }
    }
    

    
    /**
     * 断开连接
     */
    public void disconnect() {
        Log.d(TAG, "开始断开所有硬件连接");
        
        // 设置连接状态为断开
        isRunning.set(false);
        isConnected.set(false);
        
        // 停止状态监控
        stopStateMonitoring();
        
        // 简单直接的资源清理
        try {
            if (serialPortManager != null) {
                serialPortManager.close();
                Log.d(TAG, "串口连接已关闭");
            }
        } catch (Exception e) {
            Log.e(TAG, "关闭串口异常: " + e.getMessage());
        }
        

        
        notifyConnectionStatus(false);
        Log.d(TAG, "所有硬件连接已断开");
    }
    
    /**
     * 开始数据监听
     */
    private void startDataListening() {
        executorService.execute(() -> {
            while (isRunning.get() && isConnected.get()) {
                try {
                    // 使用非阻塞方式接收数据，避免I/O操作阻塞垃圾回收
                    byte[] dataBytes = receiveDataNonBlocking();
                    if (dataBytes != null && dataBytes.length > 0) {
                        String data = new String(dataBytes);
                        notifyDataReceived(data);
                        processHardwareData(data);
                    }
                    
                    // 使用更短的轮询间隔，但避免过度消耗CPU
                    Thread.sleep(50); // 50ms轮询间隔
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    Log.e(TAG, "数据监听异常: " + e.getMessage());
                    notifyError("数据监听异常: " + e.getMessage());
                    break;
                }
            }
        });
    }
    
    /**
     * 非阻塞方式接收数据
     */
    private byte[] receiveDataNonBlocking() {
        try {
            switch (currentMode) {
                case MODE_SERIAL:
                    if (serialPortManager != null && serialPortManager.isOpen()) {
                        // 检查是否有可用数据，避免阻塞
                        return serialPortManager.readData();
                    }
                    return null;

                default:
                    return null;
            }
        } catch (Exception e) {
            Log.e(TAG, "接收数据失败: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * 接收数据
     */
    private byte[] receiveData() {
        try {
            switch (currentMode) {
                case MODE_SERIAL:
                    if (serialPortManager != null) {
                        byte[] buffer = new byte[1024];
                        byte[] data = serialPortManager.readData();
                        if (data != null && data.length > 0) {
                            return data;
                        }
                    }
                    return null;

                default:
                    return null;
            }
        } catch (Exception e) {
            Log.e(TAG, "接收数据失败: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * 发送数据到硬件
     */
    public void sendData(String data) {
        if (!isConnected.get()) {
            notifyError("未连接到硬件设备");
            return;
        }
        
        executorService.execute(() -> {
            try {
                boolean success = false;
                switch (currentMode) {
                    case MODE_SERIAL:
                        if (serialPortManager != null) {
                            byte[] dataBytes = data.getBytes();
                            serialPortManager.writeData(dataBytes);
                            success = true;
                        }
                        break;

                }
                
                if (success) {
                    Log.d(TAG, "数据发送成功: " + data);
                } else {
                    notifyError("数据发送失败");
                }
            } catch (Exception e) {
                Log.e(TAG, "发送数据异常: " + e.getMessage());
                notifyError("发送数据异常: " + e.getMessage());
            }
        });
    }
    
    /**
     * 处理硬件数据
     */
    private void processHardwareData(String data) {
        // 解析硬件状态数据
        // 格式示例: "LED:ON,BRIGHTNESS:80,COLOR:WHITE"
        try {
            LEDState state = parseLEDState(data);
            if (state != null) {
                notifyHardwareStateChanged(state);
            }
        } catch (Exception e) {
            Log.e(TAG, "硬件数据处理异常: " + e.getMessage());
        }
    }
    
    /**
     * 解析LED状态
     */
    private LEDState parseLEDState(String data) {
        // 简单解析实现，实际应根据硬件协议调整
        if (data.contains("LED:")) {
            LEDState state = new LEDState();
            String[] parts = data.split(",");
            for (String part : parts) {
                String[] keyValue = part.split(":");
                if (keyValue.length == 2) {
                    switch (keyValue[0].trim()) {
                        case "LED":
                            state.powerOn = "ON".equals(keyValue[1].trim());
                            break;
                        case "BRIGHTNESS":
                            state.brightness = Integer.parseInt(keyValue[1].trim());
                            break;
                    }
                }
            }
            return state;
        }
        return null;
    }
    
    // Native method declarations for JNI linkage
    public native boolean openLEDDevice();
    public native void closeLEDDevice();
    public native boolean setLEDPower(boolean powerOn);
    public native boolean setLEDBrightness(int brightness);
    public native LEDState getLEDState();
    public native int readGPIOState(int gpioPin);
    public native boolean initializeHardware();
    public native boolean checkDeviceNode(String devicePath);
    public native boolean checkDevicePermissions(String devicePath);
    public native boolean setDevicePermissions(String devicePath, int mode);
    public native boolean checkRootPermission();
    public native boolean controlWorkLED(boolean enable);
    public native boolean setWorkLEDBrightness(int brightness);
    public native boolean setWorkLEDMode(String mode);
    
    // 通知方法
    private void notifyConnectionStatus(boolean connected) {
        if (hardwareCallback != null) {
            hardwareCallback.onConnectionStatusChanged(connected);
        }
    }
    
    private void notifyDataReceived(String data) {
        if (hardwareCallback != null) {
            hardwareCallback.onDataReceived(data);
        }
    }
    
    private void notifyError(String error) {
        if (hardwareCallback != null) {
            hardwareCallback.onError(error);
        }
    }
    
    private void notifyHardwareStateChanged(LEDState state) {
        if (hardwareCallback != null) {
            hardwareCallback.onHardwareStateChanged(state);
        }
    }
    
    // Setter方法
    public void setHardwareCallback(HardwareCallback callback) {
        this.hardwareCallback = callback;
    }
    
    public boolean isConnected() {
        return isConnected.get();
    }
    
    public int getCurrentMode() {
        return currentMode;
    }
    
    /**
     * LED状态类
     */
    public static class LEDState {
        public boolean powerOn = false;
        public int brightness = 50;
        public String mode = "NORMAL";
        public int workBrightness = 0;
        public int mmc2Brightness = 0;
        public boolean workFound = false;
        public boolean mmc2Found = false;
        
        @Override
        public String toString() {
            return "LEDState{" +
                    "powerOn=" + powerOn +
                    ", brightness=" + brightness +
                    ", mode='" + mode + '\'' +
                    ", workBrightness=" + workBrightness +
                    ", mmc2Brightness=" + mmc2Brightness +
                    ", workFound=" + workFound +
                    ", mmc2Found=" + mmc2Found +
                    '}';
        }
    }
}

/**
 * 串口管理器
 */


