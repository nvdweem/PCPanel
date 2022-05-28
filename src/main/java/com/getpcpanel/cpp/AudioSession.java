package com.getpcpanel.cpp;

import java.io.File;

import org.apache.commons.lang3.StringUtils;

import lombok.AccessLevel;
import lombok.Data;
import lombok.Setter;
import lombok.ToString;
import lombok.extern.log4j.Log4j2;

@Data
@Log4j2
@Setter(AccessLevel.PRIVATE)
public class AudioSession {
    public static final String SYSTEM = "System Sounds";
    @ToString.Exclude private AudioDevice device;
    private int pid;
    private File executable;
    private String title;
    private String icon;
    private float volume;
    private boolean muted;

    public AudioSession(AudioDevice device, int pid, File executable, String title, String icon, float volume, boolean muted) {
        this.device = device;
        this.pid = pid;
        this.executable = executable;
        this.title = pid == 0 ? SYSTEM : StringUtils.firstNonBlank(title, executable.getName());
        this.icon = icon;
        this.volume = volume;
        this.muted = muted;
    }
}
