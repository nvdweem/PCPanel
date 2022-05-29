# PCPanel Software

Controller software for the [PCPanel](https://getpcpanel.com) devices.

This repository is based on the decompiled source code of the original app. The parts that couldn't be decompiled (dll's and sndctrl.exe)
have been replaced with custom native implementations. The source code for those parts are either Java-JNA implementations or in the
`src/main/cpp` directory.

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

`--module-path=&quot;${JAVAFX_HOME}\lib&quot; --add-modules=javafx.controls,javafx.fxml --add-exports javafx.controls/com.sun.javafx.scene.control.skin.resources=ALL-UNNAMED --add-exports javafx.base/com.sun.javafx.event=ALL-UNNAMED`

Adding the `skipfilecheck` command line argument ensures that you can debug while having the installed version running at the same time
(otherwise starting in the IDE will open the installed version).

# Installation

1. Install [OpenJDK Java 17](https://adoptium.net/?variant=openjdk17) or
   [Oracle Java 17](https://www.oracle.com/java/technologies/javase-downloads.html).
    - Verify by opening a fresh Terminal/Command Prompt and typing `java --version`.
1. Install [Apache Maven 3.6.3](http://maven.apache.org/install.html) or later and make sure it's on your path.
    - Verify this by opening a fresh Terminal/Command Prompt and typing `mvn --version`.
1. install [Wix 3 binaries](https://github.com/wixtoolset/wix3/releases/).
    - Installing Wix via the installer should be sufficient for jpackage to find it.
1. Final step: run `mvn clean install`

# Native code

There is a visual studio solution in the `src/main/cpp` directory. The solution seems to have a single setting that has a hardcoded path
which is the JNI include directory.

This can be changed by clicking the project properties and changing `Configuration properties > C/C++ > General > Additional Include Directories`.

The SndCtrlTest project is there because Access Violations within JNI just close the application.
Running it with the Test code might actually show the error.

---
Build template from [wiverson](https://github.com/wiverson/maven-jpackage-template)
