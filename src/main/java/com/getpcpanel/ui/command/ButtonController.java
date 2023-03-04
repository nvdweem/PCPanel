package com.getpcpanel.ui.command;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.springframework.stereotype.Component;
import org.springframework.util.ReflectionUtils;

import com.getpcpanel.commands.Commands;
import com.getpcpanel.commands.command.Command;
import com.getpcpanel.commands.command.CommandNoOp;
import com.getpcpanel.commands.command.DialAction;
import com.getpcpanel.spring.Prototype;
import com.getpcpanel.ui.FxHelper;
import com.getpcpanel.ui.MacroControllerService;
import com.getpcpanel.ui.MacroControllerService.ControllerInfo;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.control.Accordion;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TabPane;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import one.util.streamex.StreamEx;

@Log4j2
@Component
@Prototype
@RequiredArgsConstructor
public class ButtonController {
    private final MacroControllerService macroControllerService;
    private final FxHelper fxHelper;

    private Cmd.Type cmdType;
    private CommandContext context;
    private final ContextMenu addMenu = new ContextMenu();
    @FXML @Getter private TabPane root;

    @FXML private Accordion commands;
    @FXML private Button addButton;

    public void initController(Cmd.Type cmdType, CommandContext context, @Nullable Commands buttonData) {
        this.cmdType = cmdType;
        this.context = context;
        Commands.cmds(buttonData).forEach(this::add);

        commands.expandedPaneProperty().addListener((property, oldPane, newPane) -> {
            if (oldPane != null) {
                oldPane.setCollapsible(true);
            }
            if (newPane != null) {
                Platform.runLater(() -> newPane.setCollapsible(false));
            }
        });

        var panes = commands.getPanes();
        if (!panes.isEmpty()) {
            commands.setExpandedPane(panes.get(0));
        }

        macroControllerService.getControllersForType(cmdType).forEach(ctrlr -> {
            if (!isEnabled(ctrlr)) {
                return;
            }

            var menuItem = new MenuItem(ctrlr.cmd().name());
            menuItem.setOnAction(ignored -> {
                add(ctrlr, null);
                commands.setExpandedPane(panes.get(panes.size() - 1));
            });
            addMenu.getItems().add(menuItem);
        });
        addButton.setContextMenu(addMenu);
    }

    @SneakyThrows
    private static boolean isEnabled(@Nonnull ControllerInfo ctrlr) {
        return ReflectionUtils.accessibleConstructor(ctrlr.cmd().enabled()).newInstance().isEnabled();
    }

    private void add(Command cmd) {
        var controllerInfo = macroControllerService.getControllerForCommand(cmd.getClass());
        if (controllerInfo == null) {
            log.warn("Dial/button with {} found, but that command is not supported", cmd);
            return;
        }
        add(controllerInfo, cmd);
    }

    @SneakyThrows
    private void add(@Nonnull ControllerInfo info, @Nullable Command cmd) {
        var loader = fxHelper.getLoader(info.getFxml());
        var loaded = loader.<Node>load();
        var controller = loader.<CommandController<Command>>getController();
        controller.postInit(context);
        HBox.setHgrow(loaded, Priority.ALWAYS);

        var pane = new TitledPane(null, loaded);
        var invertBox = addCloseButtonAndInvert(pane, info.cmd().name(), cmd);
        pane.setUserData(new PanelData(controller, invertBox));
        commands.getPanes().add(pane);

        if (cmd != null) {
            controller.initFromCommand(cmd);
        }
    }

    private CheckBox addCloseButtonAndInvert(@Nonnull TitledPane pane, String title, @Nullable Command cmd) {
        var borderPane = new BorderPane();
        var titleOfTitledPane = new Label(title);
        var buttonClose = new Button("X");
        var invertCheck = new CheckBox("Invert");
        HBox.setMargin(invertCheck, new Insets(3, 20, 0, 0));
        var topRight = cmdType == Cmd.Type.dial ? new HBox(invertCheck, buttonClose) : buttonClose;
        if (cmd instanceof DialAction) {
            invertCheck.setSelected(((DialAction) cmd).isInvert());
        }

        borderPane.setLeft(titleOfTitledPane);
        BorderPane.setAlignment(titleOfTitledPane, Pos.CENTER_LEFT);
        borderPane.setRight(topRight);
        borderPane.prefWidthProperty().bind(commands.widthProperty().subtract(40));
        pane.setGraphic(borderPane);

        buttonClose.setOnAction(event -> commands.getPanes().remove(pane));

        return invertCheck;
    }

    public Commands determineButtonCommand() {
        var userdata = StreamEx.of(commands.getPanes()).map(Node::getUserData);

        if (cmdType == Cmd.Type.dial) {
            userdata = userdata.select(PanelData.class)
                               .mapToEntry(PanelData::controller, PanelData::invertBox)
                               .selectKeys(DialCommandController.class)
                               .mapKeyValue((dcc, invertBox) -> dcc.buildCommand(invertBox.isSelected()));
        } else {
            userdata = userdata.select(PanelData.class).map(PanelData::controller).select(ButtonCommandController.class).map(ButtonCommandController::buildCommand);
        }

        var cmds = userdata.select(Command.class).remove(CommandNoOp.class::isInstance).toList();
        return new Commands(cmds);
    }

    public void showActionsMenu(ActionEvent ignored) {
        addMenu.show(addButton, Side.TOP, 0, 0);
    }

    private record PanelData(CommandController<?> controller, CheckBox invertBox) {
    }
}
