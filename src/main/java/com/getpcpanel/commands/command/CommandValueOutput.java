package com.getpcpanel.commands.command;

import javax.annotation.Nullable;

import com.getpcpanel.util.ValueInterpolator;

import lombok.Getter;
import lombok.ToString;

/**
 * Base for the generic "output" commands (HTTP / MQTT / OSC) that emit a value-driven message.
 *
 * <p>Each is both a {@link DialAction} and a {@link ButtonAction}: on a dial the 0..1 position maps
 * (via {@code min}/{@code max} or the {@code formula}) to the number substituted for
 * <code>{{ value }}</code> in the subclass's templated fields; on a button there is no position, so the
 * value resolves at full scale (x = 1.0). The dispatcher prefers the dial path when a dial value is
 * present (see {@link Command#toRunnable}).
 */
@Getter
@ToString(callSuper = true)
public abstract class CommandValueOutput extends Command implements DialAction, ButtonAction {
    @Nullable private final Double min;
    @Nullable private final Double max;
    @Nullable private final String formula;
    @Nullable private final DialCommandParams dialParams;

    protected CommandValueOutput(@Nullable Double min, @Nullable Double max, @Nullable String formula, @Nullable DialCommandParams dialParams) {
        this.min = min;
        this.max = max;
        this.formula = formula;
        this.dialParams = dialParams;
    }

    @Override
    public void execute(DialActionParameters context) {
        // A moving dial fires a stream of events, so the dial path is rate-limitable (immediate = false).
        send(ValueInterpolator.translate(context.dial().getValue(this, 0f, 1f), min, max, formula), false);
    }

    @Override
    public void execute() {
        // A button has no dial position; resolve at full scale so {{ value }} renders the configured max,
        // and fire immediately (a single press should not be debounced).
        send(ValueInterpolator.translate(1d, min, max, formula), true);
    }

    /**
     * Emit the message with {@code value} substituted into the subclass's templated fields.
     *
     * @param immediate {@code true} for a one-shot button press, {@code false} for a dial stream the
     *                  subclass may rate-limit (MQTT debounce, HTTP leading-edge throttle, …).
     */
    protected abstract void send(double value, boolean immediate);

    /** These are not volume controls, so they never drive the on-screen volume overlay. */
    @Override
    public boolean hasOverlay() {
        return false;
    }
}
