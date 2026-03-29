package com.getpcpanel.ui.overlay;

import java.util.Optional;

import com.getpcpanel.commands.PCPanelControlEvent;
import com.getpcpanel.profile.SaveService.SaveEvent;
import com.getpcpanel.spring.ConditionalOnWindows;
import com.getpcpanel.spring.ConditionalOnWindows.OnWindowsCondition;

import io.quarkiverse.fx.FxPostStartupEvent;
import io.quarkiverse.fx.views.FxViewRepository;
import jakarta.annotation.Nullable;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import lombok.RequiredArgsConstructor;

@ApplicationScoped
@ConditionalOnWindows
@RequiredArgsConstructor
public class OverlayService {
    private final FxViewRepository viewRepository;
    private Optional<OverlayController> overlay = Optional.empty();

    void onPostStartup(@Observes FxPostStartupEvent event) throws Exception {
        if (OnWindowsCondition.matches()) {
            overlay = viewRepository.getViewData("Overlay").getController();
        }
    }

    public void updateSaveValues(@Observes @Nullable SaveEvent event) {
        overlay.ifPresent(OverlayController::updateSaveValues);
    }

    public void updateStyle(@Observes @Nullable SaveEvent event) {
        overlay.ifPresent(OverlayController::updateStyle);
    }

    public void handleControl(@Observes PCPanelControlEvent event) {
        overlay.ifPresent(c -> c.handleControl(event));
    }
}
