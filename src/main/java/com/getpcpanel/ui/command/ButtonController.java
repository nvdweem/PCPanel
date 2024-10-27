package com.getpcpanel.ui.command;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.ReflectionUtils;

import com.getpcpanel.commands.Commands;
import com.getpcpanel.commands.CommandsType;
import com.getpcpanel.commands.command.Command;
import com.getpcpanel.commands.command.CommandNoOp;
import com.getpcpanel.commands.command.DialAction;
import com.getpcpanel.commands.command.DialAction.DialCommandParams;
import com.getpcpanel.hid.DialValueCalculator;
import com.getpcpanel.profile.KnobSetting;
import com.getpcpanel.spring.Prototype;
import com.getpcpanel.ui.FxHelper;
import com.getpcpanel.ui.MacroControllerService;
import com.getpcpanel.ui.MacroControllerService.ControllerInfo;
import com.getpcpanel.ui.graphviewer.GraphViewer;
import com.getpcpanel.util.Images;

import javafx.beans.binding.Bindings;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ObservableValue;
import javafx.collections.ListChangeListener;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.control.Accordion;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.shape.SVGPath;
import javafx.stage.Stage;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
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
    @FXML private ChoiceBox<CommandsType> commandsType;
    @Setter private Stage stage;

    public void initController(Cmd.Type cmdType, CommandContext context, @Nullable Commands buttonData) {
        this.cmdType = cmdType;
        this.context = context;
        Commands.cmds(buttonData).forEach(this::add);

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

        commandsType.getItems().addAll(CommandsType.values());
        if (buttonData != null) {
            commandsType.getSelectionModel().select(buttonData.getType());
        }

        if (cmdType == Cmd.Type.button) {
            commands.getPanes().addListener(this::determineCommandsTypeVisible);
            determineCommandsTypeVisible(null);
        }
    }

    private void determineCommandsTypeVisible(@Nullable ListChangeListener.Change<?> change) {
        commandsType.setVisible(commands.getPanes().size() > 1);
        if (!commandsType.isVisible()) {
            commandsType.getSelectionModel().selectFirst();
        }
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
        var panelData = addPanelOptions(controller, pane, info.cmd().name(), cmd);
        pane.setUserData(panelData);
        commands.getPanes().add(pane);

        if (cmd != null) {
            controller.initFromCommand(cmd);
        }
    }

    private PanelData addPanelOptions(CommandController<Command> controller, @Nonnull TitledPane pane, String title, @Nullable Command cmd) {
        // Labels
        var titleOfTitledPane = new Label(title);
        var additionalLabel = buildAdditionalLabel(controller, pane);
        var labelsBox = new HBox(titleOfTitledPane, additionalLabel);
        labelsBox.setAlignment(Pos.CENTER_LEFT);

        // Buttons
        var buttonDelete = deleteButton(pane);
        var buttonUp = moveButton(pane, Images.chevronUp(), -1);
        var buttonDown = moveButton(pane, Images.chevronDown(), 1);
        var buttonCopy = copyButton(pane);

        var hbox = new HBox(buttonCopy, buttonUp, buttonDown, buttonDelete);
        var result = new PanelData(controller, cmd instanceof DialAction da ? da.getDialParams() : null);

        if (cmd instanceof DialAction da) {
            var invertCheck = addInvertCheck(result, hbox, da);
            addGraphViewer(result, controller, cmd, hbox, invertCheck);
        }

        var borderPane = new BorderPane();
        borderPane.setLeft(labelsBox);
        BorderPane.setAlignment(titleOfTitledPane, Pos.CENTER_LEFT);
        borderPane.setRight(hbox);
        borderPane.prefWidthProperty().bind(commands.widthProperty().subtract(40));
        pane.setGraphic(borderPane);

        return result;
    }

    private CheckBox addInvertCheck(PanelData result, HBox target, DialAction da) {
        var invertCheck = new CheckBox("Invert");
        HBox.setMargin(invertCheck, new Insets(3, 20, 0, 0));

        invertCheck.setSelected(da.isInvert());
        invertCheck.selectedProperty().addListener((obs, old, newValue) -> result.setParams(result.params.withInvert(newValue)));

        target.getChildren().add(0, invertCheck);
        return invertCheck;
    }

    private void addGraphViewer(PanelData panelData, CommandController<Command> controller, @Nonnull Command cmd, HBox hbox, CheckBox invertCheck) {
        if (!(cmd instanceof DialAction) || !(controller instanceof DialCommandController<?> dc)) {
            return;
        }

        var graphViewer = new GraphViewer(new DialValueCalculator(new KnobSetting()), cmd);
        graphViewer.setPrefSize(75, 28);

        var graphBox = new HBox(graphViewer);
        HBox.setMargin(graphBox, new Insets(0, 10, 0, 0));
        hbox.getChildren().add(0, graphBox);
        graphViewer.setOnAction(event -> {
            var newParams = DialCutoffOptions.show(new DialCutoffOptions.DialCutoffOptionsParams(stage, panelData.params));
            newParams.ifPresent(params -> {
                panelData.setParams(params);
                invertCheck.setSelected(params.invert());
                graphViewer.setCmd(dc.buildCommand(params));
                graphViewer.redraw();
            });
        });

        invertCheck.selectedProperty().addListener((obs, old, newValue) -> {
            graphViewer.setCmd(dc.buildCommand(panelData.params));
            graphViewer.redraw();
        });

        panelData.setGraph(graphViewer);
    }

    public void setupGraphRenderer(TextField trimMin, TextField trimMax, CheckBox logarithmic) {
        trimMin.textProperty().addListener((obs, old, newValue) -> doUpdateGraphRenderer(trimMin.getText(), trimMax.getText(), logarithmic.isSelected()));
        trimMax.textProperty().addListener((obs, old, newValue) -> doUpdateGraphRenderer(trimMin.getText(), trimMax.getText(), logarithmic.isSelected()));
        logarithmic.selectedProperty().addListener((obs, old, newValue) -> doUpdateGraphRenderer(trimMin.getText(), trimMax.getText(), logarithmic.isSelected()));
    }

    private void doUpdateGraphRenderer(String trimMinStr, String trimMaxStr, boolean logarithmic) {
        var trimMin = NumberUtils.toInt(trimMinStr, 0);
        var trimMax = NumberUtils.toInt(trimMaxStr, 100);

        var knobSettings = new KnobSetting().setMinTrim(trimMin).setMaxTrim(trimMax).setLogarithmic(logarithmic);
        StreamEx.of(commands.getPanes())
                .map(Node::getUserData)
                .select(PanelData.class)
                .map(PanelData::getGraph)
                .nonNull()
                .forEach(graph -> {
                    graph.setCalculator(new DialValueCalculator(knobSettings));
                    graph.redraw();
                });
    }

    private @Nonnull Label buildAdditionalLabel(@Nonnull CommandController<Command> controller, @Nonnull TitledPane pane) {
        var additionalLabel = new Label();
        additionalLabel.setStyle("-fx-text-fill: #999;");
        additionalLabel.textProperty().bind(appendSemiColonBinding(controller.additionalLabelText()));

        // Hide when not needed
        var showProperty = Bindings.notEqual(pane, commands.expandedPaneProperty());
        additionalLabel.visibleProperty().bind(showProperty);
        additionalLabel.managedProperty().bind(showProperty);
        return additionalLabel;
    }

    private ObservableValue<String> appendSemiColonBinding(StringProperty stringProperty) {
        // TODO: Make this a map call when using JavaFX 19
        // stringProperty.map(v -> StringUtils.isBlank(v) ? "" : ": " + v);
        return Bindings.createStringBinding(() -> StringUtils.isBlank(stringProperty.get()) ? "" : ": " + stringProperty.get(), stringProperty);
    }

    private Button deleteButton(@Nonnull TitledPane pane) {
        var buttonDelete = createButton(Images.delete());
        buttonDelete.setOnAction(event -> commands.getPanes().remove(pane));
        return buttonDelete;
    }

    private Button moveButton(@Nonnull TitledPane pane, SVGPath image, int idxChange) {
        var buttonMove = createButton(image);
        commands.getPanes().addListener((ListChangeListener.Change<?> c) -> showHideButton(buttonMove, commands.getPanes().size() > 1));
        buttonMove.setOnAction(event -> {
            var idx = commands.getPanes().indexOf(pane);
            commands.getPanes().remove(pane);

            var newIdx = Math.max(0, Math.min(commands.getPanes().size(), idx + idxChange));
            commands.getPanes().add(newIdx, pane);
        });
        return buttonMove;
    }

    private Button copyButton(TitledPane pane) {
        var buttonCopy = createButton(Images.copy());
        buttonCopy.setOnAction(event -> {
            var data = (PanelData) pane.getUserData();
            if (data.controller instanceof DialCommandController<?> dc) {
                add(dc.buildCommand(data.params));
            } else if (data.controller instanceof ButtonCommandController<?> bc) {
                add(bc.buildCommand());
            }
            commands.setExpandedPane(commands.getPanes().get(commands.getPanes().size() - 1));

        });
        return buttonCopy;
    }

    private Button createButton(SVGPath image) {
        var result = new Button("", image);
        result.setStyle("-fx-background-color: transparent;");
        return result;
    }

    private void showHideButton(Button btn, boolean visible) {
        btn.setVisible(visible);
        btn.setManaged(visible);
    }

    public Commands determineButtonCommand() {
        var userdata = StreamEx.of(commands.getPanes()).map(Node::getUserData);

        if (cmdType == Cmd.Type.dial) {
            userdata = userdata.select(PanelData.class)
                               .mapToEntry(PanelData::getController, PanelData::getParams)
                               .selectKeys(DialCommandController.class)
                               .mapKeyValue(DialCommandController::buildCommand);
        } else {
            userdata = userdata.select(PanelData.class).map(PanelData::getController).select(ButtonCommandController.class).map(ButtonCommandController::buildCommand);
        }

        var cmds = userdata.select(Command.class).remove(CommandNoOp.class::isInstance).toList();
        return new Commands(cmds, commandsType.getValue());
    }

    public void showActionsMenu(ActionEvent ignored) {
        addMenu.show(addButton, Side.TOP, 0, 0);
    }

    @Getter
    private static class PanelData {
        private final CommandController<?> controller;

        @Setter private GraphViewer graph;
        @Setter private DialCommandParams params;

        public PanelData(CommandController<?> controller, @Nullable DialCommandParams params) {
            this.controller = controller;
            this.params = params == null ? DialCommandParams.DEFAULT : params;
        }
    }
}
