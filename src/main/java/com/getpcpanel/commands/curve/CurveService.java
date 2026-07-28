package com.getpcpanel.commands.curve;

import java.util.List;

import javax.annotation.Nullable;

import com.getpcpanel.commands.DialValueCalculator;
import com.getpcpanel.profile.SaveService;
import com.getpcpanel.profile.dto.CurveDefinition;
import com.getpcpanel.profile.dto.KnobSetting;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/** Resolves the curve a control names against the saved library. */
@ApplicationScoped
public class CurveService {
    @Inject SaveService saveService;

    /** Everything the curve picker offers: the built-ins, then the user's own. */
    public List<CurveDefinition> library() {
        return Curves.library(saveService.get().getCurves());
    }

    public Curve resolve(@Nullable String id) {
        return Curves.resolve(id, saveService.get().getCurves());
    }

    public Curve forControl(@Nullable KnobSetting setting) {
        return resolve(setting == null ? null : setting.getCurve());
    }

    public DialValueCalculator calculatorFor(@Nullable KnobSetting setting) {
        return new DialValueCalculator(setting, forControl(setting));
    }
}
