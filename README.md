# PCPanel Software

Decompiled from the getpcpanel.com software and restructured to be editable.

Build template from [wiverson](https://github.com/wiverson/maven-jpackage-template)

# Running in IntelliJ

1. Import the project
1. Install [JavaFX](https://download2.gluonhq.com/openjfx/18.0.1/openjfx-18.0.1_windows-x64_bin-sdk.zip)
1. Setup the `JAVAFX_HOME` environment variable to the `javafx-sdk-x.y.z` directory
1. Use the `PCPanel` run configuration

# Installation

1. Install [OpenJDK Java 17](https://adoptium.net/?variant=openjdk17) or
   [Oracle Java 17](https://www.oracle.com/java/technologies/javase-downloads.html).
    - Verify by opening a fresh Terminal/Command Prompt and typing `java --version`.
1. Install [Apache Maven 3.6.3](http://maven.apache.org/install.html) or later and make sure it's on your path.
    - Verify this by opening a fresh Terminal/Command Prompt and typing `mvn --version`.
1. install [Wix 3 binaries](https://github.com/wixtoolset/wix3/releases/).
    - Installing Wix via the installer should be sufficient for jpackage to find it.
1. Final step: run `mvn clean install`
