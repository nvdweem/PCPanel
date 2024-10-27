package com.getpcpanel.ui.command;

import java.util.Objects;
import java.util.Optional;

import javax.annotation.Nonnull;

import org.apache.commons.lang3.math.NumberUtils;
import org.springframework.stereotype.Component;

import com.getpcpanel.MainFX;
import com.getpcpanel.commands.command.CommandBrightness;
import com.getpcpanel.commands.command.CommandNoOp;
import com.getpcpanel.commands.command.DialAction.DialCommandParams;
import com.getpcpanel.hid.DialValueCalculator;
import com.getpcpanel.profile.KnobSetting;
import com.getpcpanel.spring.Prototype;
import com.getpcpanel.ui.FxHelper;
import com.getpcpanel.ui.UIHelper;
import com.getpcpanel.ui.UIInitializer;
import com.getpcpanel.ui.graphviewer.GraphViewer;

import javafx.application.Application;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Scene;
import javafx.scene.control.CheckBox;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import lombok.SneakyThrows;

@Component
@Prototype
public class DialCutoffOptions extends Application implements UIInitializer<DialCutoffOptions.DialCutoffOptionsParams> {
    @FXML private CheckBox invert;
    @FXML private VBox panel;
    @FXML private TextField moveStart;
    @FXML private TextField moveEnd;
    @FXML private HBox chartholder;
    private Stage stage;
    private Stage parentStage;
    private boolean okClicked;
    private GraphViewer graph;

    @SneakyThrows
    public static Optional<DialCommandParams> show(DialCutoffOptionsParams params) {
        var res = MainFX.getBean(FxHelper.class).open(DialCutoffOptions.class, params);
        var afdStage = new Stage();
        res.start(afdStage);

        if (res.okClicked) {
            return Optional.of(res.buildResult());
        }
        return Optional.empty();
    }

    @Override
    public void initUI(@Nonnull DialCutoffOptionsParams args) {
        parentStage = args.parentStage;
        if (parentStage == null) {
            throw new IllegalStateException("Parent stage is required");
        }
        setInitialFieldValues(args.params);

        graph = new GraphViewer(new DialValueCalculator(new KnobSetting()), new CommandNoOp());
        graph.setPrefSize(400, 100);
        chartholder.getChildren().add(graph);
        keepGraphUpToDate();
    }

    private void keepGraphUpToDate() {
        moveStart.textProperty().addListener((observable, oldValue, newValue) -> updateGraph());
        moveEnd.textProperty().addListener((observable, oldValue, newValue) -> updateGraph());
        invert.selectedProperty().addListener((observable, oldValue, newValue) -> updateGraph());
        updateGraph();
    }

    private void updateGraph() {
        graph.setCmd(new CommandBrightness(buildResult()));
        graph.redraw();
    }

    private void setInitialFieldValues(DialCommandParams originalArgs) {
        invert.setSelected(originalArgs.invert());

        if (originalArgs.moveStart() != null) {
            moveStart.setText(String.valueOf(originalArgs.moveStart()));
        }
        if (originalArgs.moveEnd() != null) {
            moveEnd.setText(String.valueOf(originalArgs.moveEnd()));
        }
    }

    @Override
    public void start(Stage stage) throws Exception {
        this.stage = stage;
        UIHelper.closeOnEscape(stage);
        var scene = new Scene(panel);
        scene.getStylesheets().add(Objects.requireNonNull(getClass().getResource("/assets/dark_theme.css")).toExternalForm());
        stage.getIcons().add(new Image(Objects.requireNonNull(getClass().getResource("/assets/256x256.png")).toExternalForm()));
        stage.setScene(scene);
        stage.setTitle("Volume options");

        stage.initModality(Modality.WINDOW_MODAL);
        stage.initOwner(parentStage);
        stage.showAndWait();
    }

    @Nonnull
    private DialCommandParams buildResult() {
        var moveStartVal = NumberUtils.toInt(moveStart.getText(), 0);
        var moveEndVal = NumberUtils.toInt(moveEnd.getText(), 0);
        return new DialCommandParams(invert.isSelected(), moveStartVal, moveEndVal);
    }

    public void ok(ActionEvent actionEvent) {
        okClicked = true;
        stage.close();
    }

    public void closeButtonAction(ActionEvent actionEvent) {
        stage.close();
    }

    public record DialCutoffOptionsParams(Stage parentStage, DialCommandParams params) {
        public DialCutoffOptionsParams {
            if (params == null) {
                params = DialCommandParams.DEFAULT;
            }
        }
    }
}
