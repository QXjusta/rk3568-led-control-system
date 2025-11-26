package com.example.myapplication3;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.core.widget.TextViewCompat;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.switchmaterial.SwitchMaterial;

import java.io.DataOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * LED 灯控制面板：模拟文章中提到的控制流程，涵盖开关、亮度、色温/模式与日志。
 * 集成RK3588硬件通信功能
 */
public class MainActivity extends AppCompatActivity {

    
    // Work灯控制相关UI组件
    private SwitchMaterial workLedSwitch;
    private View workLedStatusDot;
    private TextView workLedStatusLabel;
    
    // 亮度控制相关UI组件
    private SeekBar brightnessSeekBar;
    private TextView brightnessValueText;
    
    // 模式控制相关UI组件
    private MaterialCardView modeDefaultOnCard;
    private MaterialCardView modeHeartbeatCard;
    private MaterialCardView modeTimerCard;
    
    // 硬件通信服务
    private RK3588HardwareService hardwareService;
    private boolean isHardwareBound = false;
    
    // 状态监控相关
    private boolean shouldMonitorState = false;
    private Thread stateMonitorThread;
    private static final long STATE_MONITOR_INTERVAL = 1000; // 1秒检测一次，更快响应
    // 应用启动时间记录
    private long appStartTime = 0;
    // 记录用户最后选择的模式
    private String lastUserSelectedMode = "default-on";
    // 记录关闭前的亮度值，用于重新开启时恢复
    private int lastBrightnessBeforeTurnOff = 255;
    
    /**
     * 硬件服务连接回调
     */
    private final ServiceConnection hardwareServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            RK3588HardwareService.HardwareBinder binder = (RK3588HardwareService.HardwareBinder) service;
            hardwareService = binder.getService();
            isHardwareBound = true;
            
            // 设置硬件回调
            hardwareService.setHardwareCallback(new RK3588HardwareService.HardwareCallback() {
                @Override
                public void onConnectionStatusChanged(boolean connected) {
                    runOnUiThread(() -> updateConnectionStatus(connected ? "已连接" : "未连接"));
                }
                
                @Override
                public void onDataReceived(String data) {
                    runOnUiThread(() -> processHardwareResponse(data));
                }
                
                @Override
                public void onError(String error) {
                    runOnUiThread(() -> addLogEntry("错误: " + error));
                }
                
                @Override
                public void onHardwareStateChanged(RK3588HardwareService.LEDState state) {
                    runOnUiThread(() -> updateUIFromHardwareState(state));
                }
            });
            
            // 初始化硬件连接
            initializeHardwareConnection();
            
            // 应用启动时读取系统当前LED状态，而不是强制设置
            addLogEntry("应用启动：读取系统当前Work灯状态");
            
            addLogEntry("硬件服务绑定成功");
        }
        
        @Override
        public void onServiceDisconnected(ComponentName name) {
            hardwareService = null;
            isHardwareBound = false;
            addLogEntry("硬件服务连接断开");
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 应用启动时读取保存的用户选择模式
        SharedPreferences prefs = getPreferences(Context.MODE_PRIVATE);
        String savedMode = prefs.getString("lastUserSelectedMode", "default-on");
        lastUserSelectedMode = savedMode;
        addLogEntry("应用启动：读取保存的用户选择模式: " + savedMode);

        // 记录应用启动时间
        appStartTime = System.currentTimeMillis();
        addLogEntry("应用启动时间记录: " + appStartTime);

        MaterialToolbar toolbar = findViewById(R.id.topAppBar);
        toolbar.setTitle(R.string.app_name);
        toolbar.setSubtitle(R.string.toolbar_subtitle);

        // Work灯控制相关UI组件初始化
        workLedSwitch = findViewById(R.id.workLedSwitch);
        workLedStatusDot = findViewById(R.id.workLedStatusDot);
        workLedStatusLabel = findViewById(R.id.workLedStatusLabel);
        
        // 亮度控制相关UI组件初始化
        brightnessSeekBar = findViewById(R.id.brightnessSeekBar);
        brightnessValueText = findViewById(R.id.brightnessValueText);
        
        // 模式控制相关UI组件初始化
        modeDefaultOnCard = findViewById(R.id.modeDefaultOnCard);
        modeHeartbeatCard = findViewById(R.id.modeHeartbeatCard);
        modeTimerCard = findViewById(R.id.modeTimerCard);

        // 应用启动时自动设置LED文件权限
        setLedFilePermissions();

        initListeners();
        initializeDefaultState();
        
        // 绑定硬件服务
        bindHardwareService();
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        // 启动状态监控
        startStateMonitoring();
    }
    
    @Override
    protected void onPause() {
        super.onPause();
        // 停止状态监控
        stopStateMonitoring();
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 停止状态监控
        stopStateMonitoring();
        // 解绑硬件服务
        if (isHardwareBound) {
            unbindService(hardwareServiceConnection);
            isHardwareBound = false;
        }
    }



    private void initializeDefaultState() {
        // 检测硬件能力并设置模拟模式
        setupHardwareSimulationMode();
        
        // 应用启动时立即同步硬件状态，确保界面与硬件状态一致
        syncHardwareState();
        
        // 初始化亮度条状态
        updateBrightnessSeekBarEnabledState();
        
        addLogEntry("应用已就绪，Work灯控制功能可用");
    }

    /**
     * 更新Work灯状态显示
     */
    private void updateWorkLedStatus(boolean isOn) {
        workLedStatusLabel.setText(isOn ? "Work灯: 开启" : "Work灯: 关闭");
        int color = ContextCompat.getColor(this, isOn ? R.color.status_green : R.color.status_gray);
        Drawable drawable = DrawableCompat.wrap(workLedStatusDot.getBackground().mutate());
        DrawableCompat.setTint(drawable, color);
        workLedStatusDot.setBackground(drawable);
    }



    private void addLogEntry(String message) {
        String timestamp = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
        // 同步输出到logcat
        Log.d("AppLog", timestamp + " - " + message);
    }
    
    /**
     * 设置硬件模拟模式 - 在没有硬件时提供完整功能体验
     */
    private void setupHardwareSimulationMode() {
        // 检查是否连接到真实硬件
        boolean hasRealHardware = checkHardwareAvailability();
        
        if (!hasRealHardware) {
            // 模拟模式：显示所有功能，但添加模拟提示
            addLogEntry("当前运行在模拟模式 - 所有功能可用");
            addLogEntry("连接到真实硬件后将自动适配实际功能");
            
            // 可以在这里添加模拟模式的特殊处理
            // 例如：禁用某些控件或显示模拟状态
        } else {
            addLogEntry("检测到硬件连接 - 使用实际硬件功能");
        }
    }
    
    /**
     * 检查硬件可用性
     */
    private boolean checkHardwareAvailability() {
        // 这里可以添加实际的硬件检测逻辑
        // 目前返回false表示使用模拟模式
        return false;
    }
    
    // ========== 硬件通信相关方法 ==========
    
    /**
     * 绑定硬件服务
     */
    private void bindHardwareService() {
        Intent intent = new Intent(this, RK3588HardwareService.class);
        bindService(intent, hardwareServiceConnection, Context.BIND_AUTO_CREATE);
    }
    
    /**
     * 初始化硬件连接
     */
    private void initializeHardwareConnection() {
        if (hardwareService != null) {
            // 设置状态监听器
            hardwareService.setStateListener(new RK3588HardwareService.HardwareStateListener() {
                @Override
                public void onGPIOStateChanged(boolean state) {
                    runOnUiThread(() -> {
                        updateGPIOState(state);
                    });
                }
                
                @Override
                public void onConnectionLost(String reason) {
                    runOnUiThread(() -> {
                        updateConnectionStatus("连接断开: " + reason);
                        showConnectionErrorDialog(reason);
                    });
                }
                
                @Override
                public void onHardwareError(String error) {
                    runOnUiThread(() -> {
                        addLogEntry("硬件错误: " + error);
                        showErrorDialog("硬件错误", error);
                    });
                }
                
                
            });
            
            // 尝试连接硬件
            connectToHardware();
        }
    }

    /**
     * 连接到硬件
     */
    private void connectToHardware() {
        new Thread(() -> {
            // 尝试串口连接
            hardwareService.connectSerial("/dev/ttyS1", 115200);
            boolean connected = hardwareService.isConnected();
            
            final boolean finalConnected = connected;
            runOnUiThread(() -> {
                if (finalConnected) {
                    updateConnectionStatus("硬件连接成功");
                    syncHardwareState();
                } else {
                    updateConnectionStatus("硬件连接失败");
                    showConnectionErrorDialog("无法连接到硬件设备");
                }
            });
        }).start();
    }
    
    /**
     * 更新GPIO状态显示
     */
    private void updateGPIOState(boolean state) {
        String gpioText = "GPIO状态: " + (state ? "高电平" : "低电平");
        addLogEntry("GPIO状态变化: " + (state ? "高电平" : "低电平"));
    }
    
    /**
     * 显示连接错误对话框
     */
    private void showConnectionErrorDialog(String message) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("连接错误")
                .setMessage(message + "\n\n是否尝试重新连接？")
                .setPositiveButton("重试", (dialog, which) -> {
                    connectToHardware();
                })
                .setNegativeButton("取消", null)
                .show();
    }
    
    /**
     * 显示错误对话框
     */
    private void showErrorDialog(String title, String message) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("确定", null)
                .show();
    }
    
    /**
     * 更新连接状态显示
     */
    private void updateConnectionStatus(String status) {
        // 可以在这里更新UI显示连接状态
        addLogEntry("连接状态: " + status);
    }
    
    /**
     * 更新连接状态显示
     */
    private void updateConnectionStatus(boolean connected) {
        // 可以在这里更新UI显示连接状态
        if (connected) {
            // 连接成功，同步硬件状态
            syncHardwareState();
            addLogEntry("硬件连接状态: 已连接");
        } else {
            addLogEntry("硬件连接状态: 已断开");
        }
    }
    
    /**
     * 启动状态监控
     */
    private void startStateMonitoring() {
        if (shouldMonitorState) {
            return; // 已经在监控中
        }
        
        shouldMonitorState = true;
        stateMonitorThread = new Thread(() -> {
            while (shouldMonitorState) {
                try {
                    // 同步硬件状态
                    syncHardwareState();
                    
                    // 等待指定间隔
                    Thread.sleep(STATE_MONITOR_INTERVAL);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    Log.e("StateMonitor", "状态监控异常: " + e.getMessage());
                    try {
                        Thread.sleep(STATE_MONITOR_INTERVAL);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        });
        stateMonitorThread.start();
        addLogEntry("状态监控已启动");
    }
    
    /**
     * 停止状态监控
     */
    private void stopStateMonitoring() {
        shouldMonitorState = false;
        if (stateMonitorThread != null) {
            stateMonitorThread.interrupt();
            try {
                stateMonitorThread.join(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            stateMonitorThread = null;
        }
        addLogEntry("状态监控已停止");
    }
    
    /**
     * 同步硬件状态到UI
     */
    private void syncHardwareState() {
        // 移除用户操作检测逻辑，始终进行状态同步
        if (hardwareService != null) {
            // 直接获取LED状态，不依赖网络连接状态
            RK3588HardwareService.LEDState state = hardwareService.getLEDState();
            if (state != null) {
                updateUIFromHardwareState(state);
                addLogEntry("成功读取硬件状态: work=" + state.workFound + ", 亮度=" + state.workBrightness);
            } else {
                addLogEntry("无法读取硬件状态");
            }
        }
    }
    
    /**
     * 处理硬件响应数据
     */
    private void processHardwareResponse(String data) {
        // 解析硬件响应并更新UI
        addLogEntry("收到硬件响应: " + data);
        if (data.contains("LED:")) {
            // 解析LED状态更新
            RK3588HardwareService.LEDState state = parseLEDStateFromResponse(data);
            if (state != null) {
                updateUIFromHardwareState(state);
            }
        }
    }
    
    /**
     * 从响应数据解析LED状态
     */
    private RK3588HardwareService.LEDState parseLEDStateFromResponse(String data) {
        RK3588HardwareService.LEDState state = new RK3588HardwareService.LEDState();
        try {
            String[] parts = data.split(",");
            for (String part : parts) {
                String[] keyValue = part.split(":");
                if (keyValue.length == 2) {
                    switch (keyValue[0].trim()) {
                        case "LED":
                            state.powerOn = "ON".equals(keyValue[1].trim());
                            break;
                    }
                }
            }
            return state;
        } catch (Exception e) {
            addLogEntry("解析硬件响应失败: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * 根据硬件状态更新UI
     */
    private void updateUIFromHardwareState(RK3588HardwareService.LEDState state) {
        runOnUiThread(() -> {
            // 记录当前UI状态
            boolean currentUIState = workLedSwitch.isChecked();
            
            // 更新Work灯状态
            if (state.workFound) {
                // 添加调试日志
                Log.d("StateDebug", "硬件状态: powerOn=" + state.powerOn + 
                      ", workBrightness=" + state.workBrightness + 
                      ", mode=" + state.mode + 
                      ", UI状态: " + currentUIState);
                
                // 简化模式检测逻辑：只在LED开启时检测模式
                if (state.mode != null && state.powerOn) {
                    String currentMode = state.mode.toLowerCase();
                    
                    // 添加调试日志
                    Log.d("ModeDebug", "LED开启状态，当前模式: " + state.mode + ", 小写后: " + currentMode);
                    
                    // 根据当前模式更新卡片选择状态
                    if (currentMode.contains("heartbeat")) {
                        Log.d("ModeDebug", "匹配到心跳模式");
                        updateModeCardSelection(modeDefaultOnCard, false);
                        updateModeCardSelection(modeHeartbeatCard, true);
                        updateModeCardSelection(modeTimerCard, false);
                        // 更新用户最后选择的模式为当前检测到的模式
                        if (!lastUserSelectedMode.equals("heartbeat")) {
                            lastUserSelectedMode = "heartbeat";
                            addLogEntry("检测到硬件模式: 呼吸灯模式，记录用户选择模式");
                        }
                    } else if (currentMode.contains("timer")) {
                        Log.d("ModeDebug", "匹配到定时器模式");
                        updateModeCardSelection(modeDefaultOnCard, false);
                        updateModeCardSelection(modeHeartbeatCard, false);
                        updateModeCardSelection(modeTimerCard, true);
                        // 更新用户最后选择的模式为当前检测到的模式
                        if (!lastUserSelectedMode.equals("timer")) {
                            lastUserSelectedMode = "timer";
                            addLogEntry("检测到硬件模式: 闪烁模式，记录用户选择模式");
                        }
                    } else if (currentMode.contains("default-on")) {
                        Log.d("ModeDebug", "匹配到常亮模式");
                        updateModeCardSelection(modeDefaultOnCard, true);
                        updateModeCardSelection(modeHeartbeatCard, false);
                        updateModeCardSelection(modeTimerCard, false);
                        // 更新用户最后选择的模式为当前检测到的模式
                        if (!lastUserSelectedMode.equals("default-on")) {
                            lastUserSelectedMode = "default-on";
                            addLogEntry("检测到硬件模式: 常亮模式，记录用户选择模式");
                        }
                    } else {
                        Log.d("ModeDebug", "未匹配到任何模式，当前模式: " + currentMode);
                    }
                } else if (!state.powerOn) {
                    // LED关闭状态，显示为用户最后选择的模式
                    Log.d("ModeDebug", "LED关闭状态，显示用户最后选择的模式: " + lastUserSelectedMode);
                    
                    // 根据用户最后选择的模式更新UI
                    if (lastUserSelectedMode.equals("heartbeat")) {
                        updateModeCardSelection(modeHeartbeatCard, true);
                        updateModeCardSelection(modeDefaultOnCard, false);
                        updateModeCardSelection(modeTimerCard, false);
                    } else if (lastUserSelectedMode.equals("timer")) {
                        updateModeCardSelection(modeTimerCard, true);
                        updateModeCardSelection(modeDefaultOnCard, false);
                        updateModeCardSelection(modeHeartbeatCard, false);
                    } else {
                        updateModeCardSelection(modeDefaultOnCard, true);
                        updateModeCardSelection(modeHeartbeatCard, false);
                        updateModeCardSelection(modeTimerCard, false);
                    }
                    addLogEntry("LED关闭状态，显示用户最后选择的模式");
                }
                
                // 简化逻辑：只有当硬件状态确实发生变化且与UI状态不一致时才更新UI
                // 避免在用户操作时产生竞态条件
                if (state.powerOn != currentUIState) {
                    // 记录状态变化
                    addLogEntry("检测到状态变化: Work灯" + (state.powerOn ? "开启" : "关闭"));
                    Log.d("StateMonitor", "检测到状态变化: Work灯" + (state.powerOn ? "开启" : "关闭"));
                    
                    // 直接更新UI状态，不临时移除监听器
                    // 这样可以避免竞态条件和状态同步问题
                    workLedSwitch.setChecked(state.powerOn);
                    updateWorkLedStatus(state.powerOn);
                }
                
                // 同步亮度值：只有当亮度值发生变化时才更新UI
                int currentUIBrightness = brightnessSeekBar.getProgress();
                if (state.workBrightness != currentUIBrightness) {
                    // 根据当前模式判断是否允许亮度调节
                    String currentMode = getCurrentSelectedMode();
                    
                    if (currentMode.equals("default-on")) {
                        // 常亮模式：同步亮度值
                        brightnessSeekBar.setProgress(state.workBrightness);
                        brightnessValueText.setText(String.format(getString(R.string.brightness_value_format), state.workBrightness));
                        addLogEntry("检测到亮度变化: " + state.workBrightness);
                        Log.d("StateMonitor", "常亮模式下亮度同步: " + state.workBrightness);
                    } else if (currentMode.equals("heartbeat") || currentMode.equals("timer")) {
                        // 呼吸灯/闪烁模式：根据开关状态锁定亮度
                        boolean isLedOn = workLedSwitch.isChecked();
                        final int finalBrightness; // 使用final变量
                        if (isLedOn) {
                            // 开启状态下锁定亮度为255
                            finalBrightness = 255;
                            Log.d("WorkLEDControl", "呼吸灯/闪烁模式开启状态，亮度锁定为255");
                        } else {
                            // 关闭状态下锁定亮度为0
                            finalBrightness = 0;
                            Log.d("WorkLEDControl", "呼吸灯/闪烁模式关闭状态，亮度锁定为0");
                        }
                        // 更新UI显示
                        runOnUiThread(() -> {
                            brightnessSeekBar.setProgress(finalBrightness);
                            brightnessValueText.setText(String.format(getString(R.string.brightness_value_format), finalBrightness));
                        });
                    }
                }
            }
            
            addLogEntry("UI已同步硬件状态");
        });
    }
    
    /**
     * 控制Work灯
     * 改进版本：关闭LED时保持当前模式，只设置亮度为0
     */
    private void controlWorkLED(boolean enable) {
        if (hardwareService != null) {
            if (enable) {
                // 开启LED时，先设置模式再设置亮度
                Log.d("WorkLEDControl", "开启Work灯，使用用户最后选择的模式: " + lastUserSelectedMode);
                boolean modeSuccess = hardwareService.setWorkLEDMode(lastUserSelectedMode, true);
                if (modeSuccess) {
                    // 设置亮度为当前亮度值
                    int currentBrightness = brightnessSeekBar.getProgress();
                    boolean brightnessSuccess = hardwareService.setWorkLEDBrightness(currentBrightness);
                    
                    if (brightnessSuccess) {
                        addLogEntry("Work灯开启成功，模式: " + getModeDisplayName(lastUserSelectedMode));
                        // UI状态已经在开关监听器中更新，这里不需要重复更新
                        Log.d("WorkLEDControl", "Work灯开启成功，模式: " + lastUserSelectedMode);
                    } else {
                        addLogEntry("Work灯亮度设置失败，但模式已设置");
                        Log.w("WorkLEDControl", "Work灯亮度设置失败，但模式已设置");
                        // 亮度设置失败，但模式已设置，LED可能处于开启状态
                        // 让状态监控线程来同步实际状态
                    }
                } else {
                    addLogEntry("Work灯模式设置失败，可能需要root权限");
                    Log.w("WorkLEDControl", "Work灯模式设置失败，可能需要root权限");
                    // 模式设置失败，LED可能未开启，让状态监控线程来同步实际状态
                }
            } else {
                // 关闭LED时，保持当前模式，只设置亮度为0
                boolean success = hardwareService.controlWorkLED(false);
                if (success) {
                    addLogEntry("Work灯关闭成功（保持当前模式）");
                    // UI状态已经在开关监听器中更新，这里不需要重复更新
                    Log.d("WorkLEDControl", "Work灯关闭成功（保持当前模式）");
                } else {
                    addLogEntry("Work灯关闭失败，可能需要root权限");
                    Log.w("WorkLEDControl", "Work灯关闭失败，可能需要root权限");
                    // 关闭失败，LED可能仍处于开启状态，让状态监控线程来同步实际状态
                }
            }
        } else {
            addLogEntry("硬件服务未就绪，无法控制Work灯");
            Log.e("WorkLEDControl", "硬件服务未就绪，无法控制Work灯");
        }
    }
    
    /**
     * 控制Work灯亮度（根据模式智能调整）
     */
    private void controlWorkLEDBrightness(int brightness) {
        if (hardwareService != null) {
            // 根据当前模式判断是否允许亮度控制
            String currentMode = getCurrentSelectedMode();
            
            // 使用局部变量存储最终的亮度值
            int finalBrightness = brightness;
            
            // 呼吸灯和闪烁模式下，如果开关开启则锁定亮度为255，关闭则锁定为0
            if (currentMode.equals("heartbeat") || currentMode.equals("timer")) {
                boolean isLedOn = workLedSwitch.isChecked();
                
                // 使用final变量存储最终的亮度值
                final int finalBrightnessForLambda;
                if (isLedOn) {
                    // 开启状态下锁定亮度为255
                    finalBrightnessForLambda = 255;
                    finalBrightness = 255;
                    Log.d("WorkLEDControl", "呼吸灯/闪烁模式开启状态，亮度锁定为255");
                } else {
                    // 关闭状态下锁定亮度为0
                    finalBrightnessForLambda = 0;
                    finalBrightness = 0;
                    Log.d("WorkLEDControl", "呼吸灯/闪烁模式关闭状态，亮度锁定为0");
                }
                // 更新UI显示
                runOnUiThread(() -> {
                    brightnessSeekBar.setProgress(finalBrightnessForLambda);
                    brightnessValueText.setText(String.format(getString(R.string.brightness_value_format), finalBrightnessForLambda));
                });
            }
            
            boolean success = hardwareService.setWorkLEDBrightness(finalBrightness);
            if (success) {
                addLogEntry(String.format(getString(R.string.log_brightness_set), finalBrightness));
                Log.d("WorkLEDControl", "Work灯亮度设置成功: " + finalBrightness);
            } else {
                addLogEntry("Work灯亮度控制失败，可能需要root权限");
                Log.w("WorkLEDControl", "Work灯亮度控制失败，可能需要root权限");
            }
        } else {
            addLogEntry("硬件服务未就绪，无法控制Work灯亮度");
            Log.e("WorkLEDControl", "硬件服务未就绪，无法控制Work灯亮度");
        }
    }
    
    /**
     * 控制Work灯模式
     */
    private void controlWorkLEDMode(String mode) {
        if (hardwareService != null) {
            boolean success = hardwareService.setWorkLEDMode(mode, true);
            if (success) {
                // 保存用户选择的模式到SharedPreferences
                SharedPreferences prefs = getPreferences(Context.MODE_PRIVATE);
                SharedPreferences.Editor editor = prefs.edit();
                editor.putString("lastUserSelectedMode", mode);
                editor.apply();
                
                addLogEntry(String.format(getString(R.string.log_mode_set), getModeDisplayName(mode)));
                Log.d("WorkLEDControl", "Work灯模式设置成功并保存: " + mode);
            } else {
                addLogEntry("Work灯模式控制失败，可能需要root权限");
                Log.w("WorkLEDControl", "Work灯模式控制失败，可能需要root权限");
            }
        } else {
            addLogEntry("硬件服务未就绪，无法控制Work灯模式");
            Log.e("WorkLEDControl", "硬件服务未就绪，无法控制Work灯模式");
        }
    }
    
    
    
    /**
     * 获取模式显示名称
     */
    private String getModeDisplayName(String mode) {
        switch (mode) {
            case "default-on":
                return getString(R.string.mode_default_on);
            case "heartbeat":
                return getString(R.string.mode_heartbeat);
            case "timer":
                return getString(R.string.mode_timer);
            default:
                return mode;
        }
    }
    
    /**
     * 更新模式卡片选择状态
     */
    private void updateModeCardSelection(MaterialCardView cardView, boolean selected) {
        if (selected) {
            // 选中状态：背景色加深，边框颜色为主色，图标和文字颜色为主色
            cardView.setCardBackgroundColor(getColor(R.color.colorPrimary));
            cardView.setStrokeColor(getColor(R.color.colorPrimary));
            
            // 更新内部图标和文字颜色
            LinearLayout layout = (LinearLayout) cardView.getChildAt(0);
            ImageView icon = (ImageView) layout.getChildAt(0);
            TextView text = (TextView) layout.getChildAt(1);
            
            icon.setColorFilter(getColor(android.R.color.white));
            text.setTextColor(getColor(android.R.color.white));
        } else {
            // 未选中状态：背景色为浅色，边框为灰色，图标和文字颜色为灰色
            cardView.setCardBackgroundColor(getColor(R.color.card_background));
            cardView.setStrokeColor(getColor(R.color.card_stroke));
            
            // 更新内部图标和文字颜色
            LinearLayout layout = (LinearLayout) cardView.getChildAt(0);
            ImageView icon = (ImageView) layout.getChildAt(0);
            TextView text = (TextView) layout.getChildAt(1);
            
            icon.setColorFilter(getColor(R.color.card_text));
            text.setTextColor(getColor(R.color.card_text));
        }
    }
    
    /**
     * 根据保存的模式设置初始选中状态
     */
    private void updateModeCardSelectionBasedOnSavedMode() {
        // 读取保存的模式
        SharedPreferences prefs = getPreferences(Context.MODE_PRIVATE);
        String savedMode = prefs.getString("lastUserSelectedMode", "default-on");
        
        // 根据保存的模式设置对应的卡片为选中状态
        switch (savedMode) {
            case "heartbeat":
                updateModeCardSelection(modeDefaultOnCard, false);
                updateModeCardSelection(modeHeartbeatCard, true);
                updateModeCardSelection(modeTimerCard, false);
                addLogEntry("应用启动：UI初始化为呼吸灯模式");
                break;
            case "timer":
                updateModeCardSelection(modeDefaultOnCard, false);
                updateModeCardSelection(modeHeartbeatCard, false);
                updateModeCardSelection(modeTimerCard, true);
                addLogEntry("应用启动：UI初始化为闪烁模式");
                break;
            case "default-on":
            default:
                updateModeCardSelection(modeDefaultOnCard, true);
                updateModeCardSelection(modeHeartbeatCard, false);
                updateModeCardSelection(modeTimerCard, false);
                addLogEntry("应用启动：UI初始化为常亮模式");
                break;
        }
    }
    

    
    /**
     * 显示错误对话框（简化版本）
     */
    private void showErrorDialog(String error) {
        new MaterialAlertDialogBuilder(this)
            .setTitle("硬件错误")
            .setMessage(error)
            .setPositiveButton("确定", null)
            .show();
    }
    
    // ========== 重新设计监听器逻辑 ==========
    
    private void initListeners() {
        // Work灯开关监听器 - 简化逻辑：只控制LED的开关状态
        workLedSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            // 检查是否是用户操作（而不是状态同步）
            boolean isUserInteraction = buttonView.isPressed();
            
            if (isUserInteraction) {
                Log.d("WorkLEDControl", "用户操作：Work灯开关状态改变: " + (isChecked ? "开启" : "关闭"));
                
                if (isChecked) {
                    // 开启LED：应用当前选择的模式
                    String currentMode = getCurrentSelectedMode();
                    controlWorkLEDMode(currentMode);
                    addLogEntry("开启Work灯，模式: " + currentMode);
                    
                    // 根据模式设置亮度
                    if (currentMode.equals("heartbeat") || currentMode.equals("timer")) {
                        // 呼吸灯/闪烁模式：锁定亮度为255
                        controlWorkLEDBrightness(255);
                        Log.d("WorkLEDControl", "开启呼吸灯/闪烁模式，亮度锁定为255");
                    } else {
                        // 常亮模式：使用关闭前记录的亮度值，如果为0则设置为最小亮度1避免误关闭
                        int brightnessToUse = lastBrightnessBeforeTurnOff;
                        if (brightnessToUse == 0) {
                            brightnessToUse = 1; // 设置为最小亮度避免误关闭
                        }
                        brightnessSeekBar.setProgress(brightnessToUse);
                        brightnessValueText.setText(String.format(getString(R.string.brightness_value_format), brightnessToUse));
                        controlWorkLEDBrightness(brightnessToUse);
                        Log.d("WorkLEDControl", "开启常亮模式，使用关闭前记录的亮度: " + brightnessToUse);
                    }
                } else {
                    // 关闭LED前记录当前亮度值
                    lastBrightnessBeforeTurnOff = brightnessSeekBar.getProgress();
                    Log.d("WorkLEDControl", "关闭LED前记录亮度值: " + lastBrightnessBeforeTurnOff);
                    
                    // 关闭LED：直接关闭，不改变模式
                    controlWorkLED(false);
                    addLogEntry("关闭Work灯");
                }
                
                // 更新Work灯状态显示
                updateWorkLedStatus(isChecked);
                
                // 更新亮度条状态
                updateBrightnessSeekBarEnabledState();
            }
        });
        
        // 亮度调节监听器 - 常亮模式下允许调节亮度
        brightnessSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    // 更新亮度显示文本
                    brightnessValueText.setText(String.format(getString(R.string.brightness_value_format), progress));
                    
                    // 只有在常亮模式下且LED开启时才允许实时调节亮度
                    String currentMode = getCurrentSelectedMode();
                    if (currentMode.equals("default-on") && workLedSwitch.isChecked()) {
                        // 常亮模式且LED开启时，实时调节亮度
                        // 如果亮度为0，设置为最小亮度1避免误关闭
                        int adjustedBrightness = progress;
                        if (progress == 0) {
                            adjustedBrightness = 1;
                            brightnessSeekBar.setProgress(1);
                            brightnessValueText.setText(String.format(getString(R.string.brightness_value_format), 1));
                            Log.d("WorkLEDControl", "亮度调节到0，调整为1避免误关闭");
                        }
                        controlWorkLEDBrightness(adjustedBrightness);
                        Log.d("WorkLEDControl", "常亮模式下亮度调节: " + adjustedBrightness);
                    }
                }
            }
            
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                // 检查是否允许调节亮度
                String currentMode = getCurrentSelectedMode();
                if (currentMode.equals("default-on") && !workLedSwitch.isChecked()) {
                    // 常亮模式下LED关闭时，禁止调节亮度
                    seekBar.setEnabled(false);
                    Log.d("WorkLEDControl", "常亮模式下LED关闭，禁止亮度调节");
                }
            }
            
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                // 用户停止调节亮度，恢复亮度条状态
                String currentMode = getCurrentSelectedMode();
                if (currentMode.equals("default-on")) {
                    // 常亮模式下，根据LED开关状态设置亮度条是否可用
                    updateBrightnessSeekBarEnabledState();
                }
                Log.d("WorkLEDControl", "用户亮度调节完成");
            }
        });
        
        // 模式控制监听器 - 简化逻辑：只切换模式，不控制LED开关状态
        // 根据保存的模式设置初始选中状态
        updateModeCardSelectionBasedOnSavedMode();
        
        modeDefaultOnCard.setOnClickListener(v -> {
            switchToMode("default-on", modeDefaultOnCard);
        });
        
        modeHeartbeatCard.setOnClickListener(v -> {
            switchToMode("heartbeat", modeHeartbeatCard);
        });
        
        modeTimerCard.setOnClickListener(v -> {
            switchToMode("timer", modeTimerCard);
        });
    }
    
    /**
     * 获取当前选择的模式
     */
    private String getCurrentSelectedMode() {
        // 通过检查卡片背景色来判断当前选择的模式
        int selectedColor = getColor(R.color.colorPrimary);
        
        // 使用更可靠的方法检查背景色
        int defaultOnColor = modeDefaultOnCard.getCardBackgroundColor().getDefaultColor();
        int heartbeatColor = modeHeartbeatCard.getCardBackgroundColor().getDefaultColor();
        int timerColor = modeTimerCard.getCardBackgroundColor().getDefaultColor();
        
        Log.d("ModeDetection", "模式卡片颜色 - 常亮: " + defaultOnColor + ", 呼吸灯: " + heartbeatColor + ", 闪烁: " + timerColor + ", 选中色: " + selectedColor);
        
        if (defaultOnColor == selectedColor) {
            Log.d("ModeDetection", "检测到常亮模式");
            return "default-on";
        } else if (heartbeatColor == selectedColor) {
            Log.d("ModeDetection", "检测到呼吸灯模式");
            return "heartbeat";
        } else if (timerColor == selectedColor) {
            Log.d("ModeDetection", "检测到闪烁模式");
            return "timer";
        }
        
        // 如果无法检测到选中状态，使用保存的模式
        SharedPreferences prefs = getPreferences(Context.MODE_PRIVATE);
        String savedMode = prefs.getString("lastUserSelectedMode", "default-on");
        Log.d("ModeDetection", "无法检测模式，使用保存的模式: " + savedMode);
        return savedMode;
    }
    
    /**
     * 切换到指定模式
     */
    private void switchToMode(String mode, MaterialCardView selectedCard) {
        // 更新UI选择状态
        updateModeCardSelection(modeDefaultOnCard, mode.equals("default-on"));
        updateModeCardSelection(modeHeartbeatCard, mode.equals("heartbeat"));
        updateModeCardSelection(modeTimerCard, mode.equals("timer"));
        
        lastUserSelectedMode = mode;
        
        // 保存用户选择的模式到SharedPreferences
        SharedPreferences prefs = getPreferences(Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString("lastUserSelectedMode", mode);
        editor.apply();
        
        // 如果LED当前是开启状态，则应用新模式
        boolean currentSwitchState = workLedSwitch.isChecked();
        Log.d("ModeSwitch", "切换到模式: " + mode + ", LED开关状态: " + currentSwitchState);
        
        if (currentSwitchState) {
            controlWorkLEDMode(mode);
            addLogEntry("切换到模式: " + mode);
            
            // 模式切换后，根据新模式调整亮度控制
            if (mode.equals("heartbeat") || mode.equals("timer")) {
                // 呼吸灯/闪烁模式：锁定亮度为255
                controlWorkLEDBrightness(255);
                Log.d("ModeSwitch", "呼吸灯/闪烁模式，亮度锁定为255");
            } else if (mode.equals("default-on")) {
                // 常亮模式：保持当前亮度或使用默认值
                int currentBrightness = brightnessSeekBar.getProgress();
                controlWorkLEDBrightness(currentBrightness);
                Log.d("ModeSwitch", "常亮模式，使用当前亮度: " + currentBrightness);
            }
        } else {
            addLogEntry("选择模式: " + mode + "（LED当前关闭，保持关闭状态）");
            Log.d("ModeSwitch", "LED当前关闭，只记录模式选择，不控制硬件");
        }
        
        // 模式切换后更新亮度条状态
        updateBrightnessSeekBarEnabledState();
    }
    
    /**
     * 更新亮度条启用状态
     */
    private void updateBrightnessSeekBarEnabledState() {
        String currentMode = getCurrentSelectedMode();
        boolean isLedOn = workLedSwitch.isChecked();
        
        // 只有在常亮模式下且LED开启时才允许调节亮度
        boolean enabled = (currentMode.equals("default-on") && isLedOn);
        
        brightnessSeekBar.setEnabled(enabled);
        
        // 设置亮度条的视觉状态
        if (enabled) {
            brightnessSeekBar.setAlpha(1.0f);
            brightnessValueText.setAlpha(1.0f);
            Log.d("BrightnessControl", "亮度条已启用 - 模式: " + currentMode + ", LED状态: " + isLedOn);
        } else {
            brightnessSeekBar.setAlpha(0.5f);
            brightnessValueText.setAlpha(0.5f);
            Log.d("BrightnessControl", "亮度条已禁用 - 模式: " + currentMode + ", LED状态: " + isLedOn);
        }
    }
    
    /**
     * 设置LED文件权限 - 只设置权限，不改变LED状态
     */
    private void setLedFilePermissions() {
        new Thread(() -> {
            try {
                // 尝试通过su命令获取root权限并设置LED文件权限
                Process process = Runtime.getRuntime().exec("su");
                DataOutputStream os = new DataOutputStream(process.getOutputStream());
                
                // 设置WORK LED亮度文件权限
                os.writeBytes("chmod 666 /sys/class/leds/work/brightness\n");
                // 设置WORK LED触发模式文件权限
                os.writeBytes("chmod 666 /sys/class/leds/work/trigger\n");
                
                // 重要：不再强制设置LED为常亮模式，保持LED当前状态
                Log.d("LEDPermissions", "只设置LED文件权限，不改变LED当前状态");
                
                // 退出su shell
                os.writeBytes("exit\n");
                os.flush();
                
                int result = process.waitFor();
                
                if (result == 0) {
                    Log.d("LEDPermissions", "LED文件权限设置成功，保持LED当前状态");
                    addLogEntry("LED文件权限设置成功，保持LED当前状态");
                } else {
                    Log.w("LEDPermissions", "LED文件权限设置失败，可能需要root权限");
                    addLogEntry("LED文件权限设置失败，可能需要root权限");
                    
                    // 尝试非root方式设置权限
                    tryNonRootPermissionSetting();
                }
                
            } catch (Exception e) {
                Log.e("LEDPermissions", "LED文件权限设置异常: " + e.getMessage());
                addLogEntry("LED文件权限设置异常: " + e.getMessage());
                
                // 尝试非root方式设置权限
                tryNonRootPermissionSetting();
            }
        }).start();
    }
    
    /**
     * 尝试非root方式设置权限
     */
    private void tryNonRootPermissionSetting() {
        try {
            // 尝试直接执行chmod命令（可能需要系统权限）
            Process process = Runtime.getRuntime().exec("chmod 666 /sys/class/leds/work/brightness");
            int result1 = process.waitFor();
            
            process = Runtime.getRuntime().exec("chmod 666 /sys/class/leds/work/trigger");
            int result2 = process.waitFor();
            
            if (result1 == 0 && result2 == 0) {
                Log.d("LEDPermissions", "非root方式LED文件权限设置成功");
                addLogEntry("非root方式LED文件权限设置成功，保持LED当前状态");
                
                // 重要：不再强制设置LED为常亮模式，保持LED当前状态
                Log.d("LEDPermissions", "非root方式只设置权限，不改变LED当前状态");
            } else {
                Log.w("LEDPermissions", "非root方式LED文件权限设置失败");
                addLogEntry("非root方式LED文件权限设置失败，应用启动后可能需要手动设置权限");
            }
            
        } catch (Exception e) {
            Log.e("LEDPermissions", "非root方式LED文件权限设置异常: " + e.getMessage());
            addLogEntry("LED文件权限设置失败，重启后可能需要手动设置权限");
        }
    }

}












