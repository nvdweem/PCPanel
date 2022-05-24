package voicemeeter;

import java.util.Arrays;
import java.util.List;

import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import com.sun.jna.win32.StdCallLibrary;

@SuppressWarnings("unused")
public interface VoicemeeterInstance extends StdCallLibrary {
    int VBVMR_RESULT_OK = 0;

    int VBVMR_DEVTYPE_MME = 1;

    int VBVMR_DEVTYPE_WDM = 3;

    int VBVMR_DEVTYPE_KS = 4;

    int VBVMR_DEVTYPE_ASIO = 5;

    int VBVMR_CBCOMMAND_STARTING = 1;

    int VBVMR_CBCOMMAND_ENDING = 2;

    int VBVMR_CBCOMMAND_CHANGE = 3;

    int VBVMR_CBCOMMAND_BUFFER_IN = 10;

    int VBVMR_CBCOMMAND_BUFFER_OUT = 11;

    int VBVMR_CBCOMMAND_BUFFER_MAIN = 20;

    int VBVMR_AUDIOCALLBACK_IN = 1;

    int VBVMR_AUDIOCALLBACK_OUT = 2;

    int VBVMR_AUDIOCALLBACK_MAIN = 4;

    class tagVBVMR_AUDIOINFO extends Structure {
        public int samplerate;

        public int nbSamplePerFrame;

        @Override
        protected List<String> getFieldOrder() {
            return Arrays.asList("samplerate", "nbSamplePerFrame");
        }
    }

    class tagVBVMR_AUDIOBUFFER extends Structure {
        public int audiobuffer_sr;

        public int audiobuffer_nbs;

        public int audiobuffer_nbi;

        public int audiobuffer_nbo;

        public Pointer audiobuffer_r;

        public Pointer audiobuffer_w;

        @Override
        protected List<String> getFieldOrder() {
            return Arrays.asList("audiobuffer_sr", "audiobuffer_nbs", "audiobuffer_nbi", "audiobuffer_nbo", "audiobuffer_r", "audiobuffer_w");
        }
    }

    tagVBVMR_AUDIOINFO VBVMR_T_AUDIOINFO = new tagVBVMR_AUDIOINFO();

    Pointer VBVMR_PT_AUDIOINFO = new tagVBVMR_AUDIOINFO().getPointer();

    Pointer VBVBMR_LPT_AUDIOINFO = new tagVBVMR_AUDIOINFO().getPointer();

    tagVBVMR_AUDIOBUFFER VBVMR_T_AUDIOBUFFER = new tagVBVMR_AUDIOBUFFER();

    Pointer VBVMR_PT_AUDIOBUFFER = new tagVBVMR_AUDIOBUFFER().getPointer();

    Pointer VBVMR_LPT_AUDIOBUFFER = new tagVBVMR_AUDIOBUFFER().getPointer();

    class tagVBVMR_INTERFACE extends Structure {
        public T_VBVMR_Login VBVMR_Login;

        public T_VBVMR_Logout VBVMR_Logout;

        public T_VBVMR_RunVoicemeeter VBVMR_RunVoicemeeter;

        public T_VBVMR_GetVoicemeeterType VBVMR_GetVoicemeeterType;

        public T_VBVMR_GetVoicemeeterVersion VBVMR_GetVoicemeeterVersion;

        public T_VBVMR_IsParametersDirty VBVMR_IsParametersDirty;

        public T_VBVMR_GetParameterFloat VBVMR_GetParameterFloat;

        public T_VBVMR_GetParameterStringA VBVMR_GetParameterStringA;

        public T_VBVMR_GetParameterStringW VBVMR_GetParameterStringW;

        public T_VBVMR_GetLevel VBVMR_GetLevel;

        public T_VBVMR_GetMidiMessage VBVMR_GetMidiMessage;

        public T_VBVMR_SetParameterFloat VBVMR_SetParameterFloat;

        public T_VBVMR_SetParameters VBVMR_SetParameters;

        public T_VBVMR_SetParametersW VBVMR_SetParametersW;

        public T_VBVMR_SetParameterStringA VBVMR_SetParameterStringA;

        public T_VBVMR_SetParameterStringW VBVMR_SetParameterStringW;

        public T_VBVMR_Output_GetDeviceNumber VBVMR_Output_GetDeviceNumber;

        public T_VBVMR_Output_GetDeviceDescA VBVMR_Output_GetDeviceDescA;

        public T_VBVMR_Output_GetDeviceDescW VBVMR_Output_GetDeviceDescW;

        public T_VBVMR_Input_GetDeviceNumber VBVMR_Input_GetDeviceNumber;

        public T_VBVMR_Input_GetDeviceDescA VBVMR_Input_GetDeviceDescA;

        public T_VBVMR_Input_GetDeviceDescW VBVMR_Input_GetDeviceDescW;

        public T_VBVMR_AudioCallbackRegister VBVMR_AudioCallbackRegister;

        public T_VBVMR_AudioCallbackStart VBVMR_AudioCallbackStart;

        public T_VBVMR_AudioCallbackStop VBVMR_AudioCallbackStop;

        public T_VBVMR_AudioCallbackUnregister VBVMR_AudioCallbackUnregister;

        @Override
        protected List<String> getFieldOrder() {
            return Arrays.asList("VBVMR_Login",
                    "VBVMR_Logout",
                    "VBVMR_RunVoicemeeter",
                    "VBVMR_GetVoicemeeterType",
                    "VBVMR_GetVoicemeeterVersion",
                    "VBVMR_IsParametersDirty",
                    "VBVMR_GetParameterFloat",
                    "VBVMR_GetParameterStringA",
                    "VBVMR_GetParameterStringW",
                    "VBVMR_GetLevel",
                    "VBVMR_GetMidiMessage",
                    "VBVMR_SetParameterFloat",
                    "VBVMR_SetParameters",
                    "VBVMR_SetParametersW",
                    "VBVMR_SetParameterStringA",
                    "VBVMR_SetParameterStringW",
                    "VBVMR_Output_GetDeviceNumber",
                    "VBVMR_Output_GetDeviceDescA",
                    "VBVMR_Output_GetDeviceDescW",
                    "VBVMR_Input_GetDeviceNumber",
                    "VBVMR_Input_GetDeviceDescA",
                    "VBVMR_Input_GetDeviceDescW",
                    "VBVMR_AudioCallbackRegister",
                    "VBVMR_AudioCallbackStart",
                    "VBVMR_AudioCallbackStop",
                    "VBVMR_AudioCallbackUnregister");
        }
    }

    tagVBVMR_INTERFACE T_VBVMR_INTERFACE = new tagVBVMR_INTERFACE();

    Pointer PT_VBVMR_INTERFACE = new tagVBVMR_INTERFACE().getPointer();

    Pointer LPT_VBVMR_INTERFACE = new tagVBVMR_INTERFACE().getPointer();

    int VBVMR_Login();

    int VBVMR_Logout();

    int VBVMR_RunVoicemeeter(int paramInt);

    int VBVMR_GetVoicemeeterType(Pointer paramPointer);

    int VBVMR_GetVoicemeeterVersion(Pointer paramPointer);

    int VBVMR_IsParametersDirty();

    int VBVMR_GetParameterFloat(Pointer paramPointer1, Pointer paramPointer2);

    int VBVMR_GetParameterStringA(Pointer paramPointer1, Pointer paramPointer2);

    int VBVMR_GetParameterStringW(Pointer paramPointer1, Pointer paramPointer2);

    int VBVMR_GetLevel(int paramInt1, int paramInt2, Pointer paramPointer);

    int VBVMR_GetMidiMessage(Pointer paramPointer, int paramInt);

    int VBVMR_SetParameterFloat(Pointer paramPointer, float paramFloat);

    int VBVMR_SetParameterStringA(Pointer paramPointer1, Pointer paramPointer2);

    int VBVMR_SetParameterStringW(Pointer paramPointer1, Pointer paramPointer2);

    int VBVMR_SetParameters(Pointer paramPointer);

    int VBVMR_SetParametersW(Pointer paramPointer);

    int VBVMR_Output_GetDeviceNumber();

    int VBVMR_Output_GetDeviceDescA(int paramInt, Pointer paramPointer1, Pointer paramPointer2, Pointer paramPointer3);

    int VBVMR_Output_GetDeviceDescW(int paramInt, Pointer paramPointer1, Pointer paramPointer2, Pointer paramPointer3);

    int VBVMR_Input_GetDeviceNumber();

    int VBVMR_Input_GetDeviceDescA(int paramInt, Pointer paramPointer1, Pointer paramPointer2, Pointer paramPointer3);

    int VBVMR_Input_GetDeviceDescW(int paramInt, Pointer paramPointer1, Pointer paramPointer2, Pointer paramPointer3);

    int VBVMR_AudioCallbackRegister(int paramInt, T_VBVMR_VBAUDIOCALLBACK paramT_VBVMR_VBAUDIOCALLBACK, Pointer paramPointer, char[] paramArrayOfchar);

    int VBVMR_AudioCallbackStart();

    int VBVMR_AudioCallbackStop();

    int VBVMR_AudioCallbackUnregister();

    interface T_VBVMR_AudioCallbackRegister extends StdCallCallback {
        boolean callback(int param1Int, T_VBVMR_VBAUDIOCALLBACK param1T_VBVMR_VBAUDIOCALLBACK, Pointer param1Pointer, char[] param1ArrayOfchar);
    }

    interface T_VBVMR_AudioCallbackStart extends StdCallCallback {
        boolean callback();
    }

    interface T_VBVMR_AudioCallbackStop extends StdCallCallback {
        boolean callback();
    }

    interface T_VBVMR_AudioCallbackUnregister extends StdCallCallback {
        boolean callback();
    }

    interface T_VBVMR_GetLevel extends StdCallCallback {
        boolean callback(int param1Int1, int param1Int2, Pointer param1Pointer);
    }

    interface T_VBVMR_GetMidiMessage extends StdCallCallback {
        boolean callback(Pointer param1Pointer, int param1Int);
    }

    interface T_VBVMR_GetParameterFloat extends StdCallCallback {
        boolean callback(Pointer param1Pointer1, Pointer param1Pointer2);
    }

    interface T_VBVMR_GetParameterStringA extends StdCallCallback {
        boolean callback(Pointer param1Pointer1, Pointer param1Pointer2);
    }

    interface T_VBVMR_GetParameterStringW extends StdCallCallback {
        boolean callback(Pointer param1Pointer1, Pointer param1Pointer2);
    }

    interface T_VBVMR_GetVoicemeeterType extends StdCallCallback {
        boolean callback(Pointer param1Pointer);
    }

    interface T_VBVMR_GetVoicemeeterVersion extends StdCallCallback {
        boolean callback(Pointer param1Pointer);
    }

    interface T_VBVMR_Input_GetDeviceDescA extends StdCallCallback {
        boolean callback(int param1Int, Pointer param1Pointer1, Pointer param1Pointer2, Pointer param1Pointer3);
    }

    interface T_VBVMR_Input_GetDeviceDescW extends StdCallCallback {
        boolean callback(int param1Int, Pointer param1Pointer1, Pointer param1Pointer2, Pointer param1Pointer3);
    }

    interface T_VBVMR_Input_GetDeviceNumber extends StdCallCallback {
        boolean callback();
    }

    interface T_VBVMR_IsParametersDirty extends StdCallCallback {
        boolean callback();
    }

    interface T_VBVMR_Login extends StdCallCallback {
        boolean callback();
    }

    interface T_VBVMR_Logout extends StdCallCallback {
        boolean callback();
    }

    interface T_VBVMR_Output_GetDeviceDescA extends StdCallCallback {
        boolean callback(int param1Int, Pointer param1Pointer1, Pointer param1Pointer2, Pointer param1Pointer3);
    }

    interface T_VBVMR_Output_GetDeviceDescW extends StdCallCallback {
        boolean callback(int param1Int, Pointer param1Pointer1, Pointer param1Pointer2, Pointer param1Pointer3);
    }

    interface T_VBVMR_Output_GetDeviceNumber extends StdCallCallback {
        boolean callback();
    }

    interface T_VBVMR_RunVoicemeeter extends StdCallCallback {
        boolean callback(int param1Int);
    }

    interface T_VBVMR_SetParameterFloat extends StdCallCallback {
        boolean callback(Pointer param1Pointer, int param1Int);
    }

    interface T_VBVMR_SetParameterStringA extends StdCallCallback {
        boolean callback(Pointer param1Pointer1, Pointer param1Pointer2);
    }

    interface T_VBVMR_SetParameterStringW extends StdCallCallback {
        boolean callback(Pointer param1Pointer1, Pointer param1Pointer2);
    }

    interface T_VBVMR_SetParameters extends StdCallCallback {
        boolean callback(Pointer param1Pointer);
    }

    interface T_VBVMR_SetParametersW extends StdCallCallback {
        boolean callback(Pointer param1Pointer);
    }

    interface T_VBVMR_VBAUDIOCALLBACK extends StdCallCallback {
        boolean callback(Pointer param1Pointer1, int param1Int1, Pointer param1Pointer2, int param1Int2);
    }
}
