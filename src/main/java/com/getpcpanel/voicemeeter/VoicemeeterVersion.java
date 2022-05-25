package com.getpcpanel.voicemeeter;

public enum VoicemeeterVersion {
    POTATO {
        private final Voicemeeter.DialType[][] potatoStripDials = {
                { Voicemeeter.DialType.GAIN, Voicemeeter.DialType.COMP, Voicemeeter.DialType.GATE, Voicemeeter.DialType.REVERB, Voicemeeter.DialType.DELAY, Voicemeeter.DialType.FX1,
                        Voicemeeter.DialType.FX2, Voicemeeter.DialType.LIMIT },
                { Voicemeeter.DialType.GAIN, Voicemeeter.DialType.COMP, Voicemeeter.DialType.GATE, Voicemeeter.DialType.REVERB, Voicemeeter.DialType.DELAY, Voicemeeter.DialType.FX1,
                        Voicemeeter.DialType.FX2, Voicemeeter.DialType.LIMIT },
                { Voicemeeter.DialType.GAIN, Voicemeeter.DialType.COMP, Voicemeeter.DialType.GATE, Voicemeeter.DialType.REVERB, Voicemeeter.DialType.DELAY, Voicemeeter.DialType.FX1,
                        Voicemeeter.DialType.FX2, Voicemeeter.DialType.LIMIT },
                { Voicemeeter.DialType.GAIN, Voicemeeter.DialType.COMP, Voicemeeter.DialType.GATE, Voicemeeter.DialType.REVERB, Voicemeeter.DialType.DELAY, Voicemeeter.DialType.FX1,
                        Voicemeeter.DialType.FX2, Voicemeeter.DialType.LIMIT },
                { Voicemeeter.DialType.GAIN, Voicemeeter.DialType.COMP, Voicemeeter.DialType.GATE, Voicemeeter.DialType.REVERB, Voicemeeter.DialType.DELAY, Voicemeeter.DialType.FX1,
                        Voicemeeter.DialType.FX2, Voicemeeter.DialType.LIMIT }, { Voicemeeter.DialType.GAIN, Voicemeeter.DialType.EQGAIN1, Voicemeeter.DialType.EQGAIN2, Voicemeeter.DialType.EQGAIN3 },
                { Voicemeeter.DialType.GAIN, Voicemeeter.DialType.EQGAIN1, Voicemeeter.DialType.EQGAIN2, Voicemeeter.DialType.EQGAIN3 },
                { Voicemeeter.DialType.GAIN, Voicemeeter.DialType.EQGAIN1, Voicemeeter.DialType.EQGAIN2, Voicemeeter.DialType.EQGAIN3 } };

        private final Voicemeeter.DialType[][] potatoBusDials = {
                { Voicemeeter.DialType.GAIN, Voicemeeter.DialType.RETURNREVERB, Voicemeeter.DialType.RETURNDELAY, Voicemeeter.DialType.RETURNFX1, Voicemeeter.DialType.RETURNFX2 },
                { Voicemeeter.DialType.GAIN, Voicemeeter.DialType.RETURNREVERB, Voicemeeter.DialType.RETURNDELAY, Voicemeeter.DialType.RETURNFX1, Voicemeeter.DialType.RETURNFX2 },
                { Voicemeeter.DialType.GAIN, Voicemeeter.DialType.RETURNREVERB, Voicemeeter.DialType.RETURNDELAY, Voicemeeter.DialType.RETURNFX1, Voicemeeter.DialType.RETURNFX2 },
                { Voicemeeter.DialType.GAIN, Voicemeeter.DialType.RETURNREVERB, Voicemeeter.DialType.RETURNDELAY, Voicemeeter.DialType.RETURNFX1, Voicemeeter.DialType.RETURNFX2 },
                { Voicemeeter.DialType.GAIN, Voicemeeter.DialType.RETURNREVERB, Voicemeeter.DialType.RETURNDELAY, Voicemeeter.DialType.RETURNFX1, Voicemeeter.DialType.RETURNFX2 },
                { Voicemeeter.DialType.GAIN, Voicemeeter.DialType.RETURNREVERB, Voicemeeter.DialType.RETURNDELAY, Voicemeeter.DialType.RETURNFX1, Voicemeeter.DialType.RETURNFX2 },
                { Voicemeeter.DialType.GAIN, Voicemeeter.DialType.RETURNREVERB, Voicemeeter.DialType.RETURNDELAY, Voicemeeter.DialType.RETURNFX1, Voicemeeter.DialType.RETURNFX2 },
                { Voicemeeter.DialType.GAIN, Voicemeeter.DialType.RETURNREVERB, Voicemeeter.DialType.RETURNDELAY, Voicemeeter.DialType.RETURNFX1, Voicemeeter.DialType.RETURNFX2 } };

        private final Voicemeeter.ButtonType[][] potatoStripButtons = { {
                Voicemeeter.ButtonType.A1, Voicemeeter.ButtonType.A2, Voicemeeter.ButtonType.A3, Voicemeeter.ButtonType.A4, Voicemeeter.ButtonType.A5, Voicemeeter.ButtonType.B1,
                Voicemeeter.ButtonType.B2, Voicemeeter.ButtonType.B3, Voicemeeter.ButtonType.MONO, Voicemeeter.ButtonType.SOLO,
                Voicemeeter.ButtonType.MUTE }, {
                Voicemeeter.ButtonType.A1, Voicemeeter.ButtonType.A2, Voicemeeter.ButtonType.A3, Voicemeeter.ButtonType.A4, Voicemeeter.ButtonType.A5, Voicemeeter.ButtonType.B1,
                Voicemeeter.ButtonType.B2, Voicemeeter.ButtonType.B3, Voicemeeter.ButtonType.MONO, Voicemeeter.ButtonType.SOLO,
                Voicemeeter.ButtonType.MUTE }, {
                Voicemeeter.ButtonType.A1, Voicemeeter.ButtonType.A2, Voicemeeter.ButtonType.A3, Voicemeeter.ButtonType.A4, Voicemeeter.ButtonType.A5, Voicemeeter.ButtonType.B1,
                Voicemeeter.ButtonType.B2, Voicemeeter.ButtonType.B3, Voicemeeter.ButtonType.MONO, Voicemeeter.ButtonType.SOLO,
                Voicemeeter.ButtonType.MUTE }, {
                Voicemeeter.ButtonType.A1, Voicemeeter.ButtonType.A2, Voicemeeter.ButtonType.A3, Voicemeeter.ButtonType.A4, Voicemeeter.ButtonType.A5, Voicemeeter.ButtonType.B1,
                Voicemeeter.ButtonType.B2, Voicemeeter.ButtonType.B3, Voicemeeter.ButtonType.MONO, Voicemeeter.ButtonType.SOLO,
                Voicemeeter.ButtonType.MUTE }, {
                Voicemeeter.ButtonType.A1, Voicemeeter.ButtonType.A2, Voicemeeter.ButtonType.A3, Voicemeeter.ButtonType.A4, Voicemeeter.ButtonType.A5, Voicemeeter.ButtonType.B1,
                Voicemeeter.ButtonType.B2, Voicemeeter.ButtonType.B3, Voicemeeter.ButtonType.MONO, Voicemeeter.ButtonType.SOLO,
                Voicemeeter.ButtonType.MUTE }, {
                Voicemeeter.ButtonType.A1, Voicemeeter.ButtonType.A2, Voicemeeter.ButtonType.A3, Voicemeeter.ButtonType.A4, Voicemeeter.ButtonType.A5, Voicemeeter.ButtonType.B1,
                Voicemeeter.ButtonType.B2, Voicemeeter.ButtonType.B3, Voicemeeter.ButtonType.MC, Voicemeeter.ButtonType.SOLO,
                Voicemeeter.ButtonType.MUTE },
                { Voicemeeter.ButtonType.A1, Voicemeeter.ButtonType.A2, Voicemeeter.ButtonType.A3, Voicemeeter.ButtonType.A4, Voicemeeter.ButtonType.A5, Voicemeeter.ButtonType.B1,
                        Voicemeeter.ButtonType.B2, Voicemeeter.ButtonType.B3, Voicemeeter.ButtonType.SOLO, Voicemeeter.ButtonType.MUTE }, {
                Voicemeeter.ButtonType.A1, Voicemeeter.ButtonType.A2, Voicemeeter.ButtonType.A3, Voicemeeter.ButtonType.A4, Voicemeeter.ButtonType.A5, Voicemeeter.ButtonType.B1,
                Voicemeeter.ButtonType.B2, Voicemeeter.ButtonType.B3, Voicemeeter.ButtonType.MC, Voicemeeter.ButtonType.SOLO,
                Voicemeeter.ButtonType.MUTE } };

        private final Voicemeeter.ButtonType[][] potatoBusButtons = {
                { Voicemeeter.ButtonType.SEL, Voicemeeter.ButtonType.MONO, Voicemeeter.ButtonType.EQ, Voicemeeter.ButtonType.MUTE },
                { Voicemeeter.ButtonType.SEL, Voicemeeter.ButtonType.MONO, Voicemeeter.ButtonType.EQ, Voicemeeter.ButtonType.MUTE },
                { Voicemeeter.ButtonType.SEL, Voicemeeter.ButtonType.MONO, Voicemeeter.ButtonType.EQ, Voicemeeter.ButtonType.MUTE },
                { Voicemeeter.ButtonType.SEL, Voicemeeter.ButtonType.MONO, Voicemeeter.ButtonType.EQ, Voicemeeter.ButtonType.MUTE },
                { Voicemeeter.ButtonType.SEL, Voicemeeter.ButtonType.MONO, Voicemeeter.ButtonType.EQ, Voicemeeter.ButtonType.MUTE },
                { Voicemeeter.ButtonType.SEL, Voicemeeter.ButtonType.MONO, Voicemeeter.ButtonType.EQ, Voicemeeter.ButtonType.MUTE },
                { Voicemeeter.ButtonType.SEL, Voicemeeter.ButtonType.MONO, Voicemeeter.ButtonType.EQ, Voicemeeter.ButtonType.MUTE },
                { Voicemeeter.ButtonType.SEL, Voicemeeter.ButtonType.MONO, Voicemeeter.ButtonType.EQ, Voicemeeter.ButtonType.MUTE } };

        @Override
        public Voicemeeter.DialType[][] getStripDials() {
            return potatoStripDials;
        }

        @Override
        public Voicemeeter.DialType[][] getBusDials() {
            return potatoBusDials;
        }

        @Override
        public Voicemeeter.ButtonType[][] getStripButtons() {
            return potatoStripButtons;
        }

        @Override
        public Voicemeeter.ButtonType[][] getBusButtons() {
            return potatoBusButtons;
        }
    },
    BANANA {
        private final Voicemeeter.DialType[][] bananaStripDials = {
                { Voicemeeter.DialType.GAIN, Voicemeeter.DialType.COMP, Voicemeeter.DialType.GATE, Voicemeeter.DialType.LIMIT },
                { Voicemeeter.DialType.GAIN, Voicemeeter.DialType.COMP, Voicemeeter.DialType.GATE, Voicemeeter.DialType.LIMIT },
                { Voicemeeter.DialType.GAIN, Voicemeeter.DialType.COMP, Voicemeeter.DialType.GATE, Voicemeeter.DialType.LIMIT },
                { Voicemeeter.DialType.GAIN, Voicemeeter.DialType.EQGAIN1, Voicemeeter.DialType.EQGAIN2, Voicemeeter.DialType.EQGAIN3 },
                { Voicemeeter.DialType.GAIN, Voicemeeter.DialType.EQGAIN1, Voicemeeter.DialType.EQGAIN2, Voicemeeter.DialType.EQGAIN3 } };

        private final Voicemeeter.DialType[][] bananaBusDials = { { Voicemeeter.DialType.GAIN }, { Voicemeeter.DialType.GAIN }, { Voicemeeter.DialType.GAIN },
                { Voicemeeter.DialType.GAIN }, { Voicemeeter.DialType.GAIN } };

        private final Voicemeeter.ButtonType[][] bananaStripButtons = {
                { Voicemeeter.ButtonType.A1, Voicemeeter.ButtonType.A2, Voicemeeter.ButtonType.A3, Voicemeeter.ButtonType.B1, Voicemeeter.ButtonType.B2, Voicemeeter.ButtonType.MONO,
                        Voicemeeter.ButtonType.SOLO, Voicemeeter.ButtonType.MUTE },
                { Voicemeeter.ButtonType.A1, Voicemeeter.ButtonType.A2, Voicemeeter.ButtonType.A3, Voicemeeter.ButtonType.B1, Voicemeeter.ButtonType.B2, Voicemeeter.ButtonType.MONO,
                        Voicemeeter.ButtonType.SOLO, Voicemeeter.ButtonType.MUTE },
                { Voicemeeter.ButtonType.A1, Voicemeeter.ButtonType.A2, Voicemeeter.ButtonType.A3, Voicemeeter.ButtonType.B1, Voicemeeter.ButtonType.B2, Voicemeeter.ButtonType.MONO,
                        Voicemeeter.ButtonType.SOLO, Voicemeeter.ButtonType.MUTE },
                { Voicemeeter.ButtonType.A1, Voicemeeter.ButtonType.A2, Voicemeeter.ButtonType.A3, Voicemeeter.ButtonType.B1, Voicemeeter.ButtonType.B2, Voicemeeter.ButtonType.MC,
                        Voicemeeter.ButtonType.SOLO, Voicemeeter.ButtonType.MUTE },
                { Voicemeeter.ButtonType.A1, Voicemeeter.ButtonType.A2, Voicemeeter.ButtonType.A3, Voicemeeter.ButtonType.B1, Voicemeeter.ButtonType.B2, Voicemeeter.ButtonType.SOLO,
                        Voicemeeter.ButtonType.MUTE } };

        private final Voicemeeter.ButtonType[][] bananaBusButtons = { { Voicemeeter.ButtonType.MONO, Voicemeeter.ButtonType.EQ, Voicemeeter.ButtonType.MUTE },
                { Voicemeeter.ButtonType.MONO, Voicemeeter.ButtonType.EQ, Voicemeeter.ButtonType.MUTE }, { Voicemeeter.ButtonType.MONO, Voicemeeter.ButtonType.EQ, Voicemeeter.ButtonType.MUTE },
                { Voicemeeter.ButtonType.MONO, Voicemeeter.ButtonType.EQ, Voicemeeter.ButtonType.MUTE }, { Voicemeeter.ButtonType.MONO, Voicemeeter.ButtonType.EQ, Voicemeeter.ButtonType.MUTE } };

        @Override
        public Voicemeeter.DialType[][] getStripDials() {
            return bananaStripDials;
        }

        @Override
        public Voicemeeter.DialType[][] getBusDials() {
            return bananaBusDials;
        }

        @Override
        public Voicemeeter.ButtonType[][] getStripButtons() {
            return bananaStripButtons;
        }

        @Override
        public Voicemeeter.ButtonType[][] getBusButtons() {
            return bananaBusButtons;
        }
    },
    VOICEMEETER {
        private final Voicemeeter.DialType[][] voicemeeterStripDials = { { Voicemeeter.DialType.GAIN, Voicemeeter.DialType.AUDIBILITY },
                { Voicemeeter.DialType.GAIN, Voicemeeter.DialType.AUDIBILITY },
                { Voicemeeter.DialType.GAIN, Voicemeeter.DialType.EQGAIN1, Voicemeeter.DialType.EQGAIN2, Voicemeeter.DialType.EQGAIN3 } };

        private final Voicemeeter.DialType[][] voicemeeterBusDials = { { Voicemeeter.DialType.GAIN }, { Voicemeeter.DialType.GAIN } };

        private final Voicemeeter.ButtonType[][] voicemeeterStripButtons = {
                { Voicemeeter.ButtonType.A1, Voicemeeter.ButtonType.B1, Voicemeeter.ButtonType.MONO, Voicemeeter.ButtonType.SOLO, Voicemeeter.ButtonType.MUTE },
                { Voicemeeter.ButtonType.A1, Voicemeeter.ButtonType.B1, Voicemeeter.ButtonType.MONO, Voicemeeter.ButtonType.SOLO, Voicemeeter.ButtonType.MUTE },
                { Voicemeeter.ButtonType.A1, Voicemeeter.ButtonType.B1, Voicemeeter.ButtonType.MC, Voicemeeter.ButtonType.SOLO, Voicemeeter.ButtonType.MUTE } };

        private final Voicemeeter.ButtonType[][] voicemeeterBusButtons = {
                { Voicemeeter.ButtonType.MIXA, Voicemeeter.ButtonType.REPEAT, Voicemeeter.ButtonType.COMPOSITE, Voicemeeter.ButtonType.MONO, Voicemeeter.ButtonType.MUTE },
                { Voicemeeter.ButtonType.MIXA, Voicemeeter.ButtonType.REPEAT, Voicemeeter.ButtonType.COMPOSITE, Voicemeeter.ButtonType.MONO, Voicemeeter.ButtonType.MUTE } };

        @Override
        public Voicemeeter.DialType[][] getStripDials() {
            return voicemeeterStripDials;
        }

        @Override
        public Voicemeeter.DialType[][] getBusDials() {
            return voicemeeterBusDials;
        }

        @Override
        public Voicemeeter.ButtonType[][] getStripButtons() {
            return voicemeeterStripButtons;
        }

        @Override
        public Voicemeeter.ButtonType[][] getBusButtons() {
            return voicemeeterBusButtons;
        }
    };

    public Voicemeeter.DialType[][] getStripDials() {
        return null;
    }

    public Voicemeeter.DialType[][] getBusDials() {
        return null;
    }

    public Voicemeeter.ButtonType[][] getStripButtons() {
        return null;
    }

    public Voicemeeter.ButtonType[][] getBusButtons() {
        return null;
    }
}

