package com.example.myapplication3;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.TextView;
import android.widget.ScrollView;
import android.widget.LinearLayout;
import android.view.View;

public class HardwareTestActivity extends Activity {
    private RK3588HardwareService hardwareService;
    private TextView resultTextView;
    private Handler handler;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // 初始化硬件服务
        hardwareService = new RK3588HardwareService();
        handler = new Handler(Looper.getMainLooper());
        
        // 创建界面
        createUI();
        
        // 自动测试并显示LED状态
        autoTestLEDState();
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
        resultTextView.setPadding(20, 10, 20, 20);
        resultTextView.setTextSize(14);
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
            RK3588HardwareService.LEDState state = hardwareService.getLEDState();
            if (state != null) {
                String result = formatLEDState(state);
                updateUI("LED设备状态:\\n" + result);
            } else {
                updateUI("LED设备状态:\\n无法读取LED状态");
            }
        }).start();
    }
    
    private void testLEDState() {
        new Thread(() -> {
            RK3588HardwareService.LEDState state = hardwareService.getLEDState();
            if (state != null) {
                String result = formatLEDState(state);
                updateUI("LED状态测试结果:\\n" + result);
            } else {
                updateUI("LED状态测试结果:\\n无法读取LED状态");
            }
        }).start();
    }
    
    private String formatLEDState(RK3588HardwareService.LEDState state) {
        StringBuilder sb = new StringBuilder();
        sb.append("LED设备状态详情\\n");
        sb.append("电源状态: ").append(state.powerOn ? "开启" : "关闭").append("\\n");
        sb.append("模式: ").append(state.mode).append("\\n\\n");
        
        sb.append("work设备: ").append(state.workFound ? "已检测" : "未找到").append("\\n");
        if (state.workFound) {
            sb.append("亮度值: ").append(state.workBrightness).append("\\n");
        }
        
        sb.append("\\nmmc2::设备: ").append(state.mmc2Found ? "已检测" : "未找到").append("\\n");
        if (state.mmc2Found) {
            // 对于mmc2::设备，如果亮度为255（我们设置的特殊值），说明是硬件控制模式
            if (state.mmc2Brightness == 255) {
                sb.append("状态: 硬件控制模式（常亮）\\n");
                sb.append("说明: 此LED由MMC2硬件直接控制，亮度值不反映实际光强度\\n");
            } else {
                sb.append("亮度值: ").append(state.mmc2Brightness).append("\\n");
            }
        }
        
        return sb.toString();
    }
    
    private void testSystemInfo() {
        new Thread(() -> {
            String systemInfo = hardwareService.readSystemInfo();
            String result = "系统信息测试结果:\\n" + systemInfo;
            updateUI(result);
        }).start();
    }
    
    private void testDevicePermissions() {
        new Thread(() -> {
            StringBuilder result = new StringBuilder("设备权限测试结果:\\n");
            String[] devices = {"/dev/ttyS4", "/sys/class/leds/work/brightness", "/proc/version"};
            for (String device : devices) {
                boolean hasPermission = hardwareService.checkDevicePermissions(device);
                result.append(device).append(": ").append(hasPermission ? "有权限" : "无权限").append("\\n");
            }
            updateUI(result.toString());
        }).start();
    }
    
    private void testAllHardware() {
        new Thread(() -> {
            StringBuilder result = new StringBuilder("所有硬件测试结果:\\n\\n");
            
            // 测试LED
            result.append("1. LED状态:\\n");
            RK3588HardwareService.LEDState ledState = hardwareService.getLEDState();
            if (ledState != null) {
                result.append(formatLEDState(ledState)).append("\\n\\n");
            } else {
                result.append("无法读取LED状态\\n\\n");
            }
            
            // 测试系统信息
            result.append("2. 系统信息:\\n");
            result.append(hardwareService.readSystemInfo()).append("\\n\\n");
            
            // 测试设备权限
            result.append("3. 设备权限检查:\\n");
            String[] devices = {"/dev/ttyS4", "/sys/class/leds/work/brightness", "/proc/version"};
            for (String device : devices) {
                boolean hasPermission = hardwareService.checkDevicePermissions(device);
                result.append(device).append(": ").append(hasPermission ? "有权限" : "无权限").append("\\n");
            }
            
            updateUI(result.toString());
        }).start();
    }
    
    private void updateUI(final String text) {
        handler.post(() -> {
            resultTextView.setText(text);
        });
    }
    
    private void controlWorkLED(final boolean enable) {
        new Thread(() -> {
            boolean success = hardwareService.controlWorkLED(enable);
            if (success) {
                updateUI("work灯控制成功: " + (enable ? "已开启" : "已关闭") + "\n\n正在更新状态...");
                
                // 延迟1秒后重新读取LED状态
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                
                // 重新读取并显示LED状态
                RK3588HardwareService.LEDState state = hardwareService.getLEDState();
                if (state != null) {
                    String result = formatLEDState(state);
                    updateUI("work灯控制成功: " + (enable ? "已开启" : "已关闭") + "\n\n" + result);
                } else {
                    updateUI("work灯控制成功: " + (enable ? "已开启" : "已关闭") + "\n\n但无法读取最新状态");
                }
            } else {
                // 提供详细的错误信息和替代方案
                StringBuilder errorMessage = new StringBuilder();
                errorMessage.append("work灯控制失败！\n\n");
                errorMessage.append("原因分析：\n");
                errorMessage.append("• work设备需要root权限才能直接控制\n");
                errorMessage.append("• 当前应用运行在普通用户权限下\n\n");
                errorMessage.append("替代解决方案：\n");
                errorMessage.append("1. 使用root权限运行应用\n");
                errorMessage.append("2. 通过ADB命令手动控制：\n");
                errorMessage.append("   开启: adb shell \"echo 255 > /sys/class/leds/work/brightness\"\n");
                errorMessage.append("   关闭: adb shell \"echo 0 > /sys/class/leds/work/brightness\"\n\n");
                errorMessage.append("3. 修改设备权限（需要root）：\n");
                errorMessage.append("   chmod 666 /sys/class/leds/work/brightness\n");
                errorMessage.append("   chmod 666 /sys/class/leds/work/trigger\n\n");
                errorMessage.append("当前work设备状态：\n");
                
                // 显示当前work设备状态
                RK3588HardwareService.LEDState state = hardwareService.getLEDState();
                if (state != null && state.workFound) {
                    errorMessage.append("• 亮度值: ").append(state.workBrightness).append("\n");
                    errorMessage.append("• 状态: ").append(state.workBrightness > 0 ? "亮" : "灭").append("\n");
                } else {
                    errorMessage.append("• 无法读取work设备状态\n");
                }
                
                updateUI(errorMessage.toString());
            }
        }).start();
    }
}