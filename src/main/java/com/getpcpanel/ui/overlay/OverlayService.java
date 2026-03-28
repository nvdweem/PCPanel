package com.getpcpanel.ui.overlay;

import com.getpcpanel.commands.IconService;
import com.getpcpanel.commands.PCPanelControlEvent;
import com.getpcpanel.profile.SaveService;
import com.getpcpanel.profile.SaveService.SaveEvent;
import com.getpcpanel.spring.ConditionalOnWindows;
import com.getpcpanel.ui.FxHelper;
import com.getpcpanel.util.Debouncer;

import jakarta.annotation.Nullable;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

@ApplicationScoped
@ConditionalOnWindows
public class OverlayService {
    private final Overlay overlay;

    public OverlayService(FxHelper fxHelper, SaveService save, IconService iconService, Debouncer debouncer) {
        overlay = new Overlay(fxHelper, save, iconService, debouncer);
    }

    public void updateSaveValues(@Observes @Nullable SaveEvent event) {
        overlay.updateSaveValues();
    }

    public void updateStyle(@Observes @Nullable SaveEvent event) {
        overlay.updateStyle();
    }

    public void handleControl(@Observes PCPanelControlEvent event) {
        overlay.handleControl(event);
    }
}
