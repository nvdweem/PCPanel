package main;

import java.io.IOException;

import com.sun.javafx.webkit.Accessor;

import hid.InputInterpreter;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.scene.shape.Shape;
import javafx.scene.text.Font;
import javafx.scene.web.WebView;
import javafx.stage.Stage;
import save.DeviceSave;
import save.LightingConfig;
import save.LightingConfig.LightingMode;
import util.Util;

public class PCPanelRGBUI extends Device {
    private static final int KNOB_COUNT = 4;

    @FXML
    private Pane lightPanes;

    @FXML
    private Pane panelPane;

    @FXML
    private WebView webview;

    private Label label;

    private Button lightingButton;

    private final Button[] knobs = new Button[4];

    private static final Image previewImage = new Image(PCPanelRGBUI.class.getResourceAsStream("/assets/PCPanelRGB/preview.png"));

    private Stage childDialogStage;

    public PCPanelRGBUI(String serialNum, DeviceSave deviceSave) {
        super(serialNum, deviceSave);
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/assets/PCPanelRGB/PCPanelRGB.fxml"));
        loader.setController(this);
        try {
            Pane pane = loader.load();
            initBox();
            initButtons();
            initLabel();
            initLightingButton();
            pane.getStylesheets().addAll(getClass().getResource("/assets/PCPanelRGB/PCPanelRGB.css").toExternalForm());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public Node getLabel() {
        return label;
    }

    @Override
    public Pane getDevicePane() {
        return panelPane;
    }

    private void rotateKnob(int knob, int val) {
        ((Region) knobs[knob].getGraphic()).getChildrenUnmodifiable().get(3).setRotate(Util.analogValueToRotation(val));
    }

    private void initLabel() {
        label = new Label("PCPANEL RGB");
        Font f = Font.loadFont(getClass().getResourceAsStream("/assets/apex-mk2.regular.otf"), 50.0D);
        label.setFont(f);
        label.setUnderline(true);
        label.setTextFill(Paint.valueOf("white"));
    }

    private void initBox() {
        webview.setPrefSize(600.0D, 170.0D);
        webview.setMaxSize(600.0D, 170.0D);
        webview.getEngine()
               .loadContent(
                       "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 344.3 88.4\"><defs><style>.cls-1{fill:#25262a;}.cls-2{fill:#868993;}.cls-3{fill:#3b3c41;}.cls-4{fill:#1c1d20;}</style></defs><g id=\"Layer_2\" data-name=\"Layer 2\"><g id=\"Layer_1-2\" data-name=\"Layer 1\"><path class=\"cls-1\" d=\"M0,17.5V88.4H289.1V17.5ZM49.3,82.8A26.9,26.9,0,1,1,76.2,55.9,26.91,26.91,0,0,1,49.3,82.8Zm63.4,0a26.9,26.9,0,1,1,26.9-26.9h0A27,27,0,0,1,112.7,82.8Zm63.3,0a26.9,26.9,0,1,1,26.9-26.9h0A26.91,26.91,0,0,1,176,82.8Zm63.4,0a26.9,26.9,0,1,1,26.9-26.9h0a27,27,0,0,1-26.9,26.9Z\"/><path class=\"cls-2\" d=\"M49.3,24.5A31.21,31.21,0,0,0,19.7,45.7l1.7.6a29.47,29.47,0,0,1,55.8,0l1.7-.6A31.34,31.34,0,0,0,49.3,24.5Z\"/><rect class=\"cls-2\" x=\"19.7\" y=\"25.6\" width=\"6.3\" height=\"1.4\"/><polygon class=\"cls-2\" points=\"79.4 25.6 76.9 25.6 76.9 23.2 75.5 23.2 75.5 25.6 73.1 25.6 73.1 27 75.5 27 75.5 29.5 76.9 29.5 76.9 27 79.4 27 79.4 25.6\"/><path class=\"cls-2\" d=\"M112.7,24.5A31.43,31.43,0,0,0,83,45.7l1.7.6a29.47,29.47,0,0,1,55.8,0l1.7-.6A31.31,31.31,0,0,0,112.7,24.5Z\"/><rect class=\"cls-2\" x=\"83\" y=\"25.6\" width=\"6.3\" height=\"1.4\"/><polygon class=\"cls-2\" points=\"142.8 25.6 140.3 25.6 140.3 23.2 138.9 23.2 138.9 25.6 136.4 25.6 136.4 27 138.9 27 138.9 29.5 140.3 29.5 140.3 27 142.8 27 142.8 25.6\"/><path class=\"cls-2\" d=\"M176,24.5a31.21,31.21,0,0,0-29.6,21.2l1.7.6a29.47,29.47,0,0,1,55.8,0l1.7-.6A31.34,31.34,0,0,0,176,24.5Z\"/><rect class=\"cls-2\" x=\"146.4\" y=\"25.6\" width=\"6.3\" height=\"1.4\"/><polygon class=\"cls-2\" points=\"206.1 25.6 203.6 25.6 203.6 23.2 202.2 23.2 202.2 25.6 199.8 25.6 199.8 27 202.2 27 202.2 29.5 203.6 29.5 203.6 27 206.1 27 206.1 25.6\"/><path class=\"cls-2\" d=\"M239.4,24.5a31.21,31.21,0,0,0-29.6,21.2l1.7.6a29.47,29.47,0,0,1,55.8,0l1.7-.6A31.34,31.34,0,0,0,239.4,24.5Z\"/><rect class=\"cls-2\" x=\"209.7\" y=\"25.6\" width=\"6.3\" height=\"1.4\"/><polygon class=\"cls-2\" points=\"269.5 25.6 267 25.6 267 23.2 265.6 23.2 265.6 25.6 263.1 25.6 263.1 27 265.6 27 265.6 29.5 267 29.5 267 27 269.5 27 269.5 25.6\"/><polygon class=\"cls-3\" points=\"0 17.5 83.4 0 344.3 0 289.1 17.5 0 17.5\"/><polygon class=\"cls-4\" points=\"289.1 17.5 289.1 88.4 344.3 70.9 344.3 0 289.1 17.5\"/></g></g></svg>");
        Accessor.getPageFor(webview.getEngine()).setBackgroundColor(0);
    }

    private void initLightingButton() {
        WebView webview = new WebView();
        webview.setPrefSize(50.0D, 50.0D);
        webview.setMaxSize(50.0D, 50.0D);
        webview.setZoom(0.8D);
        webview.getEngine()
               .loadContent(
                       "<svg version=\"1.1\" id=\"Layer_1\" xmlns=\"http://www.w3.org/2000/svg\" xmlns:xlink=\"http://www.w3.org/1999/xlink\" x=\"0px\" y=\"0px\"\t viewBox=\"0 0 1000 1000\" style=\"enable-background:new 0 0 1000 1000;\" xml:space=\"preserve\"><style type=\"text/css\">\t.st0{fill:#FFFFFF;}</style><g>\t<g transform=\"translate(0.000000,512.000000) scale(0.100000,-0.100000)\">\t\t<path class=\"st0\" d=\"M4879.2,4997.3c-78.6-34.5-159.1-132.2-172.5-208.9c-5.7-34.5-9.6-364.1-5.7-735.9\t\t\tc5.7-645.8,7.7-676.5,46-726.3c74.7-101.6,136.1-132.2,256.8-132.2c120.7,0,182,30.7,256.8,132.2c38.3,51.7,40.2,74.7,40.2,778\t\t\ts-1.9,726.3-40.2,778c-21.1,28.7-61.3,70.9-88.2,90.1C5109.1,5020.3,4955.8,5033.7,4879.2,4997.3z\"/>\t\t<path class=\"st0\" d=\"M1655.9,3661.6c-84.3-24.9-172.5-118.8-199.3-214.6c-42.2-159.1-24.9-184,515.5-726.3\t\t\tc408.2-408.2,505.9-494.4,569.2-511.7c218.5-59.4,431.2,153.3,371.8,371.8c-17.2,63.2-105.4,161-492.5,553.8\t\t\tC1876.2,3682.6,1832.2,3713.3,1655.9,3661.6z\"/>\t\t<path class=\"st0\" d=\"M8137,3646.2c-47.9-23-253-214.6-550-511.7c-392.9-398.6-475.3-490.6-492.5-553.8\t\t\tc-57.5-218.5,153.3-431.2,371.8-371.8c63.2,17.2,161,105.4,553.8,492.5c509.7,507.8,563.4,576.8,538.5,711\t\t\tC8522.1,3615.6,8313.3,3732.5,8137,3646.2z\"/>\t\t<path class=\"st0\" d=\"M4735.4,2559.7c-398.6-51.7-780-193.5-1111.5-415.8c-184-122.7-523.2-461.8-645.8-645.8\t\t\tC2796,1226,2665.7,919.4,2596.7,597.4c-49.8-228-49.8-730.1,0-958.2c107.3-496.3,320-887.3,680.3-1247.5\t\t\tc360.3-360.3,751.2-573,1247.5-680.3c228-49.8,730.1-49.8,958.2,0c492.5,105.4,893,323.9,1247.5,680.3\t\t\tc356.5,354.5,574.9,755,680.3,1247.5c49.8,228,49.8,730.1,0,958.2c-107.3,496.3-320,887.3-680.3,1247.5\t\t\tc-354.5,354.5-726.3,561.5-1209.2,670.7C5362.1,2552,4886.8,2578.8,4735.4,2559.7z M5446.4,1902.4\t\t\tc348.8-90.1,613.2-245.3,870-507.8C6868.3,829.2,7000.6,5.2,6647.9-694.3c-107.3-216.5-172.5-306.6-348.8-482.9\t\t\ts-266.4-241.5-482.9-348.8c-293.2-147.6-590.2-208.9-916-189.7c-469.5,28.8-845.1,197.4-1180.5,530.8\t\t\tc-266.4,262.5-421.6,536.6-511.7,893c-46,182.1-46,638.1,0,820.2c86.2,343,245.3,626.7,492.5,875.8\t\t\tc281.7,287.5,592.1,452.3,983.1,527C4875.3,1967.5,5256.7,1954.1,5446.4,1902.4z\"/>\t\t<path class=\"st0\" d=\"M279.9,398C151.5,342.5,78.7,194.9,105.5,51.2c17.2-90.1,113.1-195.5,199.3-220.4\t\t\tc46-13.4,306.6-19.2,755-15.3c655.4,5.8,686,7.7,735.9,46C1897.3-63.8,1928-2.5,1928,118.2s-30.7,182.1-132.2,256.8\t\t\tc-49.8,38.3-78.6,40.2-755,44.1C485,422.9,325.9,419.1,279.9,398z\"/>\t\t<path class=\"st0\" d=\"M8251.9,398c-128.4-55.6-201.2-203.1-174.4-346.8c17.2-90.1,113.1-195.5,199.3-220.4\t\t\tc46-13.4,306.6-19.2,755-15.3c655.4,5.8,686.1,7.7,735.9,46C9869.3-63.8,9900-2.5,9900,118.2s-30.7,182.1-132.2,256.8\t\t\tc-49.8,38.3-78.6,40.2-755,44.1C8457,422.9,8297.9,419.1,8251.9,398z\"/>\t\t<path class=\"st0\" d=\"M2522.1-1980.2c-57.5-21.1-193.6-145.6-557.7-511.7c-394.8-396.7-486.8-498.3-505.9-561.5\t\t\tc-65.2-222.3,151.4-438.8,373.7-373.7c63.2,19.2,164.8,111.2,569.2,513.6c408.2,408.2,494.4,505.9,511.7,569.2\t\t\tC2974.3-2114.3,2746.3-1899.7,2522.1-1980.2z\"/>\t\t<path class=\"st0\" d=\"M7293.8-1980.2c-151.4-53.7-239.5-214.6-199.3-364.1c17.3-63.2,105.4-161,492.5-553.8\t\t\tc513.6-515.5,574.9-561.5,716.7-538.5c103.5,19.2,218.5,122.7,247.2,226.1c42.2,157.1,21.1,187.8-496.3,709.1\t\t\tc-260.6,262.5-494.4,486.8-521.2,502.1C7452.8-1957.2,7374.2-1951.4,7293.8-1980.2z\"/>\t\t<path class=\"st0\" d=\"M4879.2-2974.8c-78.6-34.5-159.1-132.2-172.5-208.9c-5.7-34.5-9.6-364.1-5.7-735.9\t\t\tc5.7-645.8,7.7-676.5,46-726.3c74.7-101.6,136.1-132.2,256.8-132.2c120.7,0,182,30.7,256.8,132.2c38.3,51.7,40.2,74.7,40.2,778\t\t\ts-1.9,726.3-40.2,778c-21.1,28.7-61.3,70.9-88.2,90.1C5109.1-2951.8,4955.8-2938.3,4879.2-2974.8z\"/>/g></g></svg>");
        webview.setMouseTransparent(true);
        Accessor.getPageFor(webview.getEngine()).setBackgroundColor(0);
        lightingButton = new Button("Lighting", webview);
        lightingButton.setStyle("-fx-background-color: transparent;");
        lightingButton.setContentDisplay(ContentDisplay.TOP);
        lightingButton.setMinHeight(100.0D);
        lightingButton.setOnAction(e -> {
            childDialogStage = new Stage();
            new RGBLightingDialog(this).start(childDialogStage);
        });
    }

    private void initButtons() throws IOException {
        double xPos = 52.0D;
        double yPos = 64.0D;
        double xDelta = 107.3D;
        int buttonSize = 80;
        for (int i = 0; i < 4; i++) {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/assets/PCPanelRGB/knob.fxml"));
            Node nx = loader.load();
            knobs[i] = new Button("", nx);
            knobs[i].setId("dial_button");
            knobs[i].setContentDisplay(ContentDisplay.CENTER);
            knobs[i].setMinSize(buttonSize, buttonSize);
            knobs[i].setMaxSize(buttonSize, buttonSize);
            knobs[i].setLayoutX(xPos);
            knobs[i].setLayoutY(yPos);
            int knob = i;
            knobs[i].setOnAction(e -> {
                Window.showHint(false);
                BasicMacro bm = new BasicMacro(this, knob);
                try {
                    childDialogStage = new Stage();
                    bm.start(childDialogStage);
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            });
            knobs[i].setOnMouseClicked(c -> {
                if (c.getButton() == MouseButton.MIDDLE) {
                    try {
                        InputInterpreter.onButtonPress(getSerialNumber(), knob, true);
                    } catch (IOException e1) {
                        e1.printStackTrace();
                    }
                    try {
                        InputInterpreter.onButtonPress(getSerialNumber(), knob, false);
                    } catch (IOException e1) {
                        e1.printStackTrace();
                    }
                }
            });
            panelPane.getChildren().add(knobs[i]);
            xPos += 107.3D;
        }
    }

    public String toString() {
        return getDisplayName();
    }

    @Override
    public Image getPreviewImage() {
        return previewImage;
    }

    public int getKnobCount() {
        return 4;
    }

    @Override
    public void setKnobRotation(int knob, int value) {
        Platform.runLater(() -> rotateKnob(knob, value));
    }

    @Override
    public void setButtonPressed(int knob, boolean pressed) {
        Platform.runLater(() -> knobs[knob].setOpacity(pressed ? 0.5D : 1.0D));
    }

    private void setAllKnobUIColor(Color color) {
        for (int i = 0; i < getKnobCount(); ) {
            setKnobUIColor(i, color);
            i++;
        }
    }

    private void setKnobUIColorHex(int knob, String color) {
        Shape lightPane = (Shape) lightPanes.getChildren().get(knob);
        lightPane.setFill(Paint.valueOf(color));
    }

    private void setKnobUIColor(int knob, Color color) {
        Shape lightPane = (Shape) lightPanes.getChildren().get(knob);
        lightPane.setFill(color);
    }

    @Override
    public void closeDialogs() {
        if (childDialogStage != null && childDialogStage.isShowing())
            childDialogStage.close();
    }

    @Override
    public Button getLightingButton() {
        return lightingButton;
    }

    @Override
    public DeviceType getDeviceType() {
        return DeviceType.PCPANEL_RGB;
    }

    @Override
    public void showLightingConfigToUI(LightingConfig config) {
        LightingMode mode = config.getLightingMode();
        if (mode == LightingMode.ALL_COLOR) {
            setAllKnobUIColor(Color.valueOf(config.getAllColor()));
        } else if (mode == LightingMode.SINGLE_COLOR) {
            for (int i = 0; i < getKnobCount(); i++)
                setKnobUIColorHex(i, config.getIndividualColors()[i]);
        } else if (mode == LightingMode.ALL_RAINBOW) {
            for (int i = 0; i < getKnobCount(); i++)
                setKnobUIColor(i,
                        Color.hsb((360 * (getKnobCount() - i - 1) * (0xFF & config.getRainbowPhaseShift())) / 255.0D * getKnobCount(), 1.0D, (0xFF & config.getRainbowBrightness()) / 255.0D));
        } else if (mode == LightingMode.ALL_WAVE) {
            for (int i = 0; i < getKnobCount(); i++)
                setKnobUIColor(i, Color.hsb(360.0D * (0xFF & config.getWaveHue()) / 255.0D, 1.0D, (0xFF & config.getWaveBrightness()) / 255.0D));
        } else if (mode == LightingMode.ALL_BREATH) {
            for (int i = 0; i < getKnobCount(); i++)
                setKnobUIColor(i, Color.hsb(360.0D * (0xFF & config.getBreathHue()) / 255.0D, 1.0D, (0xFF & config.getBreathBrightness()) / 255.0D));
        }
    }
}
