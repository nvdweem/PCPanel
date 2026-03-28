package com.getpcpanel;

import org.apache.commons.lang3.SystemUtils;

import com.getpcpanel.cpp.ISndCtrl;
import com.getpcpanel.cpp.linux.pulseaudio.SndCtrlPulseAudio;
import com.getpcpanel.cpp.windows.SndCtrlWindows;
import com.getpcpanel.iconextract.IIconService;
import com.getpcpanel.iconextract.IconServiceLinux;
import com.getpcpanel.iconextract.IconServiceWindows;
import com.getpcpanel.util.IPlatformCommand;
import com.getpcpanel.util.IPlatformCommand.LinuxPlatformCommand;
import com.getpcpanel.util.IPlatformCommand.WindowsPlatformCommand;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Default;
import jakarta.enterprise.inject.Produces;

@ApplicationScoped
class ConditionalBeans {
    @Produces
    @Default
    ISndCtrl iSndCtrl(SndCtrlWindows windows, SndCtrlPulseAudio linux) {
        return windowsOrLinux(windows, linux);
    }

    @Default
    @Produces
    IIconService iIconService(IconServiceWindows windows, IconServiceLinux linux) {
        return windowsOrLinux(windows, linux);
    }

    @Default
    @Produces
    IPlatformCommand iPlatformCommand(WindowsPlatformCommand windows, LinuxPlatformCommand linux) {
        return windowsOrLinux(windows, linux);
    }

    private <T> T windowsOrLinux(T windows, T linux) {
        return SystemUtils.IS_OS_WINDOWS ? windows : linux;
    }
}
