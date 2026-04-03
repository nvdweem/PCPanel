package com.getpcpanel.spring;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;

import com.getpcpanel.cpp.ISndCtrl;
import com.getpcpanel.cpp.linux.pulseaudio.PulseAudioImpl;
import com.getpcpanel.cpp.linux.pulseaudio.SndCtrlPulseAudio;
import com.getpcpanel.cpp.windows.SndCtrlWindows;
import com.getpcpanel.iconextract.IIconService;
import com.getpcpanel.iconextract.IconServiceLinux;
import com.getpcpanel.iconextract.IconServiceWindows;
import com.getpcpanel.util.tray.ITrayService;
import com.getpcpanel.util.tray.awt.AwtTrayImpl;
import com.getpcpanel.util.tray.awt.TrayServiceAwt;
import com.getpcpanel.util.tray.wayland.TrayServiceWayland;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import lombok.extern.log4j.Log4j2;

@Log4j2
@ApplicationScoped
public class PlatformProducer {

    @Inject @WindowsImpl Instance<ISndCtrl> windowsSndCtrl;
    @Inject @PulseAudioImpl Instance<ISndCtrl> pulseAudioSndCtrl;
    @Inject @AwtTrayImpl Instance<ITrayService> awtTray;
    @Inject @WaylandImpl Instance<ITrayService> waylandTray;
    @Inject @WindowsImpl Instance<IIconService> windowsIcons;
    @Inject @LinuxImpl Instance<IIconService> linuxIcons;

    @Produces
    @ApplicationScoped
    public ISndCtrl sndCtrl() {
        if (SystemUtils.IS_OS_WINDOWS && !windowsSndCtrl.isUnsatisfied()) {
            return windowsSndCtrl.get();
        }
        if (SystemUtils.IS_OS_LINUX && !pulseAudioSndCtrl.isUnsatisfied()) {
            return pulseAudioSndCtrl.get();
        }
        log.warn("No ISndCtrl implementation found for current OS, using no-op");
        return ISndCtrl.noOp();
    }

    @Produces
    @ApplicationScoped
    public ITrayService trayService() {
        var waylandDisplay = System.getenv("WAYLAND_DISPLAY");
        var xdgSessionType = System.getenv("XDG_SESSION_TYPE");
        boolean isWayland = StringUtils.isNotBlank(waylandDisplay) || StringUtils.equalsIgnoreCase(xdgSessionType, "wayland");
        if (SystemUtils.IS_OS_LINUX && isWayland && !waylandTray.isUnsatisfied()) {
            return waylandTray.get();
        }
        if (!awtTray.isUnsatisfied()) {
            return awtTray.get();
        }
        return new ITrayService.NoOp();
    }

    @Produces
    @ApplicationScoped
    public IIconService iconService() {
        if (SystemUtils.IS_OS_WINDOWS && !windowsIcons.isUnsatisfied()) {
            return windowsIcons.get();
        }
        if (SystemUtils.IS_OS_LINUX && !linuxIcons.isUnsatisfied()) {
            return linuxIcons.get();
        }
        return (width, height, file) -> null;
    }
}
