package main;

import hid.OutputInterpreter;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.event.EventHandler;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.shape.SVGPath;
import lombok.extern.log4j.Log4j2;
import save.DeviceSave;
import save.LightingConfig;
import save.Profile;
import save.Save;

@Log4j2
public abstract class Device {
    private HBox profileMenu;

    private ComboBox<Profile> profiles;

    protected String serialNumber;

    protected DeviceSave save;

    protected Device(String serialNum, DeviceSave deviceSave) {
        serialNumber = serialNum;
        save = deviceSave;
        initProfileMenu();
    }

    private void initProfileMenu() {
        profileMenu = new HBox();
        profileMenu.setAlignment(Pos.CENTER_RIGHT);
        profiles = new ComboBox(FXCollections.observableArrayList(save.getProfiles()));
        profiles.setPrefWidth(200.0D);
        profiles.getSelectionModel().select(save.getCurrentProfile());
        LimitedTextField textfield = new LimitedTextField(10);
        profiles.getSelectionModel().selectedItemProperty().addListener(new ChangeListener<Profile>() {
            @Override
            public void changed(ObservableValue<? extends Profile> observable, Profile oldValue, Profile newValue) {
                if (newValue == null)
                    return;
                log.debug("change");
                String name = newValue.getName();
                boolean success = save.setCurrentProfile(name);
                if (!success)
                    return;
                setLighting(save.getLightingConfig(), true);
                Save.saveFile();
            }
        });
        ListCell<Profile> buttonCell = new ListCell<Profile>() {
            @Override
            protected void updateItem(Profile item, boolean btl) {
                super.updateItem(item, btl);
                setGraphic(null);
                if (item != null)
                    setText(item.getName());
            }
        };
        textfield.setOnAction(c -> {
            Profile p = buttonCell.getItem();
            String oldName = p.getName();
            String newName = textfield.getText();
            buttonCell.setGraphic(null);
            if (save.getProfile(newName) != null) {
                buttonCell.setText(oldName);
                return;
            }
            p.setName(newName);
            buttonCell.setText(newName);
            profiles.getItems().set(profiles.getItems().indexOf(p), p);
            save.setCurrentProfile(newName);
            Save.saveFile();
        });
        textfield.focusedProperty().addListener((arg, oldVal, newVal) -> {
            if (!newVal.booleanValue()) {
                buttonCell.setGraphic(null);
                buttonCell.setText(buttonCell.getItem().getName());
            }
        });
        textfield.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.ESCAPE) {
                buttonCell.setGraphic(null);
                buttonCell.setText(buttonCell.getItem().getName());
            }
        });
        profiles.setButtonCell(buttonCell);
        profiles.addEventFilter(MouseEvent.MOUSE_RELEASED, new EventHandler<MouseEvent>() {
            @Override
            public void handle(MouseEvent event) {
                if (event.getButton() == MouseButton.SECONDARY) {
                    event.consume();
                    MenuItem rename = new MenuItem("Rename");
                    MenuItem delete = new MenuItem("Delete");
                    rename.setOnAction(c -> {
                        buttonCell.setGraphic(textfield);
                        textfield.requestFocus();
                        textfield.setText(buttonCell.getText());
                        textfield.selectAll();
                        buttonCell.setText("");
                    });
                    delete.setOnAction(c -> {
                        save.getProfiles().remove(buttonCell.getItem());
                        profiles.getItems().remove(buttonCell.getItem());
                        if (profiles.getValue() == null)
                            profiles.getSelectionModel().select(0);
                    });
                    if (profiles.getItems().size() <= 1)
                        delete.setDisable(true);
                    ContextMenu cm = new ContextMenu(rename, delete);
                    profiles.setContextMenu(cm);
                } else if (event.getButton() == MouseButton.PRIMARY) {
                    buttonCell.getGraphic();
                }
            }
        });
        Button addButton = new Button();
        String svgCode = "M28,14H18V4c0-1.104-0.896-2-2-2s-2,0.896-2,2v10H4c-1.104,0-2,0.896-2,2s0.896,2,2,2h10v10c0,1.104,0.896,2,2,2  s2-0.896,2-2V18h10c1.104,0,2-0.896,2-2S29.104,14,28,14z";
        SVGPath path = new SVGPath();
        path.setStyle("-fx-fill:white;");
        path.setContent(svgCode);
        path.setScaleX(0.7D);
        path.setScaleY(0.7D);
        addButton.setGraphic(path);
        addButton.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        addButton.setOnAction(c -> {
            String newName;
            for (int i = 1; ; i++) {
                newName = "profile" + i;
                if (save.getProfile(newName) == null)
                    break;
            }
            Profile newProfile = new Profile(newName, getDeviceType());
            save.getProfiles().add(newProfile);
            profiles.getItems().add(newProfile);
            profiles.getSelectionModel().select(newProfile);
        });
        addButton.setPrefSize(44.0D, 44.0D);
        profileMenu.getChildren().add(addButton);
        profileMenu.getChildren().addAll(profiles);
    }

    public Pane getProfileMenu() {
        return profileMenu;
    }

    public String getSerialNumber() {
        return serialNumber;
    }

    public void setProfile(String name) {
        Profile p = save.getProfile(name);
        if (p != null)
            profiles.setValue(p);
    }

    public String getDisplayName() {
        return save.getDisplayName();
    }

    public void setDisplayName(String name) {
        save.setDisplayName(name);
    }

    public LightingConfig getLightingConfig() {
        return save.getLightingConfig();
    }

    public void setLighting(LightingConfig config, boolean priority) {
        if (config == null) {
            config = LightingConfig.defaultLightingConfig(getDeviceType());
            save.setLightingConfig(config);
            Save.saveFile();
        }
        try {
            showLightingConfigToUI(config);
            save.setLightingConfig(config);
            try {
                OutputInterpreter.sendLightingConfig(getSerialNumber(), getDeviceType(), config, priority);
            } catch (Exception e) {
                log.error("Unable to send lighting config", e);
            }
        } catch (Exception e) {
            log.error("Unable to set lighting", e);
            setLighting(LightingConfig.defaultLightingConfig(getDeviceType()), priority);
        }
    }

    public abstract Pane getDevicePane();

    public abstract Node getLabel();

    public abstract Button getLightingButton();

    public abstract Image getPreviewImage();

    public abstract DeviceType getDeviceType();

    public abstract void setKnobRotation(int paramInt1, int paramInt2);

    public abstract void setButtonPressed(int paramInt, boolean paramBoolean);

    public abstract void closeDialogs();

    public abstract void showLightingConfigToUI(LightingConfig paramLightingConfig);
}
