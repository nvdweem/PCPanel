package com.getpcpanel.integration.volume.platform.windows;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.getpcpanel.integration.volume.platform.AudioDevice;
import com.getpcpanel.integration.volume.platform.AudioSession;
import com.getpcpanel.integration.volume.platform.DataFlow;
import com.getpcpanel.integration.volume.platform.MuteType;
import com.getpcpanel.integration.volume.platform.windows.SndCtrlWindows.DefaultFor;

/**
 * Exercises the classes involved in Windows JNI/SndCtrl integration.
 *
 * <p>When run with the GraalVM native-image tracing agent these tests cause all
 * relevant classes, fields, and methods to be recorded in the generated
 * {@code jni-config.json} / {@code reflect-config.json} files.
 *
 * <p>Run the generation script at the project root to regenerate those files:
 * <pre>  generate-native-configs.cmd</pre>
 */
@DisplayName("SndCtrl native-image config coverage")
class SndCtrlNativeConfigTest {

    // ── AudioSession ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("AudioSession: instantiation and field access")
    void audioSessionCanBeInstantiated() {
        var session = new AudioSession(null, 1234, new File("chrome.exe"), "Google Chrome", null, 0.75f, false);

        assertEquals(1234, session.pid());
        assertEquals(0.75f, session.volume());
        assertEquals(new File("chrome.exe"), session.executable());
        assertFalse(session.muted());
        assertFalse(session.isSystemSounds());
    }

    @Test
    @DisplayName("AudioSession: system-sounds detection via pid=0")
    void systemSoundsDetectedByPid() {
        var session = new AudioSession(null, 0, new File("system"), "System Sounds", null, 0.5f, false);
        assertTrue(session.isSystemSounds());
    }

    @Test
    @DisplayName("AudioSession: system-sounds detected by AudioSrv icon path")
    void systemSoundsDetectedByIcon() {
        var session = new AudioSession(null, 42, new File("audio"), "Audio", "C:\\Windows\\AudioSrv.Dll", 0.5f, false);
        assertTrue(session.isSystemSounds());
    }

    @Test
    @DisplayName("AudioSession: muted flag is accessible")
    void audioSessionMutedFlag() {
        var session = new AudioSession(null, 99, new File("app.exe"), "App", null, 0.0f, true);
        assertTrue(session.muted());
    }

    // ── AudioDevice (base) ───────────────────────────────────────────────────

    @Test
    @DisplayName("AudioDevice: fields declared in class (JNI callbacks fill these)")
    void audioDeviceFieldsAreDeclared() throws Exception {
        var clazz = AudioDevice.class;
        // These fields are written by JNI native code via reflection
        assertNotNull(clazz.getDeclaredField("volume"));
        assertNotNull(clazz.getDeclaredField("muted"));
        assertNotNull(clazz.getDeclaredField("dataflow"));
        assertNotNull(clazz.getDeclaredField("name"));
        assertNotNull(clazz.getDeclaredField("id"));
    }

    @Test
    @DisplayName("AudioSession: fields declared in class (JNI callbacks fill these)")
    void audioSessionFieldsDeclared() throws Exception {
        // SndCtrl.dll calls back into Java and sets these fields via JNI.
        // Accessing them via getDeclaredField causes the tracing agent to record
        // AudioSession in the generated reachability-metadata.json.
        var clazz = AudioSession.class;
        assertNotNull(clazz.getDeclaredField("pid"));
        assertNotNull(clazz.getDeclaredField("volume"));
        assertNotNull(clazz.getDeclaredField("muted"));
        assertNotNull(clazz.getDeclaredField("executable"));
        assertNotNull(clazz.getDeclaredField("title"));
        assertNotNull(clazz.getDeclaredField("icon"));
    }

    @Test
    @DisplayName("AudioDevice: DataFlow enum values are accessible")
    void dataFlowEnumValues() {
        // DataFlow is passed as ordinal from native code
        assertEquals(0, DataFlow.dfRender.ordinal());
        assertEquals(1, DataFlow.dfCapture.ordinal());
        assertEquals(2, DataFlow.dfAll.ordinal());
        assertTrue(DataFlow.dfRender.output());
        assertTrue(DataFlow.dfCapture.input());
    }

    @Test
    @DisplayName("AudioDevice: MuteType enum works without event bus")
    void muteTypeEnum() {
        assertEquals(MuteType.toggle, MuteType.valueOf("toggle"));
        assertEquals(MuteType.mute, MuteType.valueOf("mute"));
        assertEquals(MuteType.unmute, MuteType.valueOf("unmute"));
    }

    // ── SndCtrlNative (enum, native methods) ─────────────────────────────────

    @Test
    @DisplayName("SndCtrlNative: singleton instance is accessible")
    void sndCtrlNativeInstanceIsAccessible() {
        // The enum instance must be accessible by JNI at runtime
        var instance = SndCtrlNative.instance;
        assertNotNull(instance);
    }

    @Test
    @DisplayName("SndCtrlNative: setDeviceVolume native method signature")
    void sndCtrlNativeSetDeviceVolumeSignature() throws Exception {
        var method = SndCtrlNative.class.getDeclaredMethod("setDeviceVolume", String.class, float.class);
        assertTrue(Modifier.isNative(method.getModifiers()));
    }

    @Test
    @DisplayName("SndCtrlNative: setProcessVolume native method signature")
    void sndCtrlNativeSetProcessVolumeSignature() throws Exception {
        var method = SndCtrlNative.class.getDeclaredMethod("setProcessVolume", String.class, int.class, float.class);
        assertTrue(Modifier.isNative(method.getModifiers()));
    }

    @Test
    @DisplayName("SndCtrlNative: muteDevice native method signature")
    void sndCtrlNativeMuteDeviceSignature() throws Exception {
        var method = SndCtrlNative.class.getDeclaredMethod("muteDevice", String.class, boolean.class);
        assertTrue(Modifier.isNative(method.getModifiers()));
    }

    @Test
    @DisplayName("SndCtrlNative: muteSession native method signature")
    void sndCtrlNativeMuteSessionSignature() throws Exception {
        var method = SndCtrlNative.class.getDeclaredMethod("muteSession", String.class, int.class, boolean.class);
        assertTrue(Modifier.isNative(method.getModifiers()));
    }

    @Test
    @DisplayName("SndCtrlNative: getFocusApplication native method signature")
    void sndCtrlNativeGetFocusApplicationSignature() throws Exception {
        var method = SndCtrlNative.class.getDeclaredMethod("getFocusApplication");
        assertTrue(Modifier.isNative(method.getModifiers()));
    }

    @Test
    @DisplayName("SndCtrlNative: getFocusApplication native method signature")
    void sndNativePrivateMethods() throws Exception {
        assertDoesNotThrow(() -> {
            var sndCtrl = new SndCtrlWindows();
            sndCtrl.deviceAdded("", "", 0f, false, 0);
            sndCtrl.deviceRemoved("");
            sndCtrl.setDefaultDevice("", 1, 1);
            sndCtrl.focusChanged("");
        });
    }

    @Test
    @DisplayName("SndCtrlNative: static start() method is callable via reflection")
    void sndCtrlNativeStartMethodAccessible() throws Exception {
        Method start = SndCtrlNative.class.getDeclaredMethod("start", Object.class);
        assertNotNull(start);
        assertTrue(Modifier.isNative(start.getModifiers()));
    }

    @Test
    @DisplayName("SndCtrlNative: hasAudioPolicyConfigFactory method declared")
    void sndCtrlNativeHasAudioPolicyConfigFactory() throws Exception {
        var method = SndCtrlNative.class.getDeclaredMethod("hasAudioPolicyConfigFactory");
        assertNotNull(method);
        assertTrue(Modifier.isNative(method.getModifiers()));
    }

    @Test
    @DisplayName("SndCtrlNative: setPersistedDefaultAudioEndpoint method declared")
    void sndCtrlNativeSetPersistedDefaultAudioEndpoint() throws Exception {
        var method = SndCtrlNative.class.getDeclaredMethod("setPersistedDefaultAudioEndpoint", int.class, int.class, String.class);
        assertNotNull(method);
        assertTrue(Modifier.isNative(method.getModifiers()));
    }

    // ── WindowsAudioDevice (JNI callback receiver) ───────────────────────────

    @Test
    @DisplayName("WindowsAudioDevice: addSession JNI callback signature matches native side")
    void windowsAudioDeviceAddSessionSignature() throws Exception {
        // JNI calls this method reflectively; signature must be stable
        var m = WindowsAudioDevice.class.getDeclaredMethod(
                "addSession", long.class, int.class,
                String.class, String.class, String.class, float.class, boolean.class);
        assertNotNull(m);
    }

    @Test
    @DisplayName("WindowsAudioDevice: removeSession JNI callback signature matches native side")
    void windowsAudioDeviceRemoveSessionSignature() throws Exception {
        var m = WindowsAudioDevice.class.getDeclaredMethod("removeSession", long.class, int.class);
        assertNotNull(m);
    }

    // ── SndCtrlWindows DefaultFor enum ───────────────────────────────────────

    @Test
    @DisplayName("SndCtrlWindows.DefaultFor: all enum constants resolvable")
    void defaultForEnumConstants() {
        assertNotNull(DefaultFor.mediaPlayback);
        assertNotNull(DefaultFor.mediaRecord);
        assertNotNull(DefaultFor.communicationPlayback);
        assertNotNull(DefaultFor.communicationRecord);
    }

    @Test
    @DisplayName("SndCtrlWindows.DefaultFor: lookup by dataflow + role ordinals (mirrors native code)")
    void defaultForOfOrdinalLookup() {
        var result = DefaultFor.of(DataFlow.dfRender.ordinal(), 1 /* roleMultimedia */);
        assertNotNull(result);
        assertEquals(DefaultFor.mediaPlayback, result);
    }

    // ── Reflection coverage: all declared methods on SndCtrlWindows ──────────

    @Test
    @DisplayName("SndCtrlWindows: all declared methods are reflectively accessible")
    void sndCtrlWindowsAllMethodsAccessible() {
        assertDoesNotThrow(() -> {
            var methods = SndCtrlWindows.class.getDeclaredMethods();
            assertTrue(methods.length > 0);
            for (var m : methods) {
                m.setAccessible(true); // exercises reflection path used by JNI
            }
        });
    }

    @Test
    @DisplayName("SndCtrlWindows: all declared fields are reflectively accessible")
    void sndCtrlWindowsAllFieldsAccessible() {
        assertDoesNotThrow(() -> {
            var fields = SndCtrlWindows.class.getDeclaredFields();
            for (var f : fields) {
                f.setAccessible(true);
            }
        });
    }
}
