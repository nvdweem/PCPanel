package dev.niels.pulse.model;

/** An object the server announced as new, changed or removed ({@code pa_subscription_event_type_t}). */
public record SubscriptionEvent(Facility facility, Type type, int index) {
    public static SubscriptionEvent decode(long event, int index) {
        return new SubscriptionEvent(Facility.of((int) (event & 0x0F)), Type.of((int) (event & 0x30)), index);
    }

    /** What changed, named the way {@code pactl subscribe} names it. */
    public enum Facility {
        SINK(0, "sink"), SOURCE(1, "source"), SINK_INPUT(2, "sink-input"), SOURCE_OUTPUT(3, "source-output"), MODULE(4, "module"),
        CLIENT(5, "client"), SAMPLE_CACHE(6, "sample-cache"), SERVER(7, "server"), CARD(9, "card"), UNKNOWN(-1, "unknown");

        private final int code;
        private final String pactlName;

        Facility(int code, String pactlName) {
            this.code = code;
            this.pactlName = pactlName;
        }

        /** The bit to set in a subscription mask to receive this facility's events; none for {@link #UNKNOWN}. */
        public int maskBit() {
            return code < 0 ? 0 : 1 << code;
        }

        public String pactlName() {
            return pactlName;
        }

        static Facility of(int code) {
            for (var f : values()) {
                if (f.code == code) {
                    return f;
                }
            }
            return UNKNOWN;
        }
    }

    public enum Type {
        NEW("new"), CHANGE("change"), REMOVE("remove");

        private final String pactlName;

        Type(String pactlName) {
            this.pactlName = pactlName;
        }

        public String pactlName() {
            return pactlName;
        }

        static Type of(int code) {
            return switch (code) {
                case 0x00 -> NEW;
                case 0x20 -> REMOVE;
                default -> CHANGE;
            };
        }
    }
}
