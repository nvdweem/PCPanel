# Linux installation

Note: The software is not actively being developed for Linux, but attempts are being made to
stay/become more Linux compatible. If there are any issues please report them via an issue.

Due to there being a lot of Linux distributions there are bound to be features that won't work.
There are also some features that are not possible (anymore) on Linux like the focus volume on Wayland.

## Preparation

Linux might need a few more steps to get everything working.

1. Allow the software to access the device:
   ```shell
   sudoedit /etc/udev/rules.d/70-pcpanel.rules
   ```
1. Add the following lines:
   ```properties
   SUBSYSTEM=="usb", ATTRS{idVendor}=="04D8", ATTRS{idProduct}=="eb52", TAG+="uaccess"
   SUBSYSTEM=="usb", ATTRS{idVendor}=="0483", ATTRS{idProduct}=="a3c4", TAG+="uaccess"
   SUBSYSTEM=="usb", ATTRS{idVendor}=="0483", ATTRS{idProduct}=="a3c5", TAG+="uaccess"
   ```
1. Then run
   ```shell
   sudo udevadm control --reload-rules
   ```
1. Disconnect and re-connect each of your devices (udev rules are applied whenever a device is connected).
1. It seems that the first run of the software needs to be done as root (`sudo /opt/pcpanel/bin/PCPanel`). This might be needed whenever the software gets updated.
1. (Optional) Make the software startup automatically. When making the application startup automatically you can add the `quiet` parameter to not show the main window on startup.

If it still doesn't work, try restarting your computer (logging out is not enough).

The software depends on some PulseAudio commands (`pactl`) from `pulseaudio-utils` for volume control
and `xdotool` to get the currently active window for focus volume. These packages should be
installed automatically, but you can also install them manually if they are not.

If there are no tray extensions available, the application will still hide when closed. To show
the main window, just run the application again.

## Install

### Debian

Download the deb file and install with your package manager or via terminal:

   ```shell
   dpkg -i pcpanel_[version].deb
   apt-get -f install   # This is only needed if not-installed dependencies were found
   ```

### Other

If there is no option to install the .deb file then it might be needed to run the software manually.
For that, download the jar file and run it with

1. Download/install Java (at least 19) and [JavaFX 18](https://download2.gluonhq.com/openjfx/18.0.2/openjfx-18.0.2_linux-x64_bin-sdk.zip)
2. Unpack JavaFX to a location
3. Run the application:

```shell
java --module-path="[path-to-javafx]/lib" \
     --add-modules=javafx.controls,javafx.fxml \
     --add-exports javafx.controls/com.sun.javafx.scene.control.skin.resources=ALL-UNNAMED \
     --add-exports javafx.base/com.sun.javafx.event=ALL-UNNAMED \
     -jar [jarfile]
```

## Notes

In certain cases there will be a 'JavaEmbeddedFrame' when the application is running. This is caused by the tray icon.
It is possible to disable the tray icon and removing the JavaEmbeddedFrame by adding `-Ddisable.tray` to the command line.
If the tray is disabled, the close button will still only hide the application. Starting the application again will show the main window.
