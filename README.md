# PCPanel Software

Controller software for the [PCPanel](https://getpcpanel.com) devices.

Development focus is mainly targeted at Windows but Linux is supported.

# Download

The installer can be found by clicking the latest release on the right side of the page. When development is ongoing there will
also be a 'snapshot' release which can be found by opening the [releases](https://github.com/nvdweem/PCPanel/releases) page.

Once on the release page there will be a changelog and a list of assets. The Windows installer is the msi, the Linux installer is the .deb file.
It's also possible to download the jar file to run it manually using a local Java installation.

The 'Source code' artifact is probably not needed for anybody.

# Installation

## Windows

Just double-click the msi installer, and you should be good to go. The installer will run the application after the installation
is complete and will add the application to start automatically on Windows startup.

## Linux

Installing on Linux is a bit harder, see [Linux instructions](linux.md).

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

---
Build template from [wiverson](https://github.com/wiverson/maven-jpackage-template)
