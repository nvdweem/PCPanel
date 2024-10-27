package com.getpcpanel.commands.command;

import javax.annotation.Nullable;

import com.getpcpanel.hid.DialValue;

public interface DialAction {
    void execute(DialActionParameters context);

    default Runnable toRunnable(DialActionParameters context) {
        return () -> execute(context);
    }

    default boolean hasOverlay() {
        return true;
    }

    @Nullable DialCommandParams getDialParams();

    default boolean isInvert() {
        return getDialParams() != null && getDialParams().invert;
    }

    record DialActionParameters(String device, boolean initial, DialValue dial) {
    }

    record DialCommandParams(boolean invert, @Nullable Integer moveStart, @Nullable Integer moveEnd) {
        public static final DialCommandParams DEFAULT = new DialCommandParams(false, null, null);

        public int moveStartNonNull() {
            return moveStart == null ? 0 : moveStart;
        }

        public int moveEndNonNull() {
            return moveEnd == null ? 0 : moveEnd;
        }

        public DialCommandParams withInvert(Boolean newValue) {
            return new DialCommandParams(newValue, moveStart, moveEnd);
        }
    }
}
