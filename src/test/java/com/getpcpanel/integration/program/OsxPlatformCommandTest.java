package com.getpcpanel.integration.program;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.getpcpanel.platform.process.OsxProcessHelper;
import com.getpcpanel.util.os.ProcessHelper;

class OsxPlatformCommandTest {
    @Test
    void focusedTerminationUsesFreshForegroundForEachCommand() throws Exception {
        var processes = mock(ProcessHelper.class);
        when(processes.run(any(Duration.class), eq(ProcessHelper.PARSEABLE_OUTPUT), eq("lsappinfo"), eq("front")))
                .thenReturn(new ProcessHelper.Result(false, 0, List.of("ASN:A"), List.of()),
                            new ProcessHelper.Result(false, 0, List.of("ASN:B"), List.of()));
        when(processes.run(any(Duration.class), eq(ProcessHelper.PARSEABLE_OUTPUT), eq("lsappinfo"), eq("info"),
                eq("-only"), eq("name,pid,executablepath,bundleid"), eq("ASN:A")))
                .thenReturn(new ProcessHelper.Result(false, 0, List.of("\"pid\"=100"), List.of()));
        when(processes.run(any(Duration.class), eq(ProcessHelper.PARSEABLE_OUTPUT), eq("lsappinfo"), eq("info"),
                eq("-only"), eq("name,pid,executablepath,bundleid"), eq("ASN:B")))
                .thenReturn(new ProcessHelper.Result(false, 0, List.of("\"pid\"=200"), List.of()));
        var command = new IPlatformCommand.OsxPlatformCommand(new OsxProcessHelper(processes), processes);
        command.kill(IPlatformCommand.FOCUS);
        command.kill(IPlatformCommand.FOCUS);

        // launch is mocked: no application is actually terminated.
        var order = inOrder(processes);
        order.verify(processes).launch("/bin/kill", "100");
        order.verify(processes).launch("/bin/kill", "200");
    }
}
