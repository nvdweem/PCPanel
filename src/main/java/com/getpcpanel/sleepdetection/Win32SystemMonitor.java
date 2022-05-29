package com.getpcpanel.sleepdetection;

import java.util.ArrayList;
import java.util.function.Consumer;

import com.sun.jna.WString;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.platform.win32.WinDef.LPARAM;
import com.sun.jna.platform.win32.WinDef.LRESULT;
import com.sun.jna.platform.win32.WinDef.WPARAM;
import com.sun.jna.platform.win32.WinUser;
import com.sun.jna.platform.win32.WinUser.MSG;
import com.sun.jna.platform.win32.WinUser.WNDCLASSEX;
import com.sun.jna.platform.win32.WinUser.WindowProc;
import com.sun.jna.platform.win32.Wtsapi32;

import lombok.extern.log4j.Log4j2;

/**
 * https://gist.github.com/luanht/88ba957b94f94792a1fd
 */
@Log4j2
public enum Win32SystemMonitor implements WindowProc {
    instance;

    private static final int WM_POWERBROADCAST = 536;
    private static final int PBT_APMRESUMESUSPEND = 7;
    private static final int PBT_APMSUSPEND = 4;
    private static final ArrayList<IWin32SystemMonitorListener> LISTENERS = new ArrayList<>();

    public static void removeListener(IWin32SystemMonitorListener listener) {
        LISTENERS.remove(listener);
    }

    public static void addListener(IWin32SystemMonitorListener listener) {
        LISTENERS.add(listener);
    }

    Win32SystemMonitor() {
        new Thread(() -> {
            var windowClass = new WString("AnotherWindowClass");
            var hInst = Kernel32.INSTANCE.GetModuleHandle("");

            var wClass = new WNDCLASSEX();
            wClass.hInstance = hInst;
            wClass.lpfnWndProc = this;
            wClass.lpszClassName = windowClass.toString();

            // register window class
            User32.INSTANCE.RegisterClassEx(wClass);

            // create new window
            var hWnd = User32.INSTANCE
                    .CreateWindowEx(
                            User32.WS_EX_TOPMOST,
                            windowClass.toString(),
                            "PCPanel Power Event hidden helper window",
                            0, 0, 0, 0, 0,
                            null, // WM_DEVICECHANGE contradicts parent=WinUser.HWND_MESSAGE
                            null, hInst, null);

            Wtsapi32.INSTANCE.WTSRegisterSessionNotification(hWnd,
                    Wtsapi32.NOTIFY_FOR_THIS_SESSION);

            var msg = new MSG();
            while (User32.INSTANCE.GetMessage(msg, hWnd, 0, 0) != 0) {
                User32.INSTANCE.TranslateMessage(msg);
                User32.INSTANCE.DispatchMessage(msg);
            }

            Wtsapi32.INSTANCE.WTSUnRegisterSessionNotification(hWnd);
            User32.INSTANCE.UnregisterClass(windowClass.toString(), hInst);
            User32.INSTANCE.DestroyWindow(hWnd);
        }).start();
    }

    /*
     * uMSG 689 => session change
     * uMSG 536 => power change
     */
    @Override
    public LRESULT callback(HWND hwnd, int uMsg, WPARAM wParam, LPARAM lParam) {
        log.trace("HWND: {}\tuMsg: {}\tWPARAM: {}\tLPARAM: {}", hwnd, uMsg, wParam, lParam);
        return switch (uMsg) {
            case WM_POWERBROADCAST -> {
                onPowerChange(wParam);
                yield new LRESULT(0);
            }
            case WinUser.WM_DESTROY -> {
                User32.INSTANCE.PostQuitMessage(0);
                yield new LRESULT(0);
            }
            case WinUser.WM_SESSION_CHANGE -> {
                onSessionChange(wParam, lParam);
                yield new LRESULT(0);
            }
            default -> User32.INSTANCE.DefWindowProc(hwnd, uMsg, wParam, lParam);
        };
    }

    /**
     * On session change.
     *
     * @param wParam the w param
     * @param lParam the l param
     */
    private void onSessionChange(WPARAM wParam, LPARAM lParam) {
        switch (wParam.intValue()) {
            case Wtsapi32.WTS_SESSION_LOGON -> {
                lParam.intValue();
                allListeners(IWin32SystemMonitorListener::onMachineLogon);
            }
            case Wtsapi32.WTS_SESSION_LOGOFF -> {
                lParam.intValue();
                allListeners(IWin32SystemMonitorListener::onMachineLogoff);
            }
            case Wtsapi32.WTS_SESSION_LOCK -> {
                lParam.intValue();
                allListeners(IWin32SystemMonitorListener::onMachineLocked);
            }
            case Wtsapi32.WTS_SESSION_UNLOCK -> {
                lParam.intValue();
                allListeners(IWin32SystemMonitorListener::onMachineUnlocked);
            }
            default -> log.trace("Unknown event: {}", wParam.intValue());
        }
    }

    private void onPowerChange(WPARAM wParam) {
        if (wParam.intValue() == PBT_APMSUSPEND) {
            allListeners(IWin32SystemMonitorListener::onMachineGoingToSuspend);
        } else if (wParam.intValue() == PBT_APMRESUMESUSPEND) {
            allListeners(IWin32SystemMonitorListener::onMachineResumedFromSuspend);
        }
    }

    private void allListeners(Consumer<IWin32SystemMonitorListener> run) {
        LISTENERS.forEach(run);
    }

    interface IWin32SystemMonitorListener {
        default void onMachineGoingToSuspend() {
        }

        default void onMachineLocked() {
        }

        default void onMachineUnlocked() {
        }

        default void onMachineLogon() {
        }

        default void onMachineLogoff() {
        }

        default void onMachineResumedFromSuspend() {
        }
    }
}