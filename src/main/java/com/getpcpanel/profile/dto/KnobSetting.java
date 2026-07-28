package com.getpcpanel.profile.dto;

import javax.annotation.Nullable;

import com.getpcpanel.commands.curve.Curves;

import lombok.Data;

@Data
public class KnobSetting {
    private int minTrim;
    private int maxTrim = 100;
    /** Id of the response curve in the user's library; null — or an id no longer in it — is straight through. */
    @Nullable private String curve;
    private String overlayIcon;
    private int buttonDebounce = 50;

    /**
     * Part of the JSON contract for saves written before curves were named: reading one seeds
     * {@link #curve}, and writing it lets an older app read a new save file and still land on the right
     * one of the two shapes it knows. Only the built-in logarithmic curve reports true.
     */
    public boolean isLogarithmic() {
        return Curves.LOGARITHMIC_ID.equals(curve);
    }

    /**
     * Applied only when no {@link #curve} has been read yet, so the two properties may appear in either
     * order in the file without the older one clobbering the newer.
     */
    public KnobSetting setLogarithmic(boolean logarithmic) {
        if (logarithmic && curve == null) {
            curve = Curves.LOGARITHMIC_ID;
        }
        return this;
    }
}
