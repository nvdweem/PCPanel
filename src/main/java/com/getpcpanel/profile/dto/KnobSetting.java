package com.getpcpanel.profile.dto;

import javax.annotation.Nullable;

import org.apache.commons.lang3.StringUtils;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.getpcpanel.commands.curve.Curves;

import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Data
public class KnobSetting {
    private int minTrim;
    private int maxTrim = 100;
    /**
     * Id of the response curve in the user's library; null — or an id no longer in it — is straight through.
     * Left out of the document when unset: an explicit null would sit in every save file beside the legacy
     * flag, and a reader has to be able to tell "no curve named here" from "linear was chosen".
     */
    @Nullable @JsonInclude(JsonInclude.Include.NON_NULL) private String curve;
    private String overlayIcon;
    private int buttonDebounce = 50;

    /** The legacy flag as read, so the two properties settle the curve the same in either order. */
    @JsonIgnore
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    private boolean legacyLogarithmic;

    /** A blank curve names nothing, so a legacy flag already read still decides. */
    public KnobSetting setCurve(@Nullable String curve) {
        this.curve = StringUtils.isBlank(curve) && legacyLogarithmic ? Curves.LOGARITHMIC_ID : curve;
        return this;
    }

    /**
     * Takes every property from another setting. The stored setting is edited in place rather than
     * replaced, so this is the one place that has to list them — a property missing here would be
     * dropped on the way in from the UI.
     */
    public void copyFrom(KnobSetting source) {
        minTrim = source.minTrim;
        maxTrim = source.maxTrim;
        curve = source.curve;
        overlayIcon = source.overlayIcon;
        buttonDebounce = source.buttonDebounce;
    }

    /**
     * Part of the JSON contract for saves written before curves were named: reading one seeds
     * {@link #curve}, and writing it lets an older app read a new save file and still land on the right
     * one of the two shapes it knows. Only the built-in logarithmic curve reports true.
     */
    public boolean isLogarithmic() {
        return Curves.LOGARITHMIC_ID.equals(curve);
    }

    /**
     * Applied whenever nothing else names a curve, so the two may appear in either order without the
     * older one clobbering the newer. Deciding this on whether a curve property was *present* would hand
     * the answer to document order, which is not ours to choose — a writer sorting properties
     * alphabetically puts a null curve ahead of the flag, and reading that null as an answer drops the
     * setting. Anything choosing linear deliberately says so by naming it.
     */
    public KnobSetting setLogarithmic(boolean logarithmic) {
        legacyLogarithmic = logarithmic;
        if (logarithmic && StringUtils.isBlank(curve)) {
            curve = Curves.LOGARITHMIC_ID;
        }
        return this;
    }
}
