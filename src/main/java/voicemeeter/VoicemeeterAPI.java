package voicemeeter;

import java.util.Set;

import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class VoicemeeterAPI {
    private static VoicemeeterInstance instance;

    public static String DEFAULT_VM_WINDOWS_64BIT_PATH = "C:/Program Files (x86)/VB/Voicemeeter/VoicemeeterRemote64.dll";

    public static String DEFAULT_VM_WINDOWS_32BIT_PATH = "";

    public static void init() {
        init(true);
    }

    public static void init(boolean is64bit) {
        init(is64bit, is64bit ? DEFAULT_VM_WINDOWS_64BIT_PATH : DEFAULT_VM_WINDOWS_32BIT_PATH);
    }

    public static void init(boolean is64bit, String vmWindowsPath) {
        System.load(vmWindowsPath);
        instance = Native.loadLibrary("VoicemeeterRemote" + (is64bit ? "64" : ""), VoicemeeterInstance.class);
    }

    public static void init(VoicemeeterInstance voicemeeterInstance) {
        instance = voicemeeterInstance;
    }

    public static void login() throws VoicemeeterException {
        ensureNoError(instance.VBVMR_Login(), 0, 1);
    }

    public static void logout() {
        ensureNoError(instance.VBVMR_Logout(), 0);
    }

    public static void runVoicemeeter(int type) {
        ensureNoError(instance.VBVMR_RunVoicemeeter(type), 0);
    }

    public static int getVoicemeeterType() {
        Pointer type = getPointer(4);
        ensureNoError(instance.VBVMR_GetVoicemeeterType(type), 0);
        return type.getInt(0L);
    }

    public static int getVoicemeeterVersion() {
        Pointer version = getPointer(4);
        ensureNoError(instance.VBVMR_GetVoicemeeterVersion(version), 0);
        return version.getInt(0L);
    }

    public static boolean areParametersDirty() {
        return ensureNoError(instance.VBVMR_IsParametersDirty(), 0, 1) == 1;
    }

    public static float getParameterFloat(String parameterName) {
        Pointer paramName = getStringPointer(parameterName);
        Pointer paramValue = getPointer(4);
        ensureNoError(instance.VBVMR_GetParameterFloat(paramName, paramValue), 0);
        return paramValue.getFloat(0L);
    }

    public static String getParameterStringA(String parameterName) {
        Pointer paramName = getStringPointer(parameterName);
        Pointer paramValue = getPointer(8);
        ensureNoError(instance.VBVMR_GetParameterStringA(paramName, paramValue), 0);
        return paramValue.getString(0L);
    }

    public static String getParameterStringW(String parameterName) {
        Pointer paramName = getStringPointer(parameterName);
        Pointer paramValue = getPointer(8);
        ensureNoError(instance.VBVMR_GetParameterStringW(paramName, paramValue), 0);
        return paramValue.getString(0L);
    }

    public static float getLevel(int type, int channel) {
        Pointer levelValue = getPointer(4);
        ensureNoError(instance.VBVMR_GetLevel(type, channel, levelValue), 0);
        return levelValue.getFloat(0L);
    }

    public static byte[] getMidiMessage(int size) {
        Pointer midiMessage = getPointer(size);
        ensureGte(instance.VBVMR_GetMidiMessage(midiMessage, size), 0);
        return midiMessage.getByteArray(0L, size);
    }

    public static void setParameterFloat(String parameterName, float value) {
        Pointer paramName = getStringPointer(parameterName);
        ensureNoError(instance.VBVMR_SetParameterFloat(paramName, value), 0);
    }

    public static void setParameterStringA(String parameterName, String value) {
        Pointer paramName = getStringPointer(parameterName);
        Pointer paramValue = getStringPointer(value);
        ensureNoError(instance.VBVMR_SetParameterStringA(paramName, paramValue), 0);
    }

    public static void setParameterStringW(String parameterName, String value) {
        Pointer paramName = getStringPointer(parameterName);
        Pointer paramValue = getStringPointer(value);
        ensureNoError(instance.VBVMR_SetParameterStringW(paramName, paramValue), 0);
    }

    public static void setParameters(String script) {
        Pointer stringPointer = getStringPointer(script);
        var val = ensureGte(instance.VBVMR_SetParameters(stringPointer), 0);
        if (val > 0)
            throw new VoicemeeterException("Script error on line " + val);
    }

    public static void setParametersW(String script) {
        Pointer stringPointer = getStringPointer(script);
        ensureNoError(instance.VBVMR_SetParametersW(stringPointer), 0);
    }

    private static int ensureGte(Integer code, Integer expected) {
        if (code < expected) {
            return ensureNoError(code);
        }
        return code;
    }

    private static int ensureNoError(Integer code, Integer... expected) {
        return switch (code) {
            case Integer i && Set.of(expected).contains(i) -> code;
            case -1 -> throw new VoicemeeterException("Unable to get the Voicemeeter client");
            case -2 -> throw new VoicemeeterException("Unable to get the Voicemeeter server");
            case -3 -> throw new VoicemeeterException("Not available");
            case -5 -> throw new VoicemeeterException("Structure mismatch");
            case Integer i && i < 0 -> throw new VoicemeeterException("Unknown voicemeter error occurred " + code);
            case null, default ->
                    throw new VoicemeeterException("Unexpected function return value. Function returned " + code);
        };
    }

    public static int getNumberOfAudioDevices(boolean areInputDevices) {
        if (areInputDevices)
            return instance.VBVMR_Input_GetDeviceNumber();
        return instance.VBVMR_Output_GetDeviceNumber();
    }

    public static DeviceDescription getAudioDeviceDescriptionA(int index, boolean isInputDevice) {
        Pointer type = getPointer(4);
        Pointer name = getPointer(4);
        Pointer hardwareId = getPointer(4);
        if (isInputDevice) {
            int val = instance.VBVMR_Input_GetDeviceDescA(index, type, name, hardwareId);
            if (val != 0)
                throw new VoicemeeterException("Unexpected function return value. Function returned " + val);
        } else {
            int val = instance.VBVMR_Output_GetDeviceDescA(index, type, name, hardwareId);
            if (val != 0)
                throw new VoicemeeterException("Unexpected function return value. Function returned " + val);
        }
        DeviceDescription desc = new DeviceDescription();
        desc.setType(type.getInt(0L));
        desc.setName(name.getString(0L));
        desc.setHardwareId(hardwareId.getString(0L));
        return desc;
    }

    public static DeviceDescription getOutputDeviceDescriptionW(int index, boolean isInputDevice) {
        Pointer type = getPointer(4);
        Pointer name = getPointer(4);
        Pointer hardwareId = getPointer(4);
        if (isInputDevice) {
            int val = instance.VBVMR_Input_GetDeviceDescW(index, type, name, hardwareId);
            if (val != 0)
                throw new VoicemeeterException("Unexpected function return value. Function returned " + val);
        } else {
            int val = instance.VBVMR_Output_GetDeviceDescW(index, type, name, hardwareId);
            if (val != 0)
                throw new VoicemeeterException("Unexpected function return value. Function returned " + val);
        }
        DeviceDescription desc = new DeviceDescription();
        desc.setType(type.getInt(0L));
        desc.setName(name.getString(0L));
        desc.setHardwareId(hardwareId.getString(0L));
        return desc;
    }

    private static class DeviceDescription {
        private int type;

        private String name;

        private String hardwareId;

        private DeviceDescription() {
        }

        public void setType(int givenType) {
            type = givenType;
        }

        public void setName(String givenName) {
            name = givenName;
        }

        public void setHardwareId(String givenId) {
            hardwareId = givenId;
        }

        public int getType() {
            return type;
        }

        public String getName() {
            return name;
        }

        public String getHardwareId() {
            return hardwareId;
        }
    }

    private static Pointer getStringPointer(String str) {
        int size = str.getBytes().length + 1;
        Memory m = new Memory(size);
        m.setString(0L, str);
        return m;
    }

    private static Pointer getPointer(int size) {
        return new Memory(size);
    }
}

