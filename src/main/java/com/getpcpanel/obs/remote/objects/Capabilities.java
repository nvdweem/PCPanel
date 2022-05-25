package com.getpcpanel.obs.remote.objects;

public class Capabilities {
    private boolean isAsync;

    private boolean hasVideo;

    private boolean hasAudio;

    private boolean canInteract;

    private boolean isComposite;

    private boolean doNotDuplicate;

    private boolean doNotSelfMonitor;

    public boolean isAsync() {
        return isAsync;
    }

    public boolean isVideo() {
        return hasVideo;
    }

    public boolean isAudio() {
        return hasAudio;
    }

    public boolean canInteract() {
        return canInteract;
    }

    public boolean isComposite() {
        return isComposite;
    }

    public boolean isDoNotDuplicate() {
        return doNotDuplicate;
    }

    public boolean isDoNotSelfMonitor() {
        return doNotSelfMonitor;
    }

    public String toString() {
        return "Capabilities [isAsync=" + isAsync + ", hasVideo=" + hasVideo + ", hasAudio=" + hasAudio +
                ", canInteract=" + canInteract + ", isComposite=" + isComposite + ", doNotDuplicate=" + doNotDuplicate +
                ", doNotSelfMonitor=" + doNotSelfMonitor + "]";
    }
}
