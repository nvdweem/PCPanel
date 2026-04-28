package com.getpcpanel.voicemeeter;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.getpcpanel.voicemeeter.VoicemeeterInstance.tagVBVMR_AUDIOBUFFER;
import com.getpcpanel.voicemeeter.VoicemeeterInstance.tagVBVMR_AUDIOINFO;
import com.getpcpanel.voicemeeter.VoicemeeterInstance.tagVBVMR_INTERFACE;
import com.sun.jna.Structure;
import com.sun.jna.win32.StdCallLibrary;

/**
 * Exercises the Voicemeeter JNA classes referenced in the native-image
 * {@code jni-config.json} and {@code proxy-config.json}.
 *
 * <p>Only the inner {@link Structure} subclasses (which have no native
 * dependencies) are actually instantiated.  The outer {@link VoicemeeterInstance}
 * proxy interface is inspected via reflection only – loading the JNA proxy
 * would attempt to locate the Voicemeeter DLL which may not be installed.
 */
@DisplayName("Voicemeeter native-image config coverage")
class VoicemeeterNativeTest {

    // ── VoicemeeterInstance (JNA proxy interface) ─────────────────────────────

    @Test
    @DisplayName("VoicemeeterInstance is a StdCallLibrary interface")
    void voicemeeterInstanceIsStdCallLibrary() {
        assertTrue(VoicemeeterInstance.class.isInterface());
        assertTrue(StdCallLibrary.class.isAssignableFrom(VoicemeeterInstance.class));
    }

    @Test
    @DisplayName("VoicemeeterInstance declares the expected API methods")
    void voicemeeterInstanceDeclaresMethods() throws Exception {
        var clazz = VoicemeeterInstance.class;
        assertNotNull(clazz.getDeclaredMethod("VBVMR_Login"));
        assertNotNull(clazz.getDeclaredMethod("VBVMR_Logout"));
        assertNotNull(clazz.getDeclaredMethod("VBVMR_IsParametersDirty"));
        assertNotNull(clazz.getDeclaredMethod("VBVMR_GetParameterFloat",
                com.sun.jna.Pointer.class, com.sun.jna.Pointer.class));
        assertNotNull(clazz.getDeclaredMethod("VBVMR_SetParameterFloat",
                com.sun.jna.Pointer.class, float.class));
    }

    // ── tagVBVMR_AUDIOINFO (JNA Structure inner class) ────────────────────────

    @Test
    @DisplayName("tagVBVMR_AUDIOINFO can be instantiated")
    void audioInfoInstantiates() {
        var info = new tagVBVMR_AUDIOINFO();
        assertNotNull(info);
    }

    @Test
    @DisplayName("tagVBVMR_AUDIOINFO declares samplerate and nbSamplePerFrame fields")
    void audioInfoFieldsDeclared() throws Exception {
        assertNotNull(tagVBVMR_AUDIOINFO.class.getDeclaredField("samplerate"));
        assertNotNull(tagVBVMR_AUDIOINFO.class.getDeclaredField("nbSamplePerFrame"));
    }

    @Test
    @DisplayName("tagVBVMR_AUDIOINFO extends Structure (required by JNA)")
    void audioInfoExtendsStructure() {
        assertTrue(Structure.class.isAssignableFrom(tagVBVMR_AUDIOINFO.class));
    }

    @Test
    @DisplayName("tagVBVMR_AUDIOINFO getFieldOrder matches declared fields")
    void audioInfoFieldOrder() {
        var order = new tagVBVMR_AUDIOINFO().getFieldOrder();
        assertTrue(order.contains("samplerate"));
        assertTrue(order.contains("nbSamplePerFrame"));
    }

    // ── tagVBVMR_AUDIOBUFFER (JNA Structure inner class) ──────────────────────

    @Test
    @DisplayName("tagVBVMR_AUDIOBUFFER can be instantiated")
    void audioBufferInstantiates() {
        var buf = new tagVBVMR_AUDIOBUFFER();
        assertNotNull(buf);
    }

    @Test
    @DisplayName("tagVBVMR_AUDIOBUFFER declares all expected fields")
    void audioBufferFieldsDeclared() throws Exception {
        var clazz = tagVBVMR_AUDIOBUFFER.class;
        assertNotNull(clazz.getDeclaredField("audiobuffer_sr"));
        assertNotNull(clazz.getDeclaredField("audiobuffer_nbs"));
        assertNotNull(clazz.getDeclaredField("audiobuffer_nbi"));
        assertNotNull(clazz.getDeclaredField("audiobuffer_nbo"));
        assertNotNull(clazz.getDeclaredField("audiobuffer_r"));
        assertNotNull(clazz.getDeclaredField("audiobuffer_w"));
    }

    @Test
    @DisplayName("tagVBVMR_AUDIOBUFFER extends Structure (required by JNA)")
    void audioBufferExtendsStructure() {
        assertTrue(Structure.class.isAssignableFrom(tagVBVMR_AUDIOBUFFER.class));
    }

    @Test
    @DisplayName("tagVBVMR_AUDIOBUFFER getFieldOrder matches declared fields")
    void audioBufferFieldOrder() {
        var order = new tagVBVMR_AUDIOBUFFER().getFieldOrder();
        assertTrue(order.contains("audiobuffer_sr"));
        assertTrue(order.contains("audiobuffer_r"));
        assertTrue(order.contains("audiobuffer_w"));
    }

    // ── tagVBVMR_INTERFACE (JNA Structure inner class) ────────────────────────

    @Test
    @DisplayName("tagVBVMR_INTERFACE can be instantiated")
    void interfaceStructureInstantiates() {
        var iface = new tagVBVMR_INTERFACE();
        assertNotNull(iface);
    }

    @Test
    @DisplayName("tagVBVMR_INTERFACE extends Structure (required by JNA)")
    void interfaceStructureExtendsStructure() {
        assertTrue(Structure.class.isAssignableFrom(tagVBVMR_INTERFACE.class));
    }

    @Test
    @DisplayName("tagVBVMR_INTERFACE getFieldOrder includes VBVMR_Login and VBVMR_Logout")
    void interfaceStructureFieldOrder() {
        var order = new tagVBVMR_INTERFACE().getFieldOrder();
        assertTrue(order.contains("VBVMR_Login"));
        assertTrue(order.contains("VBVMR_Logout"));
    }

    @Test
    @DisplayName("tagVBVMR_INTERFACE declares all callback pointer fields")
    void interfaceStructureFieldsDeclared() throws Exception {
        var clazz = tagVBVMR_INTERFACE.class;
        assertNotNull(clazz.getDeclaredField("VBVMR_Login"));
        assertNotNull(clazz.getDeclaredField("VBVMR_Logout"));
        assertNotNull(clazz.getDeclaredField("VBVMR_GetParameterFloat"));
        assertNotNull(clazz.getDeclaredField("VBVMR_SetParameterFloat"));
        assertNotNull(clazz.getDeclaredField("VBVMR_AudioCallbackRegister"));
        assertNotNull(clazz.getDeclaredField("VBVMR_AudioCallbackStart"));
        assertNotNull(clazz.getDeclaredField("VBVMR_AudioCallbackStop"));
        assertNotNull(clazz.getDeclaredField("VBVMR_AudioCallbackUnregister"));
    }
}
