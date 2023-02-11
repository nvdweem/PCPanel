# PCPanel contribution

This repository is based on the decompiled source code of the original app. The parts that couldn't be decompiled (dll's and sndctrl.exe)
have been replaced with custom native implementations. The source code for those parts are either Java-JNA implementations or in the
`src/main/cpp` directory.

## Running in IntelliJ

1. Import the project
1. Either
    1. Install JavaFX manually:
        1. Install JavaFX [Windows](https://download2.gluonhq.com/openjfx/18.0.2/openjfx-18.0.2_windows-x64_bin-sdk.zip), [Linux](https://download2.gluonhq.com/openjfx/18.0.2/openjfx-18.0.2_linux-x64_bin-sdk.zip)
        1. Setup the `JAVAFX_HOME` environment variable to the `javafx-sdk-x.y.z` directory
    1. Use [Liberica JDK](https://bell-sw.com/pages/downloads/) (Full JDK option, minimal version 17). It has JavaFX included.
1. Use the `PCPanel` run configuration

For Linux you will also need to do the steps from the [Linux instructions](linux.md) Installation step.

## Other IDE's

Probably the same, for the run configuration the important part is the VM options:

`--module-path="${JAVAFX_HOME}\lib" --add-modules=javafx.controls,javafx.fxml --add-exports javafx.controls/com.sun.javafx.scene.control.skin.resources=ALL-UNNAMED --add-exports javafx.base/com.sun.javafx.event=ALL-UNNAMED`

Adding the `skipfilecheck` command line argument ensures that you can debug while having the installed version running at the same time
(otherwise starting in the IDE will open the installed version).

It's also possible to change the `application.properties` (or create an `application-default.properties` so it doesn't get committed) with the following:

```properties
application.root=${user.home}/.pcpaneldev/
```

This will make the application use a different directory for the settings and profiles so that you don't overwrite your non-development profile while testing.

## Build installer

1. Install [Liberica JDK](https://bell-sw.com/pages/downloads/). The Full JDK option is required, at least version 17.
    - Verify by opening a fresh Terminal/Command Prompt and typing `java --version`.
1. Install [Apache Maven 3.6.3](http://maven.apache.org/install.html) or later and make sure it's on your path.
    - Verify this by opening a fresh Terminal/Command Prompt and typing `mvn --version`.
1. install [Wix 3 binaries](https://github.com/wixtoolset/wix3/releases/).
    - Windows only, not needed for Linux
    - Installing Wix via the installer should be sufficient for jpackage to find it.
1. Final step: run `mvn clean install`

## Native code (Windows only)

There is a visual studio solution in the `src/main/cpp` directory. The solution seems to have a single setting that has a hardcoded path
which is the JNI include directory.

This can be changed by clicking the project properties and changing `Configuration properties > C/C++ > General > Additional Include Directories`.

The SndCtrlTest project is there because Access Violations within JNI just close the application.
Running it with the Test code might actually show the error.

An `EnableFullDump.reg` registry file is included to enable full dumps when the application crashes. This can be used to debug the native code.
