package com.example.myapplication3;

// 硬件服务的AIDL接口
interface IRK3588HardwareService {
    // 连接硬件设备
    void connect(int mode, String address, int port);
    
    // 断开连接
    void disconnect();
    
    // 断开所有连接
    void disconnectAll();
    
    // 发送控制命令
    boolean sendControlCommand(String command);
    
    // 发送数据
    void sendData(String data);
    
    // 串口打开
    boolean serialOpen(String port, int baudRate);
    
    // 串口关闭
    void serialClose();
    
    // 检查串口是否打开
    boolean isSerialOpen();
    
    // 串口读取数据
    byte[] serialReadData();
    
    // 串口写入数据
    boolean serialWriteData(in byte[] data);
    
    // 获取当前连接状态
    boolean isConnected();
    
    // 获取当前模式
    int getCurrentMode();
    
    // 获取当前LED状态
    LEDState getCurrentLEDState();
    
    // 打开LED设备
    boolean openLEDDevice();
    
    // 关闭LED设备
    void closeLEDDevice();
    
    // 设置LED电源
    boolean setLEDPower(boolean powerOn);
    
    // 设置LED亮度
    boolean setLEDBrightness(int brightness);
    
    // 获取LED状态
    LEDState getLEDState();
    
    // 读取GPIO状态
    int readGPIOState(int gpioPin);
    
    // 初始化硬件
    boolean initializeHardware();
    
    // 检查设备节点
    boolean checkDeviceNode(String devicePath);
    
    // 检查设备权限
    boolean checkDevicePermissions(String devicePath);
    
    // 设置设备权限
    boolean setDevicePermissions(String devicePath, int mode);
    
    // 检查root权限
    boolean checkRootPermission();
    
    // 控制WORK灯
    boolean controlWorkLED(boolean enable);
    
    // 设置WORK灯亮度
    boolean setWorkLEDBrightness(int brightness);
    
    // 设置WORK灯模式
    boolean setWorkLEDMode(String mode, boolean applyHardware);
    
    // 读取LED状态
    String readLEDState();
    
    // 读取系统信息
    String readSystemInfo();
    
    // 自动重连
    void autoReconnect();
}
