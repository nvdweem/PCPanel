package com.getpcpanel.ui;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import javax.imageio.ImageIO;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import com.getpcpanel.cpp.AudioSession;
import com.getpcpanel.cpp.ISndCtrl;
import com.getpcpanel.iconextract.IIconService;
import com.getpcpanel.spring.Prototype;

import javafx.application.Application;
import javafx.embed.swing.SwingFXUtils;
import javafx.fxml.FXML;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.FlowPane;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;
import javafx.stage.Modality;
import javafx.stage.Stage;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import one.util.streamex.StreamEx;

@Log4j2
@Component
@Prototype
@RequiredArgsConstructor
public class AppFinderDialog extends Application implements UIInitializer {
    private static final int ICON_SIZE = 90;
    private final ISndCtrl sndCtrl;
    private final IIconService iconService;
    private Stage parentStage;
    private boolean volumeApps;
    private Stage stage;
    @FXML private FlowPane flowPane;
    @FXML private ScrollPane scroll;
    @FXML private TextField filterField;
    private final List<ButtonTitleExe> allProgs = new ArrayList<>();
    private String processName;

    @Override
    public <T> void initUI(T... args) {
        parentStage = getUIArg(Stage.class, args, 0);
        volumeApps = getUIArg(Boolean.class, args, 1, false);
        postInit();
    }

    @Override
    public void start(Stage stage) {
        start(stage, false);
    }

    public void start(Stage stage, boolean andWait) {
        this.stage = stage;
        var scene = new Scene(scroll);
        scene.getStylesheets().add(Objects.requireNonNull(getClass().getResource("/assets/dark_theme.css")).toExternalForm());
        stage.getIcons().add(new Image(Objects.requireNonNull(getClass().getResource("/assets/256x256.png")).toExternalForm()));
        stage.setScene(scene);
        stage.sizeToScene();
        stage.setTitle("Application Finder");
        if (parentStage != null || andWait) {
            stage.initModality(Modality.WINDOW_MODAL);
            stage.initOwner(parentStage);
            stage.showAndWait();
        } else {
            stage.show();
        }
    }

    public String getProcessName() {
        return processName;
    }

    private static BufferedImage resize(BufferedImage img) {
        var tmp = img.getScaledInstance(ICON_SIZE, ICON_SIZE, 4);
        var dimg = new BufferedImage(ICON_SIZE, ICON_SIZE, 2);
        var g2d = dimg.createGraphics();
        g2d.drawImage(tmp, 0, 0, null);
        g2d.dispose();
        return dimg;
    }

    private BufferedImage toBufferedImage(File f) throws IOException {
        BufferedImage bi;
        try {
            bi = iconService.getIconForFile(ICON_SIZE, ICON_SIZE, f);
        } catch (Exception e) {
            return getDefaultImage();
        }
        if (bi == null)
            return getDefaultImage();
        return bi;
    }

    private static BufferedImage getDefaultImage() throws IOException {
        return resize(ImageIO.read(Objects.requireNonNull(AppFinderDialog.class.getResourceAsStream("/assets/DefaultExeIcon.ico"))));
    }

    private ImageView getImage(AudioSession session) throws Exception {
        BufferedImage bi;
        if (session.isSystemSounds()) {
            bi = resize(ImageIO.read(Objects.requireNonNull(getClass().getResourceAsStream("/assets/systemsounds.ico"))));
        } else {
            bi = toBufferedImage(session.executable());
        }
        var writableImage = SwingFXUtils.toFXImage(bi, null);
        return new ImageView(writableImage);
    }

    private List<AudioSession> getProgs() {
        if (volumeApps) {
            return StreamEx.of(sndCtrl.getAllSessions())
                           .remove(as -> StringUtils.isBlank(as.title()) && (as.executable() == null || StringUtils.isBlank(as.executable().toString())))
                           .sorted(Comparator.comparing((AudioSession as) -> StringUtils.defaultString(as.title(), "zzz"))
                                             .thenComparing(as -> as.executable() == null ? "zzz" : as.executable().getName()))
                           .toImmutableList();
        } else {
            return StreamEx.of(sndCtrl.getRunningApplications())
                           .map(f -> new AudioSession(null, 1, f.file(), f.name(), null, 0, false))
                           .distinct(AudioSession::executable)
                           .toList();
        }
    }

    private void postInit() {
        scroll.viewportBoundsProperty().addListener((ov, oldBounds, bounds) -> {
            flowPane.setPrefWidth(bounds.getWidth());
            flowPane.setPrefHeight(bounds.getHeight());
        });
        var font = new Font(18.0D);
        try {
            var apps = getProgs();
            for (var app : apps) {
                var iv = getImage(app);
                var button = new Button(app.title(), iv);
                var size = 180;
                var ivSize = 64;
                iv.minHeight(ivSize);
                iv.minWidth(ivSize);
                iv.maxHeight(ivSize);
                iv.maxWidth(ivSize);
                button.setMinSize(size, size);
                button.setMaxSize(size, size);
                button.setGraphicTextGap(10.0D);
                button.setFont(font);
                button.setWrapText(true);
                button.setTextAlignment(TextAlignment.CENTER);
                button.setContentDisplay(ContentDisplay.TOP);
                button.setOnAction(a -> {
                    processName = app.isSystemSounds() ? AudioSession.SYSTEM : app.executable().getName();
                    stage.close();
                });
                allProgs.add(new ButtonTitleExe(button, app.title(), app.executable().getName()));
            }
            StreamEx.of(allProgs).map(ButtonTitleExe::button).toListAndThen(flowPane.getChildren()::addAll);
        } catch (Exception e) {
            log.error("Unable to add app-buttons", e);
        }
    }

    @FXML
    private void onFilterChanged(KeyEvent event) {
        var filter = filterField.getText();
        flowPane.getChildren().clear();
        StreamEx.of(allProgs).filter(b -> b.matches(filter)).map(ButtonTitleExe::button).toListAndThen(flowPane.getChildren()::addAll);
    }

    private record ButtonTitleExe(Button button, String title, String exe) {
        boolean matches(String filter) {
            return StringUtils.containsIgnoreCase(title, filter) || StringUtils.containsIgnoreCase(exe, filter);
        }
    }
}
