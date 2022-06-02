package com.getpcpanel.obs.remote.objects;

import lombok.Data;

@Data
public class Capabilities {
    private boolean isAsync;
    private boolean hasVideo;
    private boolean hasAudio;
    private boolean canInteract;
    private boolean isComposite;
    private boolean doNotDuplicate;
    private boolean doNotSelfMonitor;
}
