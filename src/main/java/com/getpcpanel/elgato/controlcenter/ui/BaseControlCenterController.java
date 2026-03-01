package com.getpcpanel.elgato.controlcenter.ui;

import java.util.Objects;

import javax.annotation.Nullable;

import com.getpcpanel.elgato.controlcenter.ControlCenterService;
import com.getpcpanel.elgato.controlcenter.command.CommandControlCenter;
import com.getpcpanel.ui.command.CommandContext;
import com.getpcpanel.ui.command.CommandController;

import io.reactivex.annotations.NonNull;
import javafx.beans.Observable;
import javafx.fxml.FXML;
import javafx.scene.control.ChoiceBox;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import one.util.streamex.EntryStream;
import one.util.streamex.StreamEx;

@Log4j2
@RequiredArgsConstructor
class BaseControlCenterController<T extends CommandControlCenter> extends CommandController<T> {
    protected final ControlCenterService controlCenterService;
    @FXML protected ChoiceBox<Entry> device;

    @Override
    public void postInit(CommandContext context) {
        if (!controlCenterService.isConnected()) {
            return;
        }

        StreamEx.ofValues(controlCenterService.getDevices())
                .map(dev -> new Entry(dev.deviceID(), dev.name()))
                .sortedBy(Entry::name)
                .into(device.getItems());
    }

    @Override
    public void initFromCommand(T cmd) {
        if (cmd instanceof CommandControlCenter changeCmd) {
            selectId(device, changeCmd.getId());
        }

        super.initFromCommand(cmd);
    }

    protected String getSelectedDeviceId() {
        var selected = device.getValue();
        if (selected == null) {
            return "";
        }
        return selected.id();
    }

    protected void selectId(ChoiceBox<Entry> choice, @Nullable String cmd) {
        EntryStream.of(choice.getItems()).filterValues(v -> Objects.equals(cmd, v.id()))
                   .keys().findFirst()
                   .ifPresent(integer -> choice.getSelectionModel().select(integer));
    }

    @Override
    protected Observable[] determineDependencies() {
        return new Observable[] {
                device.valueProperty(),
        };
    }

    record Entry(String id, String name) {
        @NonNull
        @Override
        public String toString() {
            return name;
        }
    }
}
