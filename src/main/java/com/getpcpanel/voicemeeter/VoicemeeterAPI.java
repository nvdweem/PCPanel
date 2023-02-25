package com.getpcpanel.voicemeeter;

import java.io.File;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.getpcpanel.profile.SaveService;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

@Log4j2
@Service
@RequiredArgsConstructor
public final class VoicemeeterAPI {
    private final SaveService saveService;
    private VoicemeeterInstance instance;
    public final String DEFAULT_VM_WINDOWS_64BIT_PATH = "VoicemeeterRemote64.dll";
    public final String DEFAULT_VM_WINDOWS_32BIT_PATH = "VoicemeeterRemote.dll";

    public void init() {
        init(true);
    }

    public void init(boolean is64bit) {
        var dllPath = new File(saveService.get().getVoicemeeterPath(), is64bit ? DEFAULT_VM_WINDOWS_64BIT_PATH : DEFAULT_VM_WINDOWS_32BIT_PATH);
        try {
            System.load(dllPath.getAbsolutePath());
            instance = Native.load("VoicemeeterRemote" + (is64bit ? "64" : ""), VoicemeeterInstance.class);
        } catch (Throwable t) {
            log.error("Unable to load VoiceMeeter");
        }
    }

    public void init(VoicemeeterInstance voicemeeterInstance) {
        instance = voicemeeterInstance;
    }

    public void login() throws VoicemeeterException {
        ensureNoError(instance.VBVMR_Login(), 0, 1);
    }

    public void logout() throws VoicemeeterException {
        ensureNoError(instance.VBVMR_Logout(), 0);
    }

    public void runVoicemeeter(int type) throws VoicemeeterException {
        ensureNoError(instance.VBVMR_RunVoicemeeter(type), 0);
    }

    public int getVoicemeeterType() throws VoicemeeterException {
        var type = getPointer(4);
        ensureNoError(instance.VBVMR_GetVoicemeeterType(type), 0);
        return type.getInt(0L);
    }

    public int getVoicemeeterVersion() throws VoicemeeterException {
        var version = getPointer(4);
        ensureNoError(instance.VBVMR_GetVoicemeeterVersion(version), 0);
        return version.getInt(0L);
    }

    public boolean areParametersDirty() throws VoicemeeterException {
        return ensureNoError(instance.VBVMR_IsParametersDirty(), 0, 1) == 1;
    }

    public float getParameterFloat(String parameterName) throws VoicemeeterException {
        var paramName = getStringPointer(parameterName);
        var paramValue = getPointer(4);
        ensureNoError(instance.VBVMR_GetParameterFloat(paramName, paramValue), 0);
        return paramValue.getFloat(0L);
    }

    public String getParameterStringA(String parameterName) throws VoicemeeterException {
        var paramName = getStringPointer(parameterName);
        var paramValue = getPointer(8);
        ensureNoError(instance.VBVMR_GetParameterStringA(paramName, paramValue), 0);
        return paramValue.getString(0L);
    }

    public String getParameterStringW(String parameterName) throws VoicemeeterException {
        var paramName = getStringPointer(parameterName);
        var paramValue = getPointer(8);
        ensureNoError(instance.VBVMR_GetParameterStringW(paramName, paramValue), 0);
        return paramValue.getString(0L);
    }

    public float getLevel(int type, int channel) throws VoicemeeterException {
        var levelValue = getPointer(4);
        ensureNoError(instance.VBVMR_GetLevel(type, channel, levelValue), 0);
        return levelValue.getFloat(0L);
    }

    public byte[] getMidiMessage(int size) throws VoicemeeterException {
        var midiMessage = getPointer(size);
        ensureGte(instance.VBVMR_GetMidiMessage(midiMessage, size), 0);
        return midiMessage.getByteArray(0L, size);
    }

    public void setParameterFloat(String parameterName, float value) throws VoicemeeterException {
        var paramName = getStringPointer(parameterName);
        ensureNoError(instance.VBVMR_SetParameterFloat(paramName, value), 0);
    }

    public void setParameterStringA(String parameterName, String value) throws VoicemeeterException {
        var paramName = getStringPointer(parameterName);
        var paramValue = getStringPointer(value);
        ensureNoError(instance.VBVMR_SetParameterStringA(paramName, paramValue), 0);
    }

    public void setParameterStringW(String parameterName, String value) throws VoicemeeterException {
        var paramName = getStringPointer(parameterName);
        var paramValue = getStringPointer(value);
        ensureNoError(instance.VBVMR_SetParameterStringW(paramName, paramValue), 0);
    }

    public void setParameters(String script) throws VoicemeeterException {
        var stringPointer = getStringPointer(script);
        var val = ensureGte(instance.VBVMR_SetParameters(stringPointer), 0);
        if (val > 0)
            throw new VoicemeeterException("Script error on line " + val);
    }

    public void setParametersW(String script) throws VoicemeeterException {
        var stringPointer = getStringPointer(script);
        ensureNoError(instance.VBVMR_SetParametersW(stringPointer), 0);
    }

    private int ensureGte(Integer code, Integer expected) throws VoicemeeterException {
        if (code < expected) {
            return ensureNoError(code);
        }
        return code;
    }

    private int ensureNoError(Integer code, Integer... expected) throws VoicemeeterException {
        if (code == null) {
            throw new VoicemeeterException("Unexpected function return value. Function returned null");
        }
        return switch (code) {
            case -1 -> throw new VoicemeeterException("Unable to get the Voicemeeter client", true);
            case -2 -> throw new VoicemeeterException("Unable to get the Voicemeeter server", true);
            case -3 -> throw new VoicemeeterException("Not available");
            case -5 -> throw new VoicemeeterException("Structure mismatch");
            default -> {
                if (Set.of(expected).contains(code)) {
                    yield code;
                }
                if (code < 0) {
                    throw new VoicemeeterException("Unknown voicemeter error occurred " + code);
                }
                throw new VoicemeeterException("Unexpected function return value. Function returned " + code);
            }
        };
    }

    public int getNumberOfAudioDevices(boolean areInputDevices) {
        if (areInputDevices)
            return instance.VBVMR_Input_GetDeviceNumber();
        return instance.VBVMR_Output_GetDeviceNumber();
    }

    public DeviceDescription getAudioDeviceDescriptionA(int index, boolean isInputDevice) throws VoicemeeterException {
        var type = getPointer(4);
        var name = getPointer(4);
        var hardwareId = getPointer(4);
        if (isInputDevice) {
            var val = instance.VBVMR_Input_GetDeviceDescA(index, type, name, hardwareId);
            if (val != 0)
                throw new VoicemeeterException("Unexpected function return value. Function returned " + val);
        } else {
            var val = instance.VBVMR_Output_GetDeviceDescA(index, type, name, hardwareId);
            if (val != 0)
                throw new VoicemeeterException("Unexpected function return value. Function returned " + val);
        }
        var desc = new DeviceDescription();
        desc.setType(type.getInt(0L));
        desc.setName(name.getString(0L));
        desc.setHardwareId(hardwareId.getString(0L));
        return desc;
    }

    public DeviceDescription getOutputDeviceDescriptionW(int index, boolean isInputDevice) throws VoicemeeterException {
        var type = getPointer(4);
        var name = getPointer(4);
        var hardwareId = getPointer(4);
        if (isInputDevice) {
            var val = instance.VBVMR_Input_GetDeviceDescW(index, type, name, hardwareId);
            if (val != 0)
                throw new VoicemeeterException("Unexpected function return value. Function returned " + val);
        } else {
            var val = instance.VBVMR_Output_GetDeviceDescW(index, type, name, hardwareId);
            if (val != 0)
                throw new VoicemeeterException("Unexpected function return value. Function returned " + val);
        }
        var desc = new DeviceDescription();
        desc.setType(type.getInt(0L));
        desc.setName(name.getString(0L));
        desc.setHardwareId(hardwareId.getString(0L));
        return desc;
    }

    private static final class DeviceDescription {
        private int type;
        private String name;
        private String hardwareId;

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

    private Pointer getStringPointer(String str) {
        var size = str.getBytes().length + 1;
        var m = new Memory(size);
        m.setString(0L, str);
        return m;
    }

    private Pointer getPointer(int size) {
        return new Memory(size);
    }
}

