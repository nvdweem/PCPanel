package com.getpcpanel.ui.command;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.springframework.stereotype.Component;

import com.getpcpanel.commands.Commands;
import com.getpcpanel.commands.command.Command;
import com.getpcpanel.commands.command.CommandNoOp;
import com.getpcpanel.spring.Prototype;
import com.getpcpanel.ui.FxHelper;
import com.getpcpanel.ui.MacroControllerService;
import com.getpcpanel.ui.MacroControllerService.ControllerInfo;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.control.Accordion;
import javafx.scene.control.Button;
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

    private CommandContext context;
    private final ContextMenu addMenu = new ContextMenu();
    @FXML @Getter private TabPane root;

    @FXML private Accordion commands;
    @FXML private Button addButton;

    public void initController(Cmd.Type cmdType, CommandContext context, @Nullable Commands buttonData) {
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
            var menuItem = new MenuItem(ctrlr.cmd().name());
            menuItem.setOnAction(ignored -> {
                add(ctrlr);
                commands.setExpandedPane(panes.get(panes.size() - 1));
            });
            addMenu.getItems().add(menuItem);
        });
        addButton.setContextMenu(addMenu);
    }

    private void add(Command cmd) {
        var controllerInfo = macroControllerService.getControllerForCommand(cmd.getClass());
        var controller = add(controllerInfo);
        controller.initFromCommand(cmd);
    }

    @SneakyThrows
    private CommandController<? super Command> add(@Nonnull ControllerInfo info) {
        var loader = fxHelper.getLoader(info.getFxml());
        var loaded = loader.<Node>load();
        var controller = loader.<CommandController<Command>>getController();
        controller.postInit(context);
        HBox.setHgrow(loaded, Priority.ALWAYS);

        var pane = new TitledPane(null, loaded);
        addCloseButton(pane, info.cmd().name());
        pane.setUserData(controller);
        commands.getPanes().add(pane);
        return controller;
    }

    private void addCloseButton(@Nonnull TitledPane pane, String title) {
        var borderPane = new BorderPane();
        var titleOfTitledPane = new Label(title);
        var buttonClose = new Button("X");
        borderPane.setLeft(titleOfTitledPane);
        BorderPane.setAlignment(titleOfTitledPane, Pos.CENTER_LEFT);
        borderPane.setRight(buttonClose);
        borderPane.prefWidthProperty().bind(commands.widthProperty().subtract(40));
        pane.setGraphic(borderPane);

        buttonClose.setOnAction(event -> commands.getPanes().remove(pane));
    }

    public Commands determineButtonCommand() {
        var cmds = StreamEx.of(commands.getPanes())
                           .map(Node::getUserData).select(CommandController.class)
                           .map(CommandController::buildCommand)
                           .remove(CommandNoOp.class::isInstance)
                           .toList();
        return new Commands(cmds);
    }

    public void showActionsMenu(ActionEvent ignored) {
        addMenu.show(addButton, Side.TOP, 0, 0);
    }
}
