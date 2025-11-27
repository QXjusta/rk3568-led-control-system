package com.example.myapplication3;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.widget.Button;
import android.widget.TextView;
import android.widget.ScrollView;
import android.widget.LinearLayout;
import android.view.View;

public class HardwareTestActivity extends Activity {
    private RK3588HardwareService hardwareService;
    private boolean isHardwareBound = false;
    private TextView resultTextView;
    private Handler handler;
    
    /**
     * 硬件服务连接回调
     */
    private final ServiceConnection hardwareServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            RK3588HardwareService.HardwareBinder binder = (RK3588HardwareService.HardwareBinder) service;
            hardwareService = binder.getService();
            isHardwareBound = true;
            
            // 自动测试并显示LED状态
            autoTestLEDState();
        }
        
        @Override
        public void onServiceDisconnected(ComponentName name) {
            hardwareService = null;
            isHardwareBound = false;
        }
    };
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        handler = new Handler(Looper.getMainLooper());
        
        // 创建界面
        createUI();
        
        // 绑定硬件服务
        bindHardwareService();
    }
    
    /**
     * 绑定硬件服务
     */
    private void bindHardwareService() {
        Intent intent = new Intent(this, RK3588HardwareService.class);
        bindService(intent, hardwareServiceConnection, Context.BIND_AUTO_CREATE);
    }
    
    private void createUI() {
        ScrollView scrollView = new ScrollView(this);
        LinearLayout mainLayout = new LinearLayout(this);
        mainLayout.setOrientation(LinearLayout.VERTICAL);
        
        // 标题
        TextView title = new TextView(this);
        title.setText("RK3588硬件读取测试");
        title.setTextSize(20);
        title.setPadding(20, 20, 20, 20);
        mainLayout.addView(title);
        
        // LED状态显示区域（自动显示）
        TextView ledStatusTitle = new TextView(this);
        ledStatusTitle.setText("LED设备状态（自动显示）");
        ledStatusTitle.setTextSize(16);
        ledStatusTitle.setPadding(20, 10, 20, 10);
        mainLayout.addView(ledStatusTitle);
        
        resultTextView = new TextView(this);
        resultTextView.setText("正在读取LED状态...");
        resultTextView.setPadding(30, 15, 30, 25);
        resultTextView.setTextSize(14);
        resultTextView.setBackgroundColor(0xFFF5F5F5);
        resultTextView.setTextColor(0xFF333333);
        resultTextView.setLineSpacing(0, 1.2f);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        textParams.setMargins(20, 5, 20, 15);
        resultTextView.setLayoutParams(textParams);
        mainLayout.addView(resultTextView);
        
        // 分隔线
        View separator = new View(this);
        separator.setBackgroundColor(0xFFCCCCCC);
        LinearLayout.LayoutParams separatorParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 2
        );
        separatorParams.setMargins(20, 10, 20, 10);
        separator.setLayoutParams(separatorParams);
        mainLayout.addView(separator);
        
        // 其他测试按钮
        TextView otherTestsTitle = new TextView(this);
        otherTestsTitle.setText("其他硬件测试");
        otherTestsTitle.setTextSize(16);
        otherTestsTitle.setPadding(20, 10, 20, 10);
        mainLayout.addView(otherTestsTitle);
        
        Button testLEDButton = createButton("手动测试LED状态");
        testLEDButton.setOnClickListener(v -> testLEDState());
        mainLayout.addView(testLEDButton);
        
        Button testSystemButton = createButton("测试系统信息");
        testSystemButton.setOnClickListener(v -> testSystemInfo());
        mainLayout.addView(testSystemButton);
        
        Button testPermissionsButton = createButton("测试设备权限");
        testPermissionsButton.setOnClickListener(v -> testDevicePermissions());
        mainLayout.addView(testPermissionsButton);
        
        Button testAllButton = createButton("测试所有硬件");
        testAllButton.setOnClickListener(v -> testAllHardware());
        mainLayout.addView(testAllButton);
        
        // 分隔线
        View separator2 = new View(this);
        separator2.setBackgroundColor(0xFFCCCCCC);
        LinearLayout.LayoutParams separator2Params = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 2
        );
        separator2Params.setMargins(20, 10, 20, 10);
        separator2.setLayoutParams(separator2Params);
        mainLayout.addView(separator2);
        
        // work设备控制区域
        TextView workControlTitle = new TextView(this);
        workControlTitle.setText("work设备控制");
        workControlTitle.setTextSize(16);
        workControlTitle.setPadding(20, 10, 20, 10);
        mainLayout.addView(workControlTitle);
        
        // 控制按钮布局
        LinearLayout controlLayout = new LinearLayout(this);
        controlLayout.setOrientation(LinearLayout.HORIZONTAL);
        controlLayout.setPadding(20, 10, 20, 20);
        
        Button turnOnButton = createButton("开启work灯");
        turnOnButton.setOnClickListener(v -> controlWorkLED(true));
        
        Button turnOffButton = createButton("关闭work灯");
        turnOffButton.setOnClickListener(v -> controlWorkLED(false));
        
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1
        );
        buttonParams.setMargins(5, 0, 5, 0);
        turnOnButton.setLayoutParams(buttonParams);
        turnOffButton.setLayoutParams(buttonParams);
        
        controlLayout.addView(turnOnButton);
        controlLayout.addView(turnOffButton);
        mainLayout.addView(controlLayout);
        
        scrollView.addView(mainLayout);
        setContentView(scrollView);
    }
    
    private Button createButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setPadding(20, 10, 20, 10);
        button.setTextSize(16);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(20, 10, 20, 10);
        button.setLayoutParams(params);
        return button;
    }
    
    private void autoTestLEDState() {
        new Thread(() -> {
            if (hardwareService != null) {
                LEDState state = hardwareService.getLEDState();
                if (state != null) {
                    String result = formatLEDState(state);
                    updateUI("LED设备状态:\\n" + result);
                } else {
                    updateUI("LED设备状态:\\n无法读取LED状态");
                }
            } else {
                updateUI("LED设备状态:\\n硬件服务未就绪");
            }
        }).start();
    }
    
    private void testLEDState() {
        new Thread(() -> {
            if (hardwareService != null) {
                LEDState state = hardwareService.getLEDState();
                if (state != null) {
                    String result = formatLEDState(state);
                    updateUI("LED状态测试结果:\\n" + result);
                } else {
                    updateUI("LED状态测试结果:\\n无法读取LED状态");
                }
            } else {
                updateUI("LED状态测试结果:\\n硬件服务未就绪");
            }
        }).start();
    }
    
    private String formatLEDState(LEDState state) {
        StringBuilder sb = new StringBuilder();
        sb.append("🔍 LED设备状态详情\n");
        sb.append("══════════════════════════\n");
        sb.append("• 电源状态: ").append(state.powerOn ? "✅ 开启" : "❌ 关闭").append("\n");
        
        // 处理模式显示：当LED关闭时，显示用户最后选择的模式
        String displayMode = state.mode;
        if ("unknown".equals(state.mode) || (!state.powerOn && !state.mode.equals("设备未找到"))) {
            // 读取主页面保存的用户选择的模式（使用全局SharedPreferences）
            SharedPreferences prefs = getSharedPreferences("LEDControlPrefs", Context.MODE_PRIVATE);
            String lastUserSelectedMode = prefs.getString("lastUserSelectedMode", "default-on");
            
            // 将技术模式名称转换为中文显示名称
            switch (lastUserSelectedMode) {
                case "heartbeat":
                    displayMode = "呼吸灯模式";
                    break;
                case "timer":
                    displayMode = "闪烁模式";
                    break;
                case "default-on":
                default:
                    displayMode = "常亮模式";
                    break;
            }
        } else {
            // LED开启时，显示实际的硬件模式
            switch (state.mode) {
                case "heartbeat":
                    displayMode = "呼吸灯模式";
                    break;
                case "timer":
                    displayMode = "闪烁模式";
                    break;
                case "default-on":
                    displayMode = "常亮模式";
                    break;
                case "mmc2":
                    displayMode = "硬件控制模式";
                    break;
                default:
                    displayMode = state.mode; // 保持原样
                    break;
            }
        }
        sb.append("• 模式: ").append(displayMode).append("\n\n");
        
        sb.append("💡 work设备: ").append(state.workFound ? "✅ 已检测" : "❌ 未找到").append("\n");
        if (state.workFound) {
            // 根据模式显示不同的亮度值信息
            if (state.mode.contains("heartbeat") || state.mode.contains("timer")) {
                // 呼吸灯/闪烁模式：根据电源状态显示锁定亮度
                if (state.powerOn) {
                    sb.append("  亮度值: 255/255 (模式锁定)\n");
                    sb.append("  说明: 呼吸灯/闪烁模式下亮度自动锁定为最大值\n");
                } else {
                    sb.append("  亮度值: 0/255 (模式锁定)\n");
                    sb.append("  说明: LED关闭状态下亮度自动锁定为0\n");
                }
            } else {
                // 常亮模式：显示实际亮度值
                sb.append("  亮度值: ").append(state.workBrightness).append("/255\n");
                if (!state.powerOn) {
                    sb.append("  说明: LED关闭状态，亮度值不反映实际输出\n");
                }
            }
        }
        
        sb.append("\n💡 mmc2::设备: ").append(state.mmc2Found ? "✅ 已检测" : "❌ 未找到").append("\n");
        if (state.mmc2Found) {
            // 对于mmc2::设备，如果亮度为255（我们设置的特殊值），说明是硬件控制模式
            if (state.mmc2Brightness == 255) {
                sb.append("  状态: 🛠️ 硬件控制模式（常亮）\n");
                sb.append("  说明: 此LED由MMC2硬件直接控制，亮度值不反映实际光强度\n");
            } else {
                sb.append("  亮度值: ").append(state.mmc2Brightness).append("/255\n");
            }
        }
        
        return sb.toString();
    }
    
    private void testSystemInfo() {
        new Thread(() -> {
            if (hardwareService != null) {
                String systemInfo = hardwareService.readSystemInfo();
                String result = "📊 系统信息测试结果\n" +
                              "══════════════════════════\n" +
                              systemInfo;
                updateUI(result);
            } else {
                updateUI("📊 系统信息测试结果\n══════════════════════════\n硬件服务未就绪");
            }
        }).start();
    }
    
    private void testDevicePermissions() {
        new Thread(() -> {
            StringBuilder result = new StringBuilder("🔐 设备权限测试结果\n");
            result.append("══════════════════════════\n");
            String[] devices = {"/dev/ttyS4", "/sys/class/leds/work/brightness", "/proc/version"};
            if (hardwareService != null) {
                for (String device : devices) {
                    boolean hasPermission = hardwareService.checkDevicePermissions(device);
                    result.append(hasPermission ? "✅ " : "❌ ").append(device).append(": ").append(hasPermission ? "有权限" : "无权限").append("\n");
                }
            } else {
                result.append("❌ 硬件服务未就绪\n");
            }
            updateUI(result.toString());
        }).start();
    }
    
    private void testAllHardware() {
        new Thread(() -> {
            StringBuilder result = new StringBuilder("🔧 所有硬件测试结果\n");
            result.append("══════════════════════════\n\\n");
            
            if (hardwareService != null) {
                // 测试LED
                result.append("1. 💡 LED状态\n");
                result.append("──────────────────────────\n");
                LEDState ledState = hardwareService.getLEDState();
                if (ledState != null) {
                    result.append(formatLEDState(ledState)).append("\n\n");
                } else {
                    result.append("❌ 无法读取LED状态\n\n");
                }
                
                // 测试系统信息
                result.append("2. 📊 系统信息\n");
                result.append("──────────────────────────\n");
                result.append(hardwareService.readSystemInfo()).append("\n\n");
                
                // 测试设备权限
                result.append("3. 🔐 设备权限检查\n");
                result.append("──────────────────────────\n");
                String[] devices = {"/dev/ttyS4", "/sys/class/leds/work/brightness", "/proc/version"};
                for (String device : devices) {
                    boolean hasPermission = hardwareService.checkDevicePermissions(device);
                    result.append(hasPermission ? "✅ " : "❌ ").append(device).append(": ").append(hasPermission ? "有权限" : "无权限").append("\n");
                }
            } else {
                result.append("❌ 硬件服务未就绪\n");
            }
            
            updateUI(result.toString());
        }).start();
    }
    
    private void updateUI(final String text) {
        handler.post(() -> {
            resultTextView.setText(text);
        });
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 解绑硬件服务
        if (isHardwareBound) {
            unbindService(hardwareServiceConnection);
            isHardwareBound = false;
        }
    }
    
    private void controlWorkLED(final boolean enable) {
        new Thread(() -> {
            if (hardwareService != null) {
                boolean success = hardwareService.controlWorkLED(enable);
                if (success) {
                    updateUI("✅ work灯控制成功: " + (enable ? "已开启" : "已关闭") + "\n\n🔄 正在更新状态...");
                    
                    // 延迟1秒后重新读取LED状态
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    
                    // 重新读取并显示LED状态
                    LEDState state = hardwareService.getLEDState();
                    if (state != null) {
                        String result = formatLEDState(state);
                        updateUI("✅ work灯控制成功: " + (enable ? "已开启" : "已关闭") + "\n\n" + result);
                    } else {
                        updateUI("✅ work灯控制成功: " + (enable ? "已开启" : "已关闭") + "\n\n⚠️ 但无法读取最新状态");
                    }
                } else {
                    // 提供详细的错误信息和替代方案
                    StringBuilder errorMessage = new StringBuilder();
                    errorMessage.append("❌ work灯控制失败！\n");
                    errorMessage.append("══════════════════════════\n\n");
                    errorMessage.append("📋 原因分析：\n");
                    errorMessage.append("• work设备需要root权限才能直接控制\n");
                    errorMessage.append("• 当前应用运行在普通用户权限下\n\n");
                    errorMessage.append("💡 替代解决方案：\n");
                    errorMessage.append("1. 使用root权限运行应用\n");
                    errorMessage.append("2. 通过ADB命令手动控制：\n");
                    errorMessage.append("   开启: adb shell \"echo 255 > /sys/class/leds/work/brightness\"\n");
                    errorMessage.append("   关闭: adb shell \"echo 0 > /sys/class/leds/work/brightness\"\n\n");
                    errorMessage.append("3. 修改设备权限（需要root）：\n");
                    errorMessage.append("   chmod 666 /sys/class/leds/work/brightness\n");
                    errorMessage.append("   chmod 666 /sys/class/leds/work/trigger\n\n");
                    errorMessage.append("📊 当前work设备状态：\n");
                    
                    // 显示当前work设备状态
                    LEDState state = hardwareService.getLEDState();
                    if (state != null && state.workFound) {
                        errorMessage.append("• 亮度值: ").append(state.workBrightness).append("/255\n");
                        errorMessage.append("• 状态: ").append(state.workBrightness > 0 ? "💡 亮" : "⚫ 灭").append("\n");
                    } else {
                        errorMessage.append("• ❌ 无法读取work设备状态\n");
                    }
                    
                    updateUI(errorMessage.toString());
                }
            } else {
                updateUI("❌ work灯控制失败：硬件服务未就绪\n");
            }
        }).start();
    }
}