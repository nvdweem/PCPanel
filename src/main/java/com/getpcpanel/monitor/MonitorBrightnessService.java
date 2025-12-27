package com.getpcpanel.monitor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import com.getpcpanel.Json;
import com.getpcpanel.spring.OsHelper;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import one.util.streamex.StreamEx;

@Log4j2
@Service
@RequiredArgsConstructor
public class MonitorBrightnessService {
    private static final int COMMAND_TIMEOUT_MS = 4000;
    private final OsHelper osHelper;
    private final Json json;

    public List<MonitorInfo> getMonitors() {
        if (osHelper.notWindows()) {
            return Collections.emptyList();
        }

        var nativeMonitors = MonitorNativeUtil.listMonitors();
        if (!nativeMonitors.isEmpty()) {
            return nativeMonitors;
        }

        var output = runPowerShell(buildListScript());
        var jsonLine = extractJsonLine(output);
        if (StringUtils.isBlank(jsonLine)) {
            return Collections.emptyList();
        }
        try {
            var trimmed = jsonLine.trim();
            if ("null".equalsIgnoreCase(trimmed)) {
                return Collections.emptyList();
            }
            if (trimmed.startsWith("{")) {
                trimmed = "[" + trimmed + "]";
            }
            var monitors = json.read(trimmed, MonitorInfo[].class);
            return StreamEx.of(Arrays.asList(monitors)).distinct(MonitorInfo::id).toList();
        } catch (Exception e) {
            log.warn("Failed to parse monitor list: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    public void setBrightness(String monitorId, int value) {
        if (osHelper.notWindows()) {
            return;
        }
        var brightness = Math.max(0, Math.min(100, value));
        if (StringUtils.isBlank(monitorId)) {
            for (var monitor : getMonitors()) {
                setBrightnessSingle(monitor.id(), brightness);
            }
            return;
        }
        setBrightnessSingle(monitorId, brightness);
    }

    private void setBrightnessSingle(String monitorId, int value) {
        if (StringUtils.isBlank(monitorId)) {
            return;
        }
        if (monitorId.startsWith("dxva2:")) {
            var index = parseDxva2Index(monitorId);
            if (index >= 0) {
                if (MonitorNativeUtil.setBrightnessByIndex(index, value)) {
                    return;
                }
            }
            return;
        }
        var id = escapeSingleQuotes(monitorId);
        var script = """
                $ErrorActionPreference = 'SilentlyContinue'
                $id = '%s'
                $methods = Get-CimInstance -Namespace root/WMI -ClassName WmiMonitorBrightnessMethods
                if (-not $methods) {
                  $methods = Get-WmiObject -Namespace root\\WMI -Class WmiMonitorBrightnessMethods
                }
                $methods | Where-Object { $_.InstanceName -eq $id } | ForEach-Object {
                  if ($_.PSObject.Methods.Match('WmiSetBrightness').Count -gt 0) {
                    $_.WmiSetBrightness(1, %d) | Out-Null
                  } else {
                    Invoke-CimMethod -InputObject $_ -MethodName WmiSetBrightness -Arguments @{ Timeout = 1; Brightness = %d } | Out-Null
                  }
                }
                """.formatted(id, value, value);
        runPowerShell(script);
    }

    private String buildListScript() {
        return """
                $ErrorActionPreference = 'SilentlyContinue'
                $items = @()
                try {
                  $brightness = Get-CimInstance -Namespace root/WMI -ClassName WmiMonitorBrightness
                  if (-not $brightness) { $brightness = Get-WmiObject -Namespace root\\WMI -Class WmiMonitorBrightness }
                  $methods = Get-CimInstance -Namespace root/WMI -ClassName WmiMonitorBrightnessMethods
                  if (-not $methods) { $methods = Get-WmiObject -Namespace root\\WMI -Class WmiMonitorBrightnessMethods }
                  $ids = Get-CimInstance -Namespace root/WMI -ClassName WmiMonitorID
                  if (-not $ids) { $ids = Get-WmiObject -Namespace root\\WMI -Class WmiMonitorID }
                  $instances = @()
                  if ($brightness) { $instances += $brightness | Select-Object -ExpandProperty InstanceName }
                  if ($methods) { $instances += $methods | Select-Object -ExpandProperty InstanceName }
                  $instances = $instances | Sort-Object -Unique
                  $items = foreach ($inst in $instances) {
                    $id = $ids | Where-Object { $_.InstanceName -eq $inst }
                    $name = ''
                    if ($id) {
                      $name = ($id.UserFriendlyName | Where-Object { $_ -ne 0 } | ForEach-Object { [char]$_ }) -join ''
                    }
                    if ([string]::IsNullOrWhiteSpace($name)) { $name = $inst }
                    [pscustomobject]@{ id = $inst; name = $name }
                  }
                } catch {
                }

                if (-not $items -or $items.Count -eq 0) {
                  Add-Type -TypeDefinition @"
using System;
using System.Runtime.InteropServices;
public class MonitorNative {
  public delegate bool MonitorEnumProc(IntPtr hMonitor, IntPtr hdcMonitor, IntPtr lprcMonitor, IntPtr dwData);
  [StructLayout(LayoutKind.Sequential, CharSet=CharSet.Auto)]
  public struct PHYSICAL_MONITOR {
    public IntPtr hPhysicalMonitor;
    [MarshalAs(UnmanagedType.ByValTStr, SizeConst = 128)]
    public string szPhysicalMonitorDescription;
  }
  [DllImport("user32.dll")]
  public static extern bool EnumDisplayMonitors(IntPtr hdc, IntPtr lprcClip, MonitorEnumProc lpfnEnum, IntPtr dwData);
  [DllImport("dxva2.dll", SetLastError=true)]
  public static extern bool GetNumberOfPhysicalMonitorsFromHMONITOR(IntPtr hMonitor, out uint number);
  [DllImport("dxva2.dll", SetLastError=true)]
  public static extern bool GetPhysicalMonitorsFromHMONITOR(IntPtr hMonitor, uint count, [Out] PHYSICAL_MONITOR[] monitors);
  [DllImport("dxva2.dll", SetLastError=true)]
  public static extern bool DestroyPhysicalMonitors(uint count, PHYSICAL_MONITOR[] monitors);
}
"@
                  $list = New-Object System.Collections.Generic.List[Object]
                  $index = 0
                  $callback = [MonitorNative+MonitorEnumProc]{
                    param($hMon,$hdc,$rc,$data)
                    $count = 0
                    if ([MonitorNative]::GetNumberOfPhysicalMonitorsFromHMONITOR($hMon, [ref]$count)) {
                      $arr = New-Object MonitorNative+PHYSICAL_MONITOR[] $count
                      if ([MonitorNative]::GetPhysicalMonitorsFromHMONITOR($hMon, $count, $arr)) {
                        for ($i = 0; $i -lt $arr.Length; $i++) {
                          $desc = $arr[$i].szPhysicalMonitorDescription
                          if ([string]::IsNullOrWhiteSpace($desc)) { $desc = "Monitor " + $index }
                          $list.Add([pscustomobject]@{ id = ("dxva2:" + $index); name = $desc }) | Out-Null
                          $index++
                        }
                        [MonitorNative]::DestroyPhysicalMonitors($count, $arr) | Out-Null
                      }
                    }
                    return $true
                  }
                  [MonitorNative]::EnumDisplayMonitors([IntPtr]::Zero, [IntPtr]::Zero, $callback, [IntPtr]::Zero) | Out-Null
                  $items = $list
                }

                $items | Sort-Object name | ConvertTo-Json -Compress
                """;
    }

    private String runPowerShell(String script) {
        var command = new ProcessBuilder("powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-Command", script);
        command.redirectErrorStream(true);
        try {
            var process = command.start();
            if (!process.waitFor(COMMAND_TIMEOUT_MS, java.util.concurrent.TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                return "";
            }
            return new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        } catch (IOException | InterruptedException e) {
            log.warn("Failed to run PowerShell: {}", e.getMessage());
            return "";
        }
    }

    private String extractJsonLine(String output) {
        if (StringUtils.isBlank(output)) {
            return "";
        }
        var lines = output.split("\\R");
        for (int i = lines.length - 1; i >= 0; i--) {
            var line = lines[i].trim();
            if (line.startsWith("{") || line.startsWith("[") || "null".equalsIgnoreCase(line)) {
                return line;
            }
        }
        return "";
    }

    private int parseDxva2Index(String monitorId) {
        try {
            return Integer.parseInt(monitorId.substring("dxva2:".length()));
        } catch (Exception e) {
            return -1;
        }
    }

    private String escapeSingleQuotes(String input) {
        return input.replace("'", "''");
    }
}
