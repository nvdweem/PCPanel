package commands;

public final class MediaKeys {
    static {
        System.loadLibrary("MediaKeys");
    }

    private MediaKeys() {
    }

    public static native void volumeMute();

    public static native void volumeDown();

    public static native void volumeUp();

    public static native void songPrevious();

    public static native void songNext();

    public static native void songPlayPause();

    public static native void mediaStop();

    public static void main(String[] args) {
        volumeMute();
    }
}
