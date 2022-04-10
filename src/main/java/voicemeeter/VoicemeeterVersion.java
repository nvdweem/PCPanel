package voicemeeter;

import voicemeeter.Voicemeeter.ButtonType;
import voicemeeter.Voicemeeter.DialType;

public enum VoicemeeterVersion {
    POTATO {
        private final DialType[][] potatoStripDials = {
                { DialType.GAIN, DialType.COMP, DialType.GATE, DialType.REVERB, DialType.DELAY, DialType.FX1,
                        DialType.FX2, DialType.LIMIT },
                { DialType.GAIN, DialType.COMP, DialType.GATE, DialType.REVERB, DialType.DELAY, DialType.FX1,
                        DialType.FX2, DialType.LIMIT },
                { DialType.GAIN, DialType.COMP, DialType.GATE, DialType.REVERB, DialType.DELAY, DialType.FX1,
                        DialType.FX2, DialType.LIMIT },
                { DialType.GAIN, DialType.COMP, DialType.GATE, DialType.REVERB, DialType.DELAY, DialType.FX1,
                        DialType.FX2, DialType.LIMIT },
                { DialType.GAIN, DialType.COMP, DialType.GATE, DialType.REVERB, DialType.DELAY, DialType.FX1,
                        DialType.FX2, DialType.LIMIT }, { DialType.GAIN, DialType.EQGAIN1, DialType.EQGAIN2, DialType.EQGAIN3 },
                { DialType.GAIN, DialType.EQGAIN1, DialType.EQGAIN2, DialType.EQGAIN3 },
                { DialType.GAIN, DialType.EQGAIN1, DialType.EQGAIN2, DialType.EQGAIN3 } };

        private final DialType[][] potatoBusDials = {
                { DialType.GAIN, DialType.RETURNREVERB, DialType.RETURNDELAY, DialType.RETURNFX1, DialType.RETURNFX2 },
                { DialType.GAIN, DialType.RETURNREVERB, DialType.RETURNDELAY, DialType.RETURNFX1, DialType.RETURNFX2 },
                { DialType.GAIN, DialType.RETURNREVERB, DialType.RETURNDELAY, DialType.RETURNFX1, DialType.RETURNFX2 },
                { DialType.GAIN, DialType.RETURNREVERB, DialType.RETURNDELAY, DialType.RETURNFX1, DialType.RETURNFX2 },
                { DialType.GAIN, DialType.RETURNREVERB, DialType.RETURNDELAY, DialType.RETURNFX1, DialType.RETURNFX2 },
                { DialType.GAIN, DialType.RETURNREVERB, DialType.RETURNDELAY, DialType.RETURNFX1, DialType.RETURNFX2 },
                { DialType.GAIN, DialType.RETURNREVERB, DialType.RETURNDELAY, DialType.RETURNFX1, DialType.RETURNFX2 },
                { DialType.GAIN, DialType.RETURNREVERB, DialType.RETURNDELAY, DialType.RETURNFX1, DialType.RETURNFX2 } };

        private final ButtonType[][] potatoStripButtons = { {
                ButtonType.A1, ButtonType.A2, ButtonType.A3, ButtonType.A4, ButtonType.A5, ButtonType.B1,
                ButtonType.B2, ButtonType.B3, ButtonType.MONO, ButtonType.SOLO,
                ButtonType.MUTE }, {
                ButtonType.A1, ButtonType.A2, ButtonType.A3, ButtonType.A4, ButtonType.A5, ButtonType.B1,
                ButtonType.B2, ButtonType.B3, ButtonType.MONO, ButtonType.SOLO,
                ButtonType.MUTE }, {
                ButtonType.A1, ButtonType.A2, ButtonType.A3, ButtonType.A4, ButtonType.A5, ButtonType.B1,
                ButtonType.B2, ButtonType.B3, ButtonType.MONO, ButtonType.SOLO,
                ButtonType.MUTE }, {
                ButtonType.A1, ButtonType.A2, ButtonType.A3, ButtonType.A4, ButtonType.A5, ButtonType.B1,
                ButtonType.B2, ButtonType.B3, ButtonType.MONO, ButtonType.SOLO,
                ButtonType.MUTE }, {
                ButtonType.A1, ButtonType.A2, ButtonType.A3, ButtonType.A4, ButtonType.A5, ButtonType.B1,
                ButtonType.B2, ButtonType.B3, ButtonType.MONO, ButtonType.SOLO,
                ButtonType.MUTE }, {
                ButtonType.A1, ButtonType.A2, ButtonType.A3, ButtonType.A4, ButtonType.A5, ButtonType.B1,
                ButtonType.B2, ButtonType.B3, ButtonType.MC, ButtonType.SOLO,
                ButtonType.MUTE },
                { ButtonType.A1, ButtonType.A2, ButtonType.A3, ButtonType.A4, ButtonType.A5, ButtonType.B1,
                        ButtonType.B2, ButtonType.B3, ButtonType.SOLO, ButtonType.MUTE }, {
                ButtonType.A1, ButtonType.A2, ButtonType.A3, ButtonType.A4, ButtonType.A5, ButtonType.B1,
                ButtonType.B2, ButtonType.B3, ButtonType.MC, ButtonType.SOLO,
                ButtonType.MUTE } };

        private final ButtonType[][] potatoBusButtons = {
                { ButtonType.SEL, ButtonType.MONO, ButtonType.EQ, ButtonType.MUTE },
                { ButtonType.SEL, ButtonType.MONO, ButtonType.EQ, ButtonType.MUTE },
                { ButtonType.SEL, ButtonType.MONO, ButtonType.EQ, ButtonType.MUTE },
                { ButtonType.SEL, ButtonType.MONO, ButtonType.EQ, ButtonType.MUTE },
                { ButtonType.SEL, ButtonType.MONO, ButtonType.EQ, ButtonType.MUTE },
                { ButtonType.SEL, ButtonType.MONO, ButtonType.EQ, ButtonType.MUTE },
                { ButtonType.SEL, ButtonType.MONO, ButtonType.EQ, ButtonType.MUTE },
                { ButtonType.SEL, ButtonType.MONO, ButtonType.EQ, ButtonType.MUTE } };

        @Override
        public DialType[][] getStripDials() {
            return potatoStripDials;
        }

        @Override
        public DialType[][] getBusDials() {
            return potatoBusDials;
        }

        @Override
        public ButtonType[][] getStripButtons() {
            return potatoStripButtons;
        }

        @Override
        public ButtonType[][] getBusButtons() {
            return potatoBusButtons;
        }
    },
    BANANA {
        private final DialType[][] bananaStripDials = {
                { DialType.GAIN, DialType.COMP, DialType.GATE, DialType.LIMIT },
                { DialType.GAIN, DialType.COMP, DialType.GATE, DialType.LIMIT },
                { DialType.GAIN, DialType.COMP, DialType.GATE, DialType.LIMIT },
                { DialType.GAIN, DialType.EQGAIN1, DialType.EQGAIN2, DialType.EQGAIN3 },
                { DialType.GAIN, DialType.EQGAIN1, DialType.EQGAIN2, DialType.EQGAIN3 } };

        private final DialType[][] bananaBusDials = { { DialType.GAIN }, { DialType.GAIN }, { DialType.GAIN },
                { DialType.GAIN }, { DialType.GAIN } };

        private final ButtonType[][] bananaStripButtons = {
                { ButtonType.A1, ButtonType.A2, ButtonType.A3, ButtonType.B1, ButtonType.B2, ButtonType.MONO,
                        ButtonType.SOLO, ButtonType.MUTE },
                { ButtonType.A1, ButtonType.A2, ButtonType.A3, ButtonType.B1, ButtonType.B2, ButtonType.MONO,
                        ButtonType.SOLO, ButtonType.MUTE },
                { ButtonType.A1, ButtonType.A2, ButtonType.A3, ButtonType.B1, ButtonType.B2, ButtonType.MONO,
                        ButtonType.SOLO, ButtonType.MUTE },
                { ButtonType.A1, ButtonType.A2, ButtonType.A3, ButtonType.B1, ButtonType.B2, ButtonType.MC,
                        ButtonType.SOLO, ButtonType.MUTE },
                { ButtonType.A1, ButtonType.A2, ButtonType.A3, ButtonType.B1, ButtonType.B2, ButtonType.SOLO,
                        ButtonType.MUTE } };

        private final ButtonType[][] bananaBusButtons = { { ButtonType.MONO, ButtonType.EQ, ButtonType.MUTE },
                { ButtonType.MONO, ButtonType.EQ, ButtonType.MUTE }, { ButtonType.MONO, ButtonType.EQ, ButtonType.MUTE },
                { ButtonType.MONO, ButtonType.EQ, ButtonType.MUTE }, { ButtonType.MONO, ButtonType.EQ, ButtonType.MUTE } };

        @Override
        public DialType[][] getStripDials() {
            return bananaStripDials;
        }

        @Override
        public DialType[][] getBusDials() {
            return bananaBusDials;
        }

        @Override
        public ButtonType[][] getStripButtons() {
            return bananaStripButtons;
        }

        @Override
        public ButtonType[][] getBusButtons() {
            return bananaBusButtons;
        }
    },
    VOICEMEETER {
        private final DialType[][] voicemeeterStripDials = { { DialType.GAIN, DialType.AUDIBILITY },
                { DialType.GAIN, DialType.AUDIBILITY },
                { DialType.GAIN, DialType.EQGAIN1, DialType.EQGAIN2, DialType.EQGAIN3 } };

        private final DialType[][] voicemeeterBusDials = { { DialType.GAIN }, { DialType.GAIN } };

        private final ButtonType[][] voicemeeterStripButtons = {
                { ButtonType.A1, ButtonType.B1, ButtonType.MONO, ButtonType.SOLO, ButtonType.MUTE },
                { ButtonType.A1, ButtonType.B1, ButtonType.MONO, ButtonType.SOLO, ButtonType.MUTE },
                { ButtonType.A1, ButtonType.B1, ButtonType.MC, ButtonType.SOLO, ButtonType.MUTE } };

        private final ButtonType[][] voicemeeterBusButtons = {
                { ButtonType.MIXA, ButtonType.REPEAT, ButtonType.COMPOSITE, ButtonType.MONO, ButtonType.MUTE },
                { ButtonType.MIXA, ButtonType.REPEAT, ButtonType.COMPOSITE, ButtonType.MONO, ButtonType.MUTE } };

        @Override
        public DialType[][] getStripDials() {
            return voicemeeterStripDials;
        }

        @Override
        public DialType[][] getBusDials() {
            return voicemeeterBusDials;
        }

        @Override
        public ButtonType[][] getStripButtons() {
            return voicemeeterStripButtons;
        }

        @Override
        public ButtonType[][] getBusButtons() {
            return voicemeeterBusButtons;
        }
    };

    public DialType[][] getStripDials() {
        return null;
    }

    public DialType[][] getBusDials() {
        return null;
    }

    public ButtonType[][] getStripButtons() {
        return null;
    }

    public ButtonType[][] getBusButtons() {
        return null;
    }
}

