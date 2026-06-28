package com.getpcpanel.analogbands.command;

import com.getpcpanel.commands.command.Command;
import com.getpcpanel.commands.command.DialAction;
import java.util.List;

import javax.annotation.Nullable;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.getpcpanel.commands.meta.CommandCategory;
import com.getpcpanel.commands.meta.CommandKind;
import com.getpcpanel.commands.meta.CommandMeta;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.getpcpanel.analogbands.AnalogBandColorService;
import com.getpcpanel.analogbands.BandTransition;
import com.getpcpanel.commands.Commands;
import com.getpcpanel.commands.PCPanelControlEvent;
import com.getpcpanel.util.CdiHelper;

import lombok.Getter;
import lombok.ToString;
import lombok.extern.log4j.Log4j2;

/**
 * Turns a single analog control (dial or slider) into a multi-position rotary switch. The control's
 * 0-255 travel is partitioned into ordered {@link AnalogBand}s; each band fires its own commands the
 * moment the control <em>enters</em> that band and can show its own LED feedback colour.
 *
 * <p>Firing is edge-triggered on position change: moving from band 1 into band 2 runs band 2's
 * commands once, and continued movement <em>within</em> band 2 does nothing. Bands may leave gaps,
 * which act as dead zones between positions (entering a gap fires nothing and leaves the selected
 * position unchanged), giving a noisy reading natural hysteresis. The current position is tracked on
 * this in-memory instance and is not persisted.
 *
 * <p>Each band's commands run through the normal {@link PCPanelControlEvent} machinery, so a band can
 * hold any command(s) — most usefully a {@link CommandProfile}, which makes one dial select between
 * many profiles.
 */
@Getter
@Log4j2
@ToString(callSuper = true)
@JsonTypeName("analogbands.ranges")
@CommandMeta(label = "Stepped switch (ranges)", category = CommandCategory.system, kinds = {CommandKind.dial}, icon = "sliders", legacyIds = {"com.getpcpanel.commands.command.CommandAnalogBands"})
public class CommandAnalogBands extends Command implements DialAction {
    private static final int MAX_RAW = 255;

    private final List<AnalogBand> bands;

    /** The band the control currently rests on, or -1 before the first reading / while in a gap with no prior position.
     *  volatile: written by advance() on the command-handler thread, read by getCurrentColor() from CDI observer
     *  threads (profile-switch / lighting-edit refresh); a single int needs only visibility, not atomicity. */
    @JsonIgnore private volatile int currentBand = -1;

    @JsonCreator
    public CommandAnalogBands(@Nullable @JsonProperty("bands") List<AnalogBand> bands) {
        this.bands = bands == null ? List.of() : List.copyOf(bands);
    }

    @Override
    public void execute(DialActionParameters context) {
        var transition = advance(context.dial().value(), context.initial());
        if (transition.changed()) {
            // The selected position moved: refresh the per-position LED feedback for this device.
            CdiHelper.getBean(AnalogBandColorService.class).refresh(context.device());
        }
        if (transition.fire()) {
            var commands = bands.get(transition.band()).commands();
            // Built and run inline (never goes through the dispatcher map), so the source is only for
            // consistency — this is the per-position action of a stepped dial/slider.
            new PCPanelControlEvent(context.device(), 0, commands, false, context.dial(), PCPanelControlEvent.Source.DIAL).buildRunnable().run();
        }
    }

    /**
     * Feeds a raw 0-255 reading and updates the selected position. Returns the resulting position,
     * whether the selected position changed, and whether the entered band's commands should fire (a
     * fresh, non-empty band reached by an actual movement — not the initial sync on connect).
     * Free of side effects (beyond advancing the selected position), so it is also the unit-test seam.
     */
    public BandTransition advance(int raw, boolean initial) {
        var band = bandIndexFor(raw);
        if (band == -1 || band == currentBand) {
            // In a gap, or still resting on the same position: nothing changes.
            return new BandTransition(currentBand, false, false);
        }
        currentBand = band;
        var fire = !initial && Commands.hasCommands(bands.get(band).commands());
        return new BandTransition(band, true, fire);
    }

    // (BandTransition lives in the analogbands package so it stays out of the generated TS contract.)

    /** Index of the band whose range contains the given raw 0-255 value, or -1 when it falls in a gap. */
    int bandIndexFor(int raw) {
        var pct = raw * 100.0 / MAX_RAW;
        for (var i = 0; i < bands.size(); i++) {
            if (bands.get(i).contains(pct)) {
                return i;
            }
        }
        return -1;
    }

    /** The LED feedback colour of the currently selected position, or null when none is set / selected. */
    @JsonIgnore
    public @Nullable String getCurrentColor() {
        return currentBand >= 0 && currentBand < bands.size() ? bands.get(currentBand).color() : null;
    }

    @Override
    @Nullable
    public DialCommandParams getDialParams() {
        // Bands map the raw travel to positions directly; the trim/move/invert mapping does not apply.
        return null;
    }

    @Override
    public boolean hasOverlay() {
        return false;
    }

    @Override
    public String buildLabel() {
        return bands.size() + (bands.size() == 1 ? " position" : " positions");
    }
}
