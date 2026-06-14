# Third party PCPanel Software

Third party/community managed controller software for the [PCPanel](https://getpcpanel.com) devices.

**This software is not affiliated with PCPanel Software**, the original software can be found [here](https://www.getpcpanel.com/download).
This project aims to add features and fix bugs that are requested by the community. The version numbering is disconnected from the original
software, this project started counting at version 1.0 which was essentially a branch of the official 2.2.1 release.

To see all changes since the original software, see the [changelog](CHANGELOG.md).

Development focus is mainly targeted at Windows but some effort is put into making the software run on Linux and macOS.

# Download

The installer can be found by clicking the latest release on the right side of the page. When development is ongoing there will
also be a 'snapshot' release which can be found by opening the [releases](https://github.com/nvdweem/PCPanel/releases) page.

Once on the release page there will be a changelog and a list of assets. The Windows installer is the `PCPanel-<version>-setup.exe`,
the Linux installer is the `.deb` file (a Flatpak bundle is also provided as a best-effort alternative) and the macOS installer is the
architecture-specific `PCPanel-<version>-<arch>.dmg`. The application is now a native executable, so a separate Java installation is
no longer required.

The 'Source code' artifact is probably not needed for anybody.

# Installation

## Windows

Run the `PCPanel-<version>-setup.exe` installer, and you should be good to go. It is a per-user install, so it needs no
administrator rights and installs into your user profile (`%LOCALAPPDATA%`). The installer can run the application when it
finishes and offers to add the application to start automatically when you sign in to Windows — optionally with
administrator privileges (set up as a scheduled task), which is needed if you want PCPanel to control apps that run elevated.

## Linux

Installing on Linux is a bit harder, see [Linux instructions](linux.md).

## macOS

macOS support is experimental and community-contributed, see [macOS instructions](mac.md). Device
volume, mute and default-device switching work, but per-application volume is not possible on stock macOS.

# Issues / Feature requests

If you encounter any issues with the software, or you have an idea for improvements please create an
issue on the [issue tracker](https://github.com/nvdweem/PCPanel/issues). For issues, try to be as
complete as possible in the description. The issue templates should indicate what information is needed.

# Migration

The first startup will check for the profile from the original software and ask to migrate. If this doesn't work, or you want to migrate manually again later, you will need to
manually copy the settings file:
`%localappdata%\PCPanel Software\save.json`
to
`%userprofile%\.pcpanel\profiles.json`

# Generate reachability metadata

```shell
mvn test "-DargLine=-agentlib:native-image-agent=config-output-dir=src/main/resources/META-INF/native-image/ -Djava.awt.headless=false"
mvn test "-DargLine=-Dnative -Dquarkus.native.agent-configuration-apply" -Dnative -Dquarkus.native.agent-configuration-apply
```


---
Build template from [wiverson](https://github.com/wiverson/maven-jpackage-template)
