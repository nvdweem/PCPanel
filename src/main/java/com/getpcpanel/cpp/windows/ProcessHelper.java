package com.getpcpanel.cpp.windows;

import java.io.File;
import java.util.Comparator;
import java.util.List;

import com.getpcpanel.cpp.ISndCtrl;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.PsapiUtil;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.ptr.IntByReference;

import one.util.streamex.IntStreamEx;

final class ProcessHelper {
    private static final int PROCESS_QUERY_INFORMATION = 0x0400;

    private ProcessHelper() {
    }

    public static List<ISndCtrl.RunningApplication> getRunningApplications() {
        return IntStreamEx.of(PsapiUtil.enumProcesses())
                          .mapToEntry(Integer::valueOf, ProcessHelper::getFile)
                          .nonNullValues()
                          .mapKeyValue((pid, file) -> new ISndCtrl.RunningApplication(pid, file, file.getName()))
                          .sorted(Comparator.comparing(ISndCtrl.RunningApplication::name))
                          .toImmutableList();
    }

    static File getFile(int pid) {
        var handle = Kernel32.INSTANCE.OpenProcess(PROCESS_QUERY_INFORMATION, false, pid);
        var buffer = new char[WinDef.MAX_PATH];
        var size = new IntByReference(buffer.length);
        if (!Kernel32.INSTANCE.QueryFullProcessImageName(handle, 0, buffer, size)) {
            return null;
        }
        return new File(new String(buffer, 0, size.getValue()));
    }
}
