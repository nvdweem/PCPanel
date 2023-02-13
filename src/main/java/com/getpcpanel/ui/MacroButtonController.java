package com.getpcpanel.ui;

import static com.getpcpanel.commands.command.CommandNoOp.NOOP;

import java.util.HashMap;
import java.util.function.Consumer;

import org.springframework.stereotype.Component;

import com.getpcpanel.commands.command.Command;
import com.getpcpanel.commands.command.CommandEndProgram;
import com.getpcpanel.commands.command.CommandKeystroke;
import com.getpcpanel.commands.command.CommandMedia;
import com.getpcpanel.commands.command.CommandNoOp;
import com.getpcpanel.commands.command.CommandObsMuteSource;
import com.getpcpanel.commands.command.CommandObsSetScene;
import com.getpcpanel.commands.command.CommandProfile;
import com.getpcpanel.commands.command.CommandShortcut;
import com.getpcpanel.commands.command.CommandVoiceMeeterAdvancedButton;
import com.getpcpanel.commands.command.CommandVoiceMeeterBasicButton;
import com.getpcpanel.commands.command.CommandVolumeApplicationDeviceToggle;
import com.getpcpanel.commands.command.CommandVolumeDefaultDevice;
import com.getpcpanel.commands.command.CommandVolumeDefaultDeviceAdvanced;
import com.getpcpanel.commands.command.CommandVolumeDefaultDeviceToggle;
import com.getpcpanel.commands.command.CommandVolumeDefaultDeviceToggleAdvanced;
import com.getpcpanel.commands.command.CommandVolumeDeviceMute;
import com.getpcpanel.commands.command.CommandVolumeFocusMute;
import com.getpcpanel.commands.command.CommandVolumeProcessMute;
import com.getpcpanel.spring.OsHelper;
import com.getpcpanel.spring.Prototype;
import com.getpcpanel.ui.command.CommandContext;
import com.getpcpanel.ui.command.button.BtnApplicationDeviceToggleController;
import com.getpcpanel.ui.command.button.BtnDefaultDeviceAdvancedController;
import com.getpcpanel.ui.command.button.BtnDefaultDeviceController;
import com.getpcpanel.ui.command.button.BtnDefaultDeviceToggleController;
import com.getpcpanel.ui.command.button.BtnDeviceMuteController;
import com.getpcpanel.ui.command.button.BtnDeviceToggleAdvancedController;
import com.getpcpanel.ui.command.button.BtnEndProgramController;
import com.getpcpanel.ui.command.button.BtnFocusMuteController;
import com.getpcpanel.ui.command.button.BtnKeystrokeController;
import com.getpcpanel.ui.command.button.BtnMediaController;
import com.getpcpanel.ui.command.button.BtnNoopController;
import com.getpcpanel.ui.command.button.BtnObsController;
import com.getpcpanel.ui.command.button.BtnProfileController;
import com.getpcpanel.ui.command.button.BtnShortcutController;
import com.getpcpanel.ui.command.button.BtnVoiceMeeterController;
import com.getpcpanel.ui.command.button.BtnVolumeProcessMuteController;
import com.getpcpanel.util.Util;

import javafx.fxml.FXML;
import javafx.scene.control.TabPane;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import one.util.streamex.StreamEx;

@Log4j2
@Component
@Prototype
@RequiredArgsConstructor
public class MacroButtonController {
    private final OsHelper osHelper;
    private final FxHelper fxHelper;

    private CommandContext context;
    private Command cmd;
    @FXML @Getter private TabPane root;

    // Controllers
    @FXML private BtnNoopController btnCommandNoopController;
    @FXML private BtnKeystrokeController btnCommandKeystrokeController;
    @FXML private BtnShortcutController btnCommandShortcutController;
    @FXML private BtnMediaController btnCommandMediaController;
    @FXML private BtnEndProgramController btnCommandEndProgramController;
    @FXML private BtnDefaultDeviceController btnCommandVolumeDefaultDeviceController;
    @FXML private BtnDefaultDeviceAdvancedController btnCommandVolumeDefaultDeviceAdvancedController;
    @FXML private BtnApplicationDeviceToggleController btnCommandVolumeApplicationDeviceToggleController;
    @FXML private BtnDefaultDeviceToggleController btnCommandVolumeDefaultDeviceToggleController;
    @FXML private BtnDeviceToggleAdvancedController btnCommandVolumeDefaultDeviceToggleAdvancedController;
    @FXML private BtnVolumeProcessMuteController btnCommandVolumeProcessMuteController;
    @FXML private BtnFocusMuteController btnCommandVolumeFocusMuteController;
    @FXML private BtnDeviceMuteController btnCommandVolumeDeviceMuteController;
    @FXML private BtnObsController btnCommandObsController;
    @FXML private BtnVoiceMeeterController btnCommandVoiceMeeterController;
    @FXML private BtnProfileController btnCommandProfileController;

    public void initController(CommandContext context, Command buttonData) {
        this.context = context;
        cmd = buttonData;
        postInit();
    }

    private void postInit() {
        var toRemove = StreamEx.of(root.getTabs()).remove(osHelper::isSupported).toSet();
        root.getTabs().removeAll(toRemove);
        Util.adjustTabs(root, 150, 30);

        // TODO
        btnCommandNoopController.postInit(context, cmd);
        btnCommandKeystrokeController.postInit(context, cmd);
        btnCommandShortcutController.postInit(context, cmd);
        btnCommandMediaController.postInit(context, cmd);
        btnCommandEndProgramController.postInit(context, cmd);
        btnCommandVolumeDefaultDeviceController.postInit(context, cmd);
        btnCommandVolumeDefaultDeviceAdvancedController.postInit(context, cmd);
        btnCommandVolumeApplicationDeviceToggleController.postInit(context, cmd);
        btnCommandVolumeDefaultDeviceToggleController.postInit(context, cmd);
        btnCommandVolumeDefaultDeviceToggleAdvancedController.postInit(context, cmd);
        btnCommandVolumeProcessMuteController.postInit(context, cmd);
        btnCommandVolumeFocusMuteController.postInit(context, cmd);
        btnCommandVolumeDeviceMuteController.postInit(context, cmd);
        btnCommandObsController.postInit(context, cmd);
        btnCommandVoiceMeeterController.postInit(context, cmd);
        btnCommandProfileController.postInit(context, cmd);

        try {
            initButtonFields();
        } catch (Exception e) {
            log.error("Unable to init fields", e);
        }
    }

    private void initButtonFields() {
        if (cmd == null || cmd.equals(NOOP))
            return;
        fxHelper.selectTabById(root, "btn" + cmd.getClass().getSimpleName());
        fxHelper.selectTabById(root, "btn" + cmd.getClass().getSuperclass().getSimpleName());

        //noinspection unchecked,rawtypes
        ((Consumer) getButtonInitializer().getOrDefault(cmd.getClass(), x -> log.error("No initializer for {}", x))).accept(cmd); // Yuck :(
    }

    public Command determineButtonCommand() {
        return switch (fxHelper.getSelectedTabId(root)) {
            case "btnCommandNoop" -> btnCommandNoopController.buildCommand();
            case "btnCommandKeystroke" -> btnCommandKeystrokeController.buildCommand();
            case "btnCommandShortcut" -> btnCommandShortcutController.buildCommand();
            case "btnCommandMedia" -> btnCommandMediaController.buildCommand();
            case "btnCommandEndProgram" -> btnCommandEndProgramController.buildCommand();
            case "btnCommandVolumeDefaultDevice" -> btnCommandVolumeDefaultDeviceController.buildCommand();
            case "btnCommandVolumeDefaultDeviceToggle" -> btnCommandVolumeDefaultDeviceToggleController.buildCommand();
            case "btnCommandVolumeDefaultDeviceToggleAdvanced" -> btnCommandVolumeDefaultDeviceToggleAdvancedController.buildCommand();
            case "btnCommandVolumeProcessMute" -> btnCommandVolumeProcessMuteController.buildCommand();
            case "btnCommandVolumeFocusMute" -> btnCommandVolumeFocusMuteController.buildCommand();
            case "btnCommandVolumeDeviceMute" -> btnCommandVolumeDeviceMuteController.buildCommand();
            case "btnCommandVolumeDefaultDeviceAdvanced" -> btnCommandVolumeDefaultDeviceAdvancedController.buildCommand();
            case "btnCommandVolumeApplicationDeviceToggle" -> btnCommandVolumeApplicationDeviceToggleController.buildCommand();
            case "btnCommandObs" -> btnCommandObsController.buildCommand();
            case "btnCommandVoiceMeeter" -> btnCommandVoiceMeeterController.buildCommand();
            case "btnCommandProfile" -> btnCommandProfileController.buildCommand();
            default -> NOOP;
        };
    }

    /**
     * This should either be a visitor or a Pattern matching switch (Java 17+)
     */
    private HashMap<Class<? extends Command>, Consumer<?>> getButtonInitializer() {
        var buttonInitializers = new HashMap<Class<? extends Command>, Consumer<?>>(); // Blegh

        buttonInitializers.put(CommandNoOp.class, (CommandNoOp command) -> btnCommandNoopController.initFromCommand(command));
        buttonInitializers.put(CommandKeystroke.class, (CommandKeystroke command) -> btnCommandKeystrokeController.initFromCommand(command));
        buttonInitializers.put(CommandShortcut.class, (CommandShortcut cmd) -> btnCommandShortcutController.initFromCommand(cmd));
        buttonInitializers.put(CommandMedia.class, (CommandMedia cmd) -> btnCommandMediaController.initFromCommand(cmd));
        buttonInitializers.put(CommandEndProgram.class, (CommandEndProgram cmd) -> btnCommandEndProgramController.initFromCommand(cmd));
        buttonInitializers.put(CommandVolumeDefaultDevice.class, (CommandVolumeDefaultDevice cmd) -> btnCommandVolumeDefaultDeviceController.initFromCommand(cmd));
        buttonInitializers.put(CommandVolumeDefaultDeviceToggle.class, (CommandVolumeDefaultDeviceToggle cmd) -> btnCommandVolumeDefaultDeviceToggleController.initFromCommand(cmd));
        buttonInitializers.put(CommandVolumeDefaultDeviceToggleAdvanced.class, (CommandVolumeDefaultDeviceToggleAdvanced cmd) -> btnCommandVolumeDefaultDeviceToggleAdvancedController.initFromCommand(cmd));
        buttonInitializers.put(CommandVolumeApplicationDeviceToggle.class, (CommandVolumeApplicationDeviceToggle cmd) -> btnCommandVolumeApplicationDeviceToggleController.initFromCommand(cmd));
        buttonInitializers.put(CommandVolumeProcessMute.class, (CommandVolumeProcessMute cmd) -> btnCommandVolumeProcessMuteController.initFromCommand(cmd));
        buttonInitializers.put(CommandVolumeFocusMute.class, (CommandVolumeFocusMute cmd) -> btnCommandVolumeFocusMuteController.initFromCommand(cmd));
        buttonInitializers.put(CommandVolumeDeviceMute.class, (CommandVolumeDeviceMute cmd) -> btnCommandVolumeDeviceMuteController.initFromCommand(cmd));
        buttonInitializers.put(CommandVolumeDefaultDeviceAdvanced.class,
                (CommandVolumeDefaultDeviceAdvanced cmd) -> btnCommandVolumeDefaultDeviceAdvancedController.initFromCommand(cmd));
        buttonInitializers.put(CommandObsSetScene.class, (CommandObsSetScene cmd) -> btnCommandObsController.initFromCommand(cmd));
        buttonInitializers.put(CommandObsMuteSource.class, (CommandObsMuteSource cmd) -> btnCommandObsController.initFromCommand(cmd));
        buttonInitializers.put(CommandVoiceMeeterBasicButton.class, (CommandVoiceMeeterBasicButton cmd) -> btnCommandVoiceMeeterController.initFromCommand(cmd));
        buttonInitializers.put(CommandVoiceMeeterAdvancedButton.class, (CommandVoiceMeeterAdvancedButton cmd) -> btnCommandVoiceMeeterController.initFromCommand(cmd));
        buttonInitializers.put(CommandProfile.class, (CommandProfile cmd) -> btnCommandProfileController.initFromCommand(cmd));

        return buttonInitializers;
    }

}
