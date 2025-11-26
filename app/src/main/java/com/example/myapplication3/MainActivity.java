package com.example.myapplication3;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
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

    // 硬件只支持开关功能，颜色选择已移除

    // 硬件只支持开关功能，动态模式相关方法已移除

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
     * 同步硬件状态
     */
    private void syncHardwareState() {
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
                boolean workLedOn = state.workBrightness > 0;
                
                // 只有当状态发生变化时才更新UI，避免不必要的闪烁
                if (workLedOn != currentUIState) {
                    // 先更新开关状态，但暂时移除监听器避免触发硬件控制
                    workLedSwitch.setOnCheckedChangeListener(null);
                    workLedSwitch.setChecked(workLedOn);
                    updateWorkLedStatus(workLedOn);
                    
                    // 重新设置监听器
                    workLedSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
                        updateWorkLedStatus(isChecked);
                        addLogEntry(isChecked ? "Work灯开启" : "Work灯关闭");
                        Log.d("WorkLEDControl", "Work灯开关状态改变: " + (isChecked ? "开启" : "关闭"));
                        controlWorkLED(isChecked);
                    });
                    
                    // 记录外部状态变化
                    addLogEntry("检测到外部状态变化: Work灯" + (workLedOn ? "开启" : "关闭"));
                    Log.d("StateMonitor", "检测到外部状态变化: Work灯" + (workLedOn ? "开启" : "关闭"));
                }
                
                // 更新模式卡片状态（根据当前模式字符串）
                if (state.mode != null) {
                    String currentMode = state.mode.toLowerCase();
                    
                    // 添加调试日志
                    Log.d("ModeDebug", "当前模式: " + state.mode + ", 小写后: " + currentMode);
                    
                    // 根据当前模式更新卡片选择状态
                    if (currentMode.contains("heartbeat")) {
                        Log.d("ModeDebug", "匹配到心跳模式");
                        updateModeCardSelection(modeDefaultOnCard, false);
                        updateModeCardSelection(modeHeartbeatCard, true);
                        updateModeCardSelection(modeTimerCard, false);
                        addLogEntry("检测到外部模式变化: 呼吸灯模式");
                    } else if (currentMode.contains("timer")) {
                        Log.d("ModeDebug", "匹配到定时器模式");
                        updateModeCardSelection(modeDefaultOnCard, false);
                        updateModeCardSelection(modeHeartbeatCard, false);
                        updateModeCardSelection(modeTimerCard, true);
                        addLogEntry("检测到外部模式变化: 闪烁模式");
                    } else if (currentMode.contains("default-on")) {
                        Log.d("ModeDebug", "匹配到常亮模式");
                        updateModeCardSelection(modeDefaultOnCard, true);
                        updateModeCardSelection(modeHeartbeatCard, false);
                        updateModeCardSelection(modeTimerCard, false);
                        addLogEntry("检测到外部模式变化: 常亮模式");
                    } else {
                        Log.d("ModeDebug", "未匹配到任何模式，当前模式: " + currentMode);
                    }
                }
            }
            
            addLogEntry("UI已同步硬件状态");
        });
    }
    
    /**
     * 控制Work灯
     */
    private void controlWorkLED(boolean enable) {
        if (hardwareService != null) {
            boolean success = hardwareService.controlWorkLED(enable);
            if (success) {
                addLogEntry("Work灯控制成功: " + (enable ? "开启" : "关闭"));
                updateWorkLedStatus(enable);
                Log.d("WorkLEDControl", "Work灯控制成功: " + (enable ? "开启" : "关闭"));
            } else {
                addLogEntry("Work灯控制失败，可能需要root权限");
                Log.w("WorkLEDControl", "Work灯控制失败，可能需要root权限");
            }
        } else {
            addLogEntry("硬件服务未就绪，无法控制Work灯");
            Log.e("WorkLEDControl", "硬件服务未就绪，无法控制Work灯");
        }
    }
    
    /**
     * 控制Work灯亮度
     */
    private void controlWorkLEDBrightness(int brightness) {
        if (hardwareService != null) {
            boolean success = hardwareService.setWorkLEDBrightness(brightness);
            if (success) {
                addLogEntry(String.format(getString(R.string.log_brightness_set), brightness));
                Log.d("WorkLEDControl", "Work灯亮度设置成功: " + brightness);
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
            boolean success = hardwareService.setWorkLEDMode(mode);
            if (success) {
                addLogEntry(String.format(getString(R.string.log_mode_set), getModeDisplayName(mode)));
                Log.d("WorkLEDControl", "Work灯模式设置成功: " + mode);
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
     * 显示错误对话框（简化版本）
     */
    private void showErrorDialog(String error) {
        new MaterialAlertDialogBuilder(this)
            .setTitle("硬件错误")
            .setMessage(error)
            .setPositiveButton("确定", null)
            .show();
    }
    
    // ========== 修改现有监听器以集成硬件控制 ==========
    
    private void initListeners() {
        // Work灯开关监听器
        workLedSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            updateWorkLedStatus(isChecked);
            addLogEntry(isChecked ? "Work灯开启" : "Work灯关闭");
            
            // 添加详细的Logcat日志
            Log.d("WorkLEDControl", "Work灯开关状态改变: " + (isChecked ? "开启" : "关闭"));
            
            // 控制Work灯
            controlWorkLED(isChecked);
        });
        
        // 添加硬件测试按钮
        MaterialButton hardwareTestButton = findViewById(R.id.hardwareTestButton);
        if (hardwareTestButton != null) {
            hardwareTestButton.setOnClickListener(v -> {
                Intent intent = new Intent(MainActivity.this, HardwareTestActivity.class);
                startActivity(intent);
                addLogEntry("启动硬件测试界面");
            });
        }
        
        // 亮度控制监听器
        brightnessSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    // 更新亮度值显示
                    brightnessValueText.setText(String.format(getString(R.string.brightness_value_format), progress));
                    
                    // 控制Work灯亮度
                    controlWorkLEDBrightness(progress);
                }
            }
            
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                // 开始拖动时不做处理
            }
            
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                // 停止拖动时不做处理
            }
        });
        
        // 模式控制监听器 - 卡片按钮样式
        // 设置默认选中状态
        updateModeCardSelection(modeDefaultOnCard, true);
        
        modeDefaultOnCard.setOnClickListener(v -> {
            updateModeCardSelection(modeDefaultOnCard, true);
            updateModeCardSelection(modeHeartbeatCard, false);
            updateModeCardSelection(modeTimerCard, false);
            controlWorkLEDMode("default-on");
        });
        
        modeHeartbeatCard.setOnClickListener(v -> {
            updateModeCardSelection(modeDefaultOnCard, false);
            updateModeCardSelection(modeHeartbeatCard, true);
            updateModeCardSelection(modeTimerCard, false);
            controlWorkLEDMode("heartbeat");
        });
        
        modeTimerCard.setOnClickListener(v -> {
            updateModeCardSelection(modeDefaultOnCard, false);
            updateModeCardSelection(modeHeartbeatCard, false);
            updateModeCardSelection(modeTimerCard, true);
            controlWorkLEDMode("timer");
        });
    }
    
    /**
     * 设置LED文件权限并初始化LED状态 - 解决重启后权限丢失问题
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
                
                // 设置WORK LED为常亮模式（default-on）
                os.writeBytes("echo default-on > /sys/class/leds/work/trigger\n");
                // 设置WORK LED亮度为255（最大亮度）
                os.writeBytes("echo 255 > /sys/class/leds/work/brightness\n");
                
                // 退出su shell
                os.writeBytes("exit\n");
                os.flush();
                
                int result = process.waitFor();
                
                if (result == 0) {
                    Log.d("LEDPermissions", "LED文件权限设置成功，WORK灯已设为常亮模式");
                    addLogEntry("LED文件权限设置成功，WORK灯已设为常亮模式");
                } else {
                    Log.w("LEDPermissions", "LED文件权限设置失败，可能需要root权限");
                    addLogEntry("LED文件权限设置失败，可能需要root权限");
                    
                    // 尝试非root方式设置权限和LED状态
                    tryNonRootPermissionSetting();
                }
                
            } catch (Exception e) {
                Log.e("LEDPermissions", "LED文件权限设置异常: " + e.getMessage());
                addLogEntry("LED文件权限设置异常: " + e.getMessage());
                
                // 尝试非root方式设置权限和LED状态
                tryNonRootPermissionSetting();
            }
        }).start();
    }
    
    /**
     * 尝试非root方式设置权限和LED状态
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
                addLogEntry("非root方式LED文件权限设置成功");
                
                // 尝试设置WORK LED为常亮模式
                try {
                    process = Runtime.getRuntime().exec("echo default-on > /sys/class/leds/work/trigger");
                    int result3 = process.waitFor();
                    
                    process = Runtime.getRuntime().exec("echo 255 > /sys/class/leds/work/brightness");
                    int result4 = process.waitFor();
                    
                    if (result3 == 0 && result4 == 0) {
                        Log.d("LEDPermissions", "非root方式WORK灯常亮模式设置成功");
                        addLogEntry("WORK灯已设为常亮模式");
                    } else {
                        Log.w("LEDPermissions", "非root方式WORK灯模式设置失败");
                        addLogEntry("WORK灯模式设置失败，可能需要手动设置");
                    }
                } catch (Exception e) {
                    Log.e("LEDPermissions", "非root方式LED状态设置异常: " + e.getMessage());
                    addLogEntry("WORK灯状态设置异常");
                }
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

