package com.wardrumstudios.utils;

import android.os.Bundle;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.ViewParent;

import com.bda.controller.ControllerListener;
import com.bda.controller.StateEvent;

public class WarGamepad extends WarBilling implements ControllerListener {
    private static final int MAX_GAME_PADS = 4;

    public final GamePad[] GamePads = new GamePad[MAX_GAME_PADS];

    public static class GamePad {
        public final float[] GamepadAxes = new float[6];
        public int GamepadButtonMask = 0;
        public int GamepadType = -1;
        public boolean active = false;
        public int deviceId = -1;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        for (int i = 0; i < GamePads.length; i++) {
            GamePads[i] = new GamePad();
        }

        super.onCreate(savedInstanceState);
    }

    public native boolean processTouchpadAsPointer(ViewParent viewParent, boolean processAsPointer);

    public void SetReportPS3As360(boolean reportPS3as360) {
    }

    public int InitMogaController(int index) {
        return -1;
    }

    @Override
    public void SetGamepad(String gamepadString) {
    }

    public void GamepadReportSurfaceCreated(SurfaceHolder holder) {
    }

    public void TouchpadEvent(int touchAction, int count, int x1, int y1, int x2, int y2) {
    }

    public int GetGamepadType(int index) {
        GamePad gamePad = getGamePad(index);
        return gamePad == null ? -1 : gamePad.GamepadType;
    }

    public int GetGamepadButtons(int index) {
        GamePad gamePad = getGamePad(index);
        return gamePad == null ? 0 : gamePad.GamepadButtonMask;
    }

    public float GetGamepadAxis(int index, int axisId) {
        GamePad gamePad = getGamePad(index);
        if (gamePad == null || axisId < 0 || axisId >= gamePad.GamepadAxes.length) {
            return 0.0f;
        }

        return gamePad.GamepadAxes[axisId];
    }

    public int GetGamepadTrack(int index, int trackId, int coord) {
        return 0;
    }

    @Override
    public boolean onGenericMotionEvent(MotionEvent event) {
        if (event != null && isGamepadEvent(event.getSource())) {
            GamePad gamePad = getOrCreateGamePad(event.getDeviceId());
            if (gamePad != null) {
                gamePad.GamepadAxes[0] = cleanAxis(event.getAxisValue(MotionEvent.AXIS_X));
                gamePad.GamepadAxes[1] = cleanAxis(event.getAxisValue(MotionEvent.AXIS_Y));
                gamePad.GamepadAxes[2] = cleanAxis(event.getAxisValue(MotionEvent.AXIS_Z));
                gamePad.GamepadAxes[3] = cleanAxis(event.getAxisValue(MotionEvent.AXIS_RZ));
                gamePad.GamepadAxes[4] = cleanAxis(event.getAxisValue(MotionEvent.AXIS_LTRIGGER));
                gamePad.GamepadAxes[5] = cleanAxis(event.getAxisValue(MotionEvent.AXIS_RTRIGGER));
            }
        }

        return super.onGenericMotionEvent(event);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        updateButtonMask(keyCode, event, true);
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        updateButtonMask(keyCode, event, false);
        return super.onKeyUp(keyCode, event);
    }

    private void updateButtonMask(int keyCode, KeyEvent event, boolean pressed) {
        if (event == null || !isGamepadEvent(event.getSource())) {
            return;
        }

        int mask = buttonMaskForKey(keyCode);
        if (mask == 0) {
            return;
        }

        GamePad gamePad = getOrCreateGamePad(event.getDeviceId());
        if (gamePad == null) {
            return;
        }

        if (pressed) {
            gamePad.GamepadButtonMask |= mask;
        } else {
            gamePad.GamepadButtonMask &= ~mask;
        }
    }

    private static int buttonMaskForKey(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_BUTTON_A:
                return 1;
            case KeyEvent.KEYCODE_BUTTON_B:
                return 2;
            case KeyEvent.KEYCODE_BUTTON_X:
                return 4;
            case KeyEvent.KEYCODE_BUTTON_Y:
                return 8;
            case KeyEvent.KEYCODE_BUTTON_START:
                return 16;
            case KeyEvent.KEYCODE_BUTTON_SELECT:
            case KeyEvent.KEYCODE_BACK:
                return 32;
            case KeyEvent.KEYCODE_BUTTON_L1:
                return 64;
            case KeyEvent.KEYCODE_BUTTON_R1:
                return 128;
            case KeyEvent.KEYCODE_DPAD_UP:
                return 256;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                return 512;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                return 1024;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                return 2048;
            default:
                return 0;
        }
    }

    private static boolean isGamepadEvent(int source) {
        return (source & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD
                || (source & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK;
    }

    private static float cleanAxis(float value) {
        return Math.abs(value) < 0.25f ? 0.0f : value;
    }

    private GamePad getGamePad(int index) {
        if (index < 0 || index >= GamePads.length) {
            return null;
        }

        return GamePads[index];
    }

    private GamePad getOrCreateGamePad(int deviceId) {
        for (GamePad gamePad : GamePads) {
            if (gamePad.active && gamePad.deviceId == deviceId) {
                return gamePad;
            }
        }

        for (GamePad gamePad : GamePads) {
            if (!gamePad.active) {
                gamePad.active = true;
                gamePad.deviceId = deviceId;
                gamePad.GamepadType = 5;
                return gamePad;
            }
        }

        return null;
    }

    @Override
    public void onKeyEvent(com.bda.controller.KeyEvent event) {
    }

    @Override
    public void onMotionEvent(com.bda.controller.MotionEvent event) {
    }

    @Override
    public void onStateEvent(StateEvent event) {
    }
}
