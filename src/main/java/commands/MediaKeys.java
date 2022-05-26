/**
 * Needs to be here because of the JNI bindings
 */
package commands;

import com.getpcpanel.util.Util;

public final class MediaKeys {
    static {
        System.load(Util.extractAndDeleteOnExit("MediaKeys.dll").toString());
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
}
