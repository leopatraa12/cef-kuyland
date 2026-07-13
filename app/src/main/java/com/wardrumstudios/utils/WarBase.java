package com.wardrumstudios.utils;

import android.hardware.usb.UsbDevice;

import com.nvidia.devtech.NvEventQueueActivity;

/**
 * Public compatibility base for the legacy game-activity bridge.
 *
 * The original runtime may provide vendor-specific behavior here. The public
 * source keeps only the small lifecycle and extension points needed by the
 * launcher/client code.
 */
public class WarBase extends NvEventQueueActivity {
    public boolean FinalRelease = false;

    public void USBDeviceAttached(UsbDevice device, String name) {
    }

    public void USBDeviceDetached(UsbDevice device, String name) {
    }

    public void SetGamepad(String gamepadString) {
    }
}
