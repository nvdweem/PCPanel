package com.getpcpanel.profile.dto;

import javax.annotation.Nullable;

import com.fasterxml.jackson.annotation.JsonIgnore;
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
    /** Id of the response curve in the user's library; null — or an id no longer in it — is straight through. */
    @Nullable private String curve;
    private String overlayIcon;
    private int buttonDebounce = 50;

    /** Whether a curve property was present at all, null included. Not part of the value. */
    @JsonIgnore
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    private boolean curveRead;

    public KnobSetting setCurve(@Nullable String curve) {
        this.curve = curve;
        curveRead = true;
        return this;
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
     * Applied only when the document carried no curve property at all, so the two may appear in either
     * order without the older one clobbering the newer — and so a client clearing a control back to linear
     * (curve null next to a flag it has not refreshed) is not silently undone.
     */
    public KnobSetting setLogarithmic(boolean logarithmic) {
        if (logarithmic && !curveRead) {
            curve = Curves.LOGARITHMIC_ID;
        }
        return this;
    }
}
