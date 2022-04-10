package main;

import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;
import java.util.Scanner;

import javax.imageio.ImageIO;

import javafx.application.Application;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.embed.swing.SwingFXUtils;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.geometry.Bounds;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.FlowPane;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;
import javafx.stage.Modality;
import javafx.stage.Stage;
import me.marnic.jiconextract2.JIconExtract;

public class AppFinderDialog extends Application implements Initializable {
    private Scene scene;

    private Stage stage;

    @FXML
    FlowPane flowPane;

    @FXML
    ScrollPane scroll;

    private ScrollPane pane;

    private String processName;

    private Stage parentStage;

    private static final int ICON_SIZE = 90;

    public AppFinderDialog(Stage parentStage) {
        this.parentStage = parentStage;
    }

    public AppFinderDialog() {
    }

    @Override
    public void start(Stage stage) {
        this.stage = stage;
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/assets/AppFinderDialog.fxml"));
        loader.setController(this);
        try {
            pane = loader.load();
        } catch (IOException e) {
            e.printStackTrace();
        }
        scene = new Scene(pane);
        scene.getStylesheets().add(getClass().getResource("/assets/dark_theme.css").toExternalForm());
        stage.getIcons().add(new javafx.scene.image.Image("/assets/256x256.png"));
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
        Image tmp = img.getScaledInstance(newW, newH, 4);
        BufferedImage dimg = new BufferedImage(newW, newH, 2);
        Graphics2D g2d = dimg.createGraphics();
        g2d.drawImage(tmp, 0, 0, null);
        g2d.dispose();
        return dimg;
    }

    private static BufferedImage toBufferedImage(File f) throws IOException {
        BufferedImage bi = null;
        try {
            bi = JIconExtract.getIconForFile(90, 90, f);
        } catch (Exception e) {
            return getDefaultImage();
        }
        if (bi == null)
            return getDefaultImage();
        return bi;
    }

    private static BufferedImage getDefaultImage() throws IOException {
        return resize(ImageIO.read(AppFinderDialog.class.getResourceAsStream("/assets/DefaultExeIcon.ico")), 90, 90);
    }

    private ImageView getImage(App app) throws Exception {
        BufferedImage bi;
        if (app.processName.equals("ShellExperienceHost.exe")) {
            app.displayName = "System Sounds";
            bi = resize(ImageIO.read(getClass().getResourceAsStream("/assets/systemsounds.ico")), 90, 90);
        } else {
            bi = toBufferedImage(app.exeLocation);
        }
        WritableImage writableImage = SwingFXUtils.toFXImage(bi, null);
        return new ImageView(writableImage);
    }

    static class App {
        private final String processName;

        private String displayName;

        private final File exeLocation;

        public App(String processName, String displayName, String exeLocation) {
            this.processName = processName;
            this.displayName = displayName;
            this.exeLocation = new File(exeLocation);
        }

        public String toString() {
            return "App [processName=" + processName + ", displayName=" + displayName + ", exeLocation=" + exeLocation + "]\n";
        }
    }

    private static List<App> getProgs() throws Exception {
        List<App> ret = new ArrayList<>();
        File program = new File("sndctrl.exe");
        ProcessBuilder c = new ProcessBuilder(program.toString(), "listapps");
        Process sndctrlProc = c.start();
        InputStream in = sndctrlProc.getInputStream();
        Scanner scan = new Scanner(in);
        try {
            String x;
            while (!(x = scan.nextLine()).startsWith("Elapsed Milliseconds : "))
                ret.add(new App(x, scan.nextLine(), scan.nextLine()));
        } catch (Exception e) {
            e.printStackTrace();
        }
        scan.close();
        in.close();
        sndctrlProc.destroy();
        return ret;
    }

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        scroll.viewportBoundsProperty().addListener(new ChangeListener<Bounds>() {
            @Override
            public void changed(ObservableValue<? extends Bounds> ov, Bounds oldBounds, Bounds bounds) {
                flowPane.setPrefWidth(bounds.getWidth());
                flowPane.setPrefHeight(bounds.getHeight());
            }
        });
        Font font = new Font(18.0D);
        try {
            List<App> apps = getProgs();
            for (App app : apps) {
                ImageView iv = getImage(app);
                Button button = new Button(app.displayName, iv);
                int size = 180, ivSize = 64;
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
                    processName = app.processName;
                    stage.close();
                });
                flowPane.getChildren().add(button);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
