# PCPanel Software

Controller software for the [PCPanel](https://getpcpanel.com) devices.

This repository is based on the decompiled source code of the original app. The parts that couldn't be decompiled (dll's and sndctrl.exe)
have been replaced with custom native implementations. The source code for those parts are either Java-JNA implementations or in the
`src/main/cpp` directory.

# Installation

## Windows

Just doubleclick the msi installer and you should be good to go. The installer will run the application after the installation
is complete and will add the application to start automatically on Windows startup.

## Linux

Linux might need a few more steps to get everything working.

1. Download the deb file and install with your package manager or via terminal:
   ```shell
   dpkg -i pcpanel_[version].deb
   apt-get -f install   # This is only needed if not-installed dependencies were found
   ```
2. Allow the software to access the device:
   ```shell
   sudoedit /etc/udev/rules.d/70-pcpanel.rules
   ```
3. Add the following lines:
   ```properties
   SUBSYSTEM=="usb", ATTRS{idVendor}=="04D8", ATTRS{idProduct}=="eb52", TAG+="uaccess"
   SUBSYSTEM=="usb", ATTRS{idVendor}=="0483", ATTRS{idProduct}=="a3c4", TAG+="uaccess"
   SUBSYSTEM=="usb", ATTRS{idVendor}=="0483", ATTRS{idProduct}=="a3c5", TAG+="uaccess"
   ```
4. Then run
   ```shell
   sudo udevadm control --reload-rules
   ```
5. (Optional) Make the software startup automatically

I then had to restart to get it to work, logging out and in might work as well.

The software depends on some PulseAudio commands from `pulseaudio-utils` for volume control
and `xdotool` to get the currently active window for focus volume. These packages should be
installed automatically, but you can also install them manually if they are not.

# Migration

When replacing the original PCPanel software with this version it's possible to keep all settings. To do that you will need to manually copy
the settings file:
`%localappdata%\PCPanel Software\save.json`
to
`%userprofile%\.pcpanel\profiles.json`

# Running in IntelliJ

1. Import the project
1. Install [JavaFX](https://download2.gluonhq.com/openjfx/18.0.1/openjfx-18.0.1_windows-x64_bin-sdk.zip)
1. Setup the `JAVAFX_HOME` environment variable to the `javafx-sdk-x.y.z` directory
1. Use the `PCPanel` run configuration

# Other IDE's

Probably the same, for the run configuration the important part is:

`--module-path="${JAVAFX_HOME}\lib" --add-modules=javafx.controls,javafx.fxml --add-exports javafx.controls/com.sun.javafx.scene.control.skin.resources=ALL-UNNAMED --add-exports javafx.base/com.sun.javafx.event=ALL-UNNAMED`

Adding the `skipfilecheck` command line argument ensures that you can debug while having the installed version running at the same time
(otherwise starting in the IDE will open the installed version).

# Build installer

1. Install [OpenJDK Java 17](https://adoptium.net/?variant=openjdk17) or
   [Oracle Java 17](https://www.oracle.com/java/technologies/javase-downloads.html).
    - Verify by opening a fresh Terminal/Command Prompt and typing `java --version`.
2. Install [Apache Maven 3.6.3](http://maven.apache.org/install.html) or later and make sure it's on your path.
    - Verify this by opening a fresh Terminal/Command Prompt and typing `mvn --version`.
3. install [Wix 3 binaries](https://github.com/wixtoolset/wix3/releases/).
    - Windows only, not needed for Linux
    - Installing Wix via the installer should be sufficient for jpackage to find it.
5. Final step: run `mvn clean install`

# Native code (Windows only)

There is a visual studio solution in the `src/main/cpp` directory. The solution seems to have a single setting that has a hardcoded path
which is the JNI include directory.

This can be changed by clicking the project properties and changing `Configuration properties > C/C++ > General > Additional Include Directories`.

The SndCtrlTest project is there because Access Violations within JNI just close the application.
Running it with the Test code might actually show the error.

---
Build template from [wiverson](https://github.com/wiverson/maven-jpackage-template)
