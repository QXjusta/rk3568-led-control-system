# Yuanzi LED Driver for RK3568

## Overview

This driver controls a GPIO-based LED on the RK3568 platform, with support for multiple modes and ADC key input.

## Features

- GPIO-based LED control
- Input event support for ADC keys (KEY_VOLUMEUP)
- Multiple LED modes:
  - Off (none)
  - On (default-on)
  - Heartbeat (pulsing effect)
  - Timer (blinking effect)
- Sysfs interface at `/sys/class/leds/work/`
- Character device at `/dev/yuanzi_led`
- Standard LED trigger support

## Changes Made

### 1. ADC Key Support Implementation

- **Removed**: GPIO button-related code (button_gpio, irq_number)
- **Added**: Input event handling for ADC keys
  - Uses `input_handler` to listen for KEY_VOLUMEUP events
  - Implemented `led_input_event()` to detect key presses
  - Added `led_input_connect()` and `led_input_disconnect()` functions
  - Set up input device ID table to match KEY_VOLUMEUP

### 2. Device Tree Update

- Removed `button-gpios` property from the device tree
- Updated comment to indicate ADC key usage

### 3. Code Structure Changes

- Updated private data structure to include input handler fields
- Updated error handling paths for input handler cleanup
- Modified `led_probe()` to register input handler
- Modified `led_remove()` to unregister input handler

## Compilation

### Requirements

- Linux kernel headers for your target platform
- Cross-compilation toolchain (if building on a different architecture)
- RK3568 device tree source files

### Compilation Steps

1. Set the `KERNEL_DIR` variable in the Makefile to point to your kernel source directory
2. Run `make` to compile the driver
3. The compiled module will be named `yuanzi_led.ko`

### Cross-Compilation Example

```bash
# Set up cross-compilation environment
export ARCH=arm64
export CROSS_COMPILE=aarch64-linux-gnu-

# Compile the driver
make KERNEL_DIR=/path/to/kernel/source
```

## Installation

1. Copy the compiled module to your target device
2. Load the module using `insmod yuanzi_led.ko`
3. The driver will create:
   - Character device: `/dev/yuanzi_led`
   - Sysfs interface: `/sys/class/leds/work/`

## Usage

### ADC Key Control

Pressing the volume up button (which generates KEY_VOLUMEUP events) will cycle through the LED modes:
1. Off → 2. On → 3. Heartbeat → 4. Timer → 5. Off → ...

### Sysfs Interface

```bash
# Check current brightness
cat /sys/class/leds/work/brightness

# Set brightness (0-255)
echo 255 > /sys/class/leds/work/brightness

# Change trigger mode
echo heartbeat > /sys/class/leds/work/trigger

# List available triggers
cat /sys/class/leds/work/trigger
```

### Character Device Interface

The driver supports IOCTL commands for controlling the LED. See the driver source code for details.

## Testing

1. Ensure the ADC keys driver is loaded and working
2. Press the volume up button and verify the LED mode cycles
3. Test sysfs commands to control the LED
4. Test character device IOCTL commands if needed

## Troubleshooting

### Driver Not Loading
- Check kernel log with `dmesg` for error messages
- Ensure the device tree has the correct `led-gpios` configuration
- Ensure you're using the correct kernel headers

### ADC Key Not Working
- Verify the ADC keys driver is loaded and functioning
- Check `/proc/bus/input/devices` to see if the key is registered
- Use `evtest` to monitor input events

### LED Not Responding
- Check GPIO configuration in the device tree
- Verify the GPIO is correctly connected to the LED
- Check kernel log for GPIO-related errors

## Uninstallation

```bash
rmmod yuanzi_led
```

## Files

- `yuanzi_led.c`: Main driver source code
- `Makefile`: Build configuration
- `rk3568/rk3568-evb1-ddr4-v10-linux.dts`: Updated device tree

## License

GPL