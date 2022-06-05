package com.getpcpanel.voicemeeter;

import java.util.Arrays;
import java.util.List;

import org.springframework.stereotype.Service;

import com.getpcpanel.profile.SaveService;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

@Log4j2
@Service
@RequiredArgsConstructor
public final class Voicemeeter {
    private final SaveService save;
    private final VoicemeeterAPI api;
    private int version = -1;
    private volatile boolean hasFinishedConnection;
    private volatile boolean hasLoggedIn;
    private final VoicemeeterVersion[] versions = { VoicemeeterVersion.VOICEMEETER, VoicemeeterVersion.BANANA, VoicemeeterVersion.POTATO };

    public enum ControlType {
        STRIP("Input"),
        BUS("Output");

        private final String dn;

        ControlType(String dn) {
            this.dn = dn;
        }

        public String toString() {
            return dn;
        }
    }

    public enum DialControlMode {
        NEG_12_TO_12("-12 to 12"),
        ZERO_TO_10("0 to 12"),
        NEG_40_TO_12("-40 to 12"),
        NEG_INF_TO_12("-Inf to 12"),
        NEG_INF_TO_ZERO("-Inf to 0");

        private final String dn;

        DialControlMode(String dn) {
            this.dn = dn;
        }

        public String toString() {
            return dn;
        }
    }

    public enum ButtonControlMode {
        ENABLE("Enable"),
        DISABLE("Disable"),
        TOGGLE("Toggle");

        private final String dn;

        ButtonControlMode(String dn) {
            this.dn = dn;
        }

        public String toString() {
            return dn;
        }
    }

    public enum ButtonType {
        MONO("Mono", "mono"),
        MUTE("Mute", "Mute"),
        SOLO("Solo", "solo"),
        MC("MC", "M.C"),
        EQ("EQ.on", "EQ"),
        A1("A1", "A1"),
        A2("A2", "A2"),
        A3("A3", "A3"),
        A4("A4", "A4"),
        A5("A5", "A5"),
        B1("B1", "B1"),
        B2("B2", "B2"),
        B3("B3", "B3"),
        SEL("Sel", "SEL"),
        MIXA("mode.Amix", "MIXA"),
        MIXB("mode.Bmix", "MIXB"),
        REPEAT("mode.Repeat", "Repeat"),
        COMPOSITE("mode.Composite", "Composite");

        private final String param;

        private final String dn;

        ButtonType(String param, String dn) {
            this.param = param;
            this.dn = dn;
        }

        public String toString() {
            return dn;
        }

        public String getParameterName() {
            return param;
        }
    }

    public enum DialType {
        GAIN("Gain", "Gain", DialControlMode.NEG_INF_TO_12),
        AUDIBILITY("Audibility", "Audibility", DialControlMode.ZERO_TO_10),
        COMP("Comp", "Comp", DialControlMode.ZERO_TO_10),
        GATE("Gate", "Gate", DialControlMode.ZERO_TO_10),
        LIMIT("Limit", "Limit", DialControlMode.NEG_40_TO_12),
        EQGAIN1("EQGain1", "EQ Gain 1", DialControlMode.NEG_12_TO_12),
        EQGAIN2("EQGain2", "EQ Gain 2", DialControlMode.NEG_12_TO_12),
        EQGAIN3("EQGain3", "EQ Gain 3", DialControlMode.NEG_12_TO_12),
        REVERB("Reverb", "Reverb", DialControlMode.ZERO_TO_10),
        DELAY("Delay", "Delay", DialControlMode.ZERO_TO_10),
        FX1("Fx1", "FX 1", DialControlMode.ZERO_TO_10),
        FX2("Fx2", "FX 2", DialControlMode.ZERO_TO_10),
        RETURNREVERB("ReturnReverb", "Return Reverb", DialControlMode.ZERO_TO_10),
        RETURNDELAY("ReturnDelay", "Return Delay", DialControlMode.ZERO_TO_10),
        RETURNFX1("ReturnFx1", "Return FX 1", DialControlMode.ZERO_TO_10),
        RETURNFX2("ReturnFx2", "Return FX 2", DialControlMode.ZERO_TO_10);

        private final String param;

        private final String dn;

        private final DialControlMode dcm;

        DialType(String param, String dn, DialControlMode dcm) {
            this.param = param;
            this.dn = dn;
            this.dcm = dcm;
        }

        public String toString() {
            return dn;
        }

        public DialControlMode getDialControlMode() {
            return dcm;
        }

        public String getParameterName() {
            return param;
        }
    }

    public boolean login() {
        if (!save.get().isVoicemeeterEnabled())
            return false;
        if (hasFinishedConnection)
            return true;
        try {
            if (!hasLoggedIn) {
                api.init(true);
                api.login();
                hasLoggedIn = true;
            }
            api.areParametersDirty();
            version = api.getVoicemeeterType();
            hasFinishedConnection = true;
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public int getNum(ControlType ct) {
        if (ct == ControlType.STRIP)
            return getNumStrips();
        if (ct == ControlType.BUS)
            return getNumBuses();
        throw new IllegalArgumentException("invalid control type");
    }

    public int getNumStrips() {
        if (version < 1 || version > 3)
            return 0;
        return versions[version - 1].getStripDials().length;
    }

    public int getNumBuses() {
        if (version < 1 || version > 3)
            return 0;
        return versions[version - 1].getBusDials().length;
    }

    public VoicemeeterVersion getVersion() {
        if (version < 1 || version > 3)
            return null;
        return versions[version - 1];
    }

    public List<ButtonType> getButtonTypes(ControlType ct, int index) {
        if (ct == ControlType.STRIP) {
            if (index >= getNumStrips())
                index = 0;
            return Arrays.asList(getVersion().getStripButtons()[index]);
        }
        if (ct == ControlType.BUS) {
            if (index >= getNumBuses())
                index = 0;
            return Arrays.asList(getVersion().getBusButtons()[index]);
        }
        throw new IllegalArgumentException("invalid control type");
    }

    public List<DialType> getDialTypes(ControlType ct, int index) {
        if (ct == ControlType.STRIP) {
            if (index >= getNumStrips())
                index = 0;
            return Arrays.asList(getVersion().getStripDials()[index]);
        }
        if (ct == ControlType.BUS) {
            if (index >= getNumBuses())
                index = 0;
            return Arrays.asList(getVersion().getBusDials()[index]);
        }
        throw new IllegalArgumentException("invalid control type");
    }

    public void controlButton(ControlType ct, int index, ButtonType bt) {
        controlButton(makeParameterString(ct, index, bt.getParameterName()), ButtonControlMode.TOGGLE);
    }

    public void controlButton(String fullParam, ButtonControlMode bt) {
        if (bt == ButtonControlMode.TOGGLE) {
            api.areParametersDirty();
            var status = api.getParameterFloat(fullParam) == 1.0F;
            api.setParameterFloat(fullParam, status ? 0.0F : 1.0F);
            api.areParametersDirty();
        } else if (bt == ButtonControlMode.ENABLE) {
            api.setParameterFloat(fullParam, 1.0F);
        } else if (bt == ButtonControlMode.DISABLE) {
            api.setParameterFloat(fullParam, 0.0F);
        }
    }

    public void controlLevel(ControlType ct, int index, DialType dt, int level) {
        controlLevel(makeParameterString(ct, index, dt.getParameterName()), dt.getDialControlMode(), level);
    }

    public void controlLevel(String fullParam, DialControlMode ct, int level) {
        api.setParameterFloat(fullParam, convertLevel(ct, level));
    }

    public String makeParameterString(ControlType ct, int index, String parameter) {
        return ct.name() + "[" + index + "]." + parameter;
    }

    private float convertLevel(DialControlMode ct, int level) {
        if (ct == DialControlMode.NEG_12_TO_12)
            return map(level, 0.0F, 100.0F, -12.0F, 12.0F);
        if (ct == DialControlMode.ZERO_TO_10)
            return map(level, 0.0F, 100.0F, 0.0F, 10.0F);
        if (ct == DialControlMode.NEG_40_TO_12)
            return map(level, 0.0F, 100.0F, -40.0F, 12.0F);
        if (ct == DialControlMode.NEG_INF_TO_12)
            return (level == 0) ? Float.NEGATIVE_INFINITY : map(level, 0.0F, 100.0F, -60.0F, 12.0F);
        if (ct == DialControlMode.NEG_INF_TO_ZERO)
            return (level == 0) ? Float.NEGATIVE_INFINITY : map(level, 0.0F, 100.0F, -60.0F, 0.0F);
        throw new IllegalArgumentException("Invalid conversiontype in voicemeeter");
    }

    private float map(float x, float in_min, float in_max, float out_min, float out_max) {
        return (x - in_min) * (out_max - out_min) / (in_max - in_min) + out_min;
    }
}

