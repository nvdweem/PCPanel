# PCPanel Software

Controller software for the [PCPanel](https://getpcpanel.com) devices.

This repository is based on the decompiled source code of the original app. The parts that couldn't be decompiled (dll's and sndctrl.exe)
have been replaced with custom native implementations. The source code for those parts are either Java-JNA implementations or in the
`src/main/cpp` directory.

# Download

The installer can be found by clicking the latest release on the right side of the page. When development is ongoing there will
also be a 'snapshot' release which can be found by opening the [releases](https://github.com/nvdweem/PCPanel/releases) page.

Once on the release page there will be a changelog and a list of assets. The Windows installer is ths msi, the Linux installer is the .deb file.
The 'Source code' artifact is probably not needed for anybody.

# Installation

## Windows

Just double-click the msi installer, and you should be good to go. The installer will run the application after the installation
is complete and will add the application to start automatically on Windows startup.

## Linux

See [Linux instructions](linux.md).

# Migration

The first startup will check for the profile from the original software and ask to migrate. If this doesn't work, or you want to migrate manually again later, you will need to
manually copy the settings file:
`%localappdata%\PCPanel Software\save.json`
to
`%userprofile%\.pcpanel\profiles.json`

# Running in IntelliJ

1. Import the project
1. Either
    1. Install JavaFX manually:
        1. Install JavaFX [Windows](https://download2.gluonhq.com/openjfx/18.0.2/openjfx-18.0.2_windows-x64_bin-sdk.zip), [Linux](https://download2.gluonhq.com/openjfx/18.0.2/openjfx-18.0.2_linux-x64_bin-sdk.zip)
        1. Setup the `JAVAFX_HOME` environment variable to the `javafx-sdk-x.y.z` directory
    1. Use [Liberica JDK](https://bell-sw.com/pages/downloads/) (Full JDK option, minimal version 17). It has JavaFX included.
1. Use the `PCPanel` run configuration

For Linux you will also need to do the steps from the Installation step.

# Other IDE's

Probably the same, for the run configuration the important part is:

`--module-path="${JAVAFX_HOME}\lib" --add-modules=javafx.controls,javafx.fxml --add-exports javafx.controls/com.sun.javafx.scene.control.skin.resources=ALL-UNNAMED --add-exports javafx.base/com.sun.javafx.event=ALL-UNNAMED`

Adding the `skipfilecheck` command line argument ensures that you can debug while having the installed version running at the same time
(otherwise starting in the IDE will open the installed version).

# Build installer

1. Install [Liberica JDK](https://bell-sw.com/pages/downloads/). The Full JDK option is required, at least version 17.
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

An `EnableFullDump.reg` registry file is included to enable full dumps when the application crashes. This can be used to debug the native code.

---
Build template from [wiverson](https://github.com/wiverson/maven-jpackage-template)
