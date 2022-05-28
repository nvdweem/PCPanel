package com.getpcpanel.ui;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.ResourceBundle;

import javax.imageio.ImageIO;

import com.getpcpanel.cpp.AudioSession;
import com.getpcpanel.cpp.SndCtrl;

import javafx.application.Application;
import javafx.embed.swing.SwingFXUtils;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.FlowPane;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;
import javafx.stage.Modality;
import javafx.stage.Stage;
import lombok.extern.log4j.Log4j2;
import me.marnic.jiconextract2.JIconExtract;
import one.util.streamex.StreamEx;

@Log4j2
class AppFinderDialog extends Application implements Initializable {
    private Stage stage;
    @FXML private FlowPane flowPane;
    @FXML private ScrollPane scroll;
    private ScrollPane pane;
    private String processName;
    private final Stage parentStage;
    private final boolean volumeApps;
    private static final int ICON_SIZE = 90;

    public AppFinderDialog(Stage parentStage, boolean volumeApps) {
        this.parentStage = parentStage;
        this.volumeApps = volumeApps;
    }

    @Override
    public void start(Stage stage) {
        this.stage = stage;
        var loader = new FXMLLoader(getClass().getResource("/assets/AppFinderDialog.fxml"));
        loader.setController(this);
        try {
            pane = loader.load();
        } catch (IOException e) {
            log.error("Unable to load loader", e);
        }
        var scene = new Scene(pane);
        scene.getStylesheets().add(Objects.requireNonNull(getClass().getResource("/assets/dark_theme.css")).toExternalForm());
        stage.getIcons().add(new Image(Objects.requireNonNull(getClass().getResource("/assets/256x256.png")).toExternalForm()));
        stage.setScene(scene);
        stage.sizeToScene();
        stage.setTitle("Application Finder");
        if (parentStage != null) {
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

    public static BufferedImage resize(BufferedImage img, int newW, int newH) {
        var tmp = img.getScaledInstance(newW, newH, 4);
        var dimg = new BufferedImage(newW, newH, 2);
        var g2d = dimg.createGraphics();
        g2d.drawImage(tmp, 0, 0, null);
        g2d.dispose();
        return dimg;
    }

    private static BufferedImage toBufferedImage(File f) throws IOException {
        BufferedImage bi;
        try {
            bi = JIconExtract.getIconForFile(ICON_SIZE, ICON_SIZE, f);
        } catch (Exception e) {
            return getDefaultImage();
        }
        if (bi == null)
            return getDefaultImage();
        return bi;
    }

    private static BufferedImage getDefaultImage() throws IOException {
        return resize(ImageIO.read(Objects.requireNonNull(AppFinderDialog.class.getResourceAsStream("/assets/DefaultExeIcon.ico"))), ICON_SIZE, ICON_SIZE);
    }

    private ImageView getImage(AudioSession session) throws Exception {
        BufferedImage bi;
        if (session.pid() == 0) {
            bi = resize(ImageIO.read(Objects.requireNonNull(getClass().getResourceAsStream("/assets/systemsounds.ico"))), ICON_SIZE, ICON_SIZE);
        } else {
            bi = toBufferedImage(session.executable());
        }
        var writableImage = SwingFXUtils.toFXImage(bi, null);
        return new ImageView(writableImage);
    }

    private List<AudioSession> getProgs() {
        if (volumeApps) {
            return StreamEx.of(SndCtrl.getDevices()).flatCollection(ad -> ad.getSessions().values())
                           .distinct(AudioSession::pid)
                           .sorted(Comparator.nullsLast(Comparator.comparing(AudioSession::title).thenComparing(AudioSession::executable)))
                           .toImmutableList();
        } else {
            return StreamEx.of(SndCtrl.getRunningApplications()).map(f -> new AudioSession(null, 1, f, f.getName(), null, 0, false)).toList();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
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
                    processName = app.pid() == 0 ? AudioSession.SYSTEM : app.executable().getName();
                    stage.close();
                });
                flowPane.getChildren().add(button);
            }
        } catch (Exception e) {
            log.error("Unable to add app-buttons", e);
        }
    }
}
