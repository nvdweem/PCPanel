; Inno Setup script for the PCPanel Quarkus native image.
;
; This produces a **per-user** installer: it requires no administrator rights and
; installs into the user profile (%LOCALAPPDATA%\PCPanel). That mirrors the old
; jpackage `--win-per-user-install` behaviour and is the friendliest option for a
; tray/background app that the user starts on login.
;
; The GraalVM native image is NOT a single self-contained file: native-image emits
; the executable plus a set of companion DLLs (awt.dll, fontmanager.dll, jvm.dll,
; lcms.dll, ...) that the binary loads from its own directory at runtime via
; java.library.path. The whole `dist` folder therefore has to be installed together.
;
; Build:
;   iscc /DMyAppVersion=1.8.123 /DSourceDir=..\..\target\windows-dist packaging\windows\pcpanel.iss
;
; Defines (override on the iscc command line):
;   MyAppVersion  - display/version-info version (default 1.8.0)
;   SourceDir     - folder containing PCPanel.exe + companion DLLs (default ..\..\target\windows-dist)
;   OutputDir     - where the installer is written       (default ..\..\target)

#ifndef MyAppVersion
  #define MyAppVersion "1.8.0"
#endif
#ifndef SourceDir
  #define SourceDir "..\..\target\windows-dist"
#endif
#ifndef OutputDir
  #define OutputDir "..\..\target"
#endif

#define MyAppName "PCPanel"
#define MyAppPublisher "PCPanel"
#define MyAppURL "https://github.com/nvdweem/PCPanel"
#define MyAppExeName "PCPanel.exe"

[Setup]
; A fixed AppId keeps upgrades in place (replaces the old jpackage upgrade UUID).
AppId={{9421bff0-3840-414c-8563-407fbcd1d04d}
AppName={#MyAppName}
AppVersion={#MyAppVersion}
AppVerName={#MyAppName} {#MyAppVersion}
AppPublisher={#MyAppPublisher}
AppPublisherURL={#MyAppURL}
AppSupportURL={#MyAppURL}/issues
AppUpdatesURL={#MyAppURL}/releases
VersionInfoVersion={#MyAppVersion}
DefaultDirName={autopf}\{#MyAppName}
DefaultGroupName={#MyAppName}
DisableProgramGroupPage=yes
; Per-user install: no UAC prompt, installs under the user profile.
PrivilegesRequired=lowest
PrivilegesRequiredOverridesAllowed=dialog
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible
SetupIconFile=..\..\app-icon.ico
UninstallDisplayIcon={app}\{#MyAppExeName}
WizardStyle=modern
Compression=lzma2/max
SolidCompression=yes
OutputDir={#OutputDir}
OutputBaseFilename=PCPanel-{#MyAppVersion}-setup
; A running PCPanel locks its own exe + companion DLLs, so it must be closed before we overwrite them.
; We close it ourselves in PrepareToInstall (graceful WM_CLOSE, then a forced kill) — see [Code] below.
; `force` is the backstop: if anything is still holding a file when the Restart Manager runs, it is
; terminated silently instead of showing the user a "please close these applications" page (which they
; could not act on for a headless tray app anyway).
CloseApplications=force
RestartApplications=no

[Languages]
Name: "english"; MessagesFile: "compiler:Default.isl"

[Tasks]
Name: "startup"; Description: "Start {#MyAppName} automatically when I sign in to Windows"; GroupDescription: "Startup:"
; Sub-option: start elevated. A plain HKCU\Run entry always launches unelevated, so administrator
; startup is set up as a scheduled task with highest privileges (created via an elevation prompt).
Name: "startup\admin"; Description: "Run it as administrator (needed to control apps that run elevated; you'll be asked to confirm)"; GroupDescription: "Startup:"; Flags: unchecked
Name: "desktopicon"; Description: "Create a desktop shortcut"; GroupDescription: "Additional shortcuts:"; Flags: unchecked

[Files]
; Recursively copy the entire native-image distribution (exe + companion DLLs).
Source: "{#SourceDir}\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs

[Icons]
Name: "{group}\{#MyAppName}"; Filename: "{app}\{#MyAppExeName}"
Name: "{group}\Uninstall {#MyAppName}"; Filename: "{uninstallexe}"
Name: "{autodesktop}\{#MyAppName}"; Filename: "{app}\{#MyAppExeName}"; Tasks: desktopicon

[Registry]
; Normal (unelevated) autostart for the current user. "quiet" launches without showing the main
; window. Skipped when the administrator-startup sub-option is chosen (a scheduled task is used
; instead) so the app does not start twice.
Root: HKCU; Subkey: "Software\Microsoft\Windows\CurrentVersion\Run"; ValueType: string; \
    ValueName: "{#MyAppName}"; ValueData: """{app}\{#MyAppExeName}"" quiet"; \
    Flags: uninsdeletevalue; Tasks: startup; Check: not WizardIsTaskSelected('startup\admin')

[Run]
; Offer to launch the app right after installation finishes. The /postinstall argument tells the app
; this launch came straight from the installer, so it opens the UI in the browser and shows the
; post-install/update dialog (changelog + the "open in browser on startup" option). It does NOT set up
; OS auto-start — this is a one-shot launch.
Filename: "{app}\{#MyAppExeName}"; Parameters: "/postinstall"; Description: "Launch {#MyAppName} now"; \
    Flags: nowait postinstall skipifsilent
; Auto-update relaunch. The in-app updater runs this installer silently with `/UPDATE=1`, which skips the
; interactive "Launch now" entry above (skipifsilent). This entry re-launches the freshly updated app in
; that case so an unattended update ends with it running again. It passes `/updated`, which flags the
; "just updated" dialog for the UI but does NOT open a browser: the update was triggered from the
; already-open UI, whose websocket reconnects and shows the dialog itself — a second tab to the same UI
; would be redundant. WantsUpdateRelaunch gates this to the /UPDATE=1 run.
Filename: "{app}\{#MyAppExeName}"; Parameters: "/updated"; \
    Flags: nowait; Check: WantsUpdateRelaunch

[UninstallRun]
; Make sure a running instance is stopped before files are removed.
Filename: "{cmd}"; Parameters: "/C taskkill /IM ""{#MyAppExeName}"" /F"; \
    Flags: runhidden; RunOnceId: "KillPCPanel"

[Code]
const
  StartupTaskName = 'PCPanel';
  // Win32 class name registered by TrayServiceWin for the app's (invisible) message window, and the
  // WM_CLOSE message we post to it. Keep the class name in sync with TrayServiceWin.WINDOW_CLASS.
  TrayWindowClass = 'PCPanelTrayWindow';
  WM_CLOSE = $0010;

// Handle to the running instance's tray window, or 0 if PCPanel is not running.
function FindTrayWindow: HWND;
begin
  Result := FindWindowByClassName(TrayWindowClass);
end;

// Close any running PCPanel before its files are overwritten. A running instance locks its own exe and
// the companion DLLs it loaded, so an in-place upgrade fails unless it is stopped first. We ask it to
// shut down gracefully (WM_CLOSE, handled by TrayServiceWin -> normal Quarkus shutdown, which saves
// settings and releases the file locks), wait for it to go, and force-kill only as a fallback.
procedure CloseRunningInstance;
var
  wnd: HWND;
  resultCode: Integer;
  i: Integer;
begin
  wnd := FindTrayWindow;
  if wnd <> 0 then
  begin
    Log('PCPanel is running; asking it to close gracefully (WM_CLOSE).');
    PostMessage(wnd, WM_CLOSE, 0, 0);
    // Wait up to ~8s for it to shut down and release its locks.
    for i := 1 to 40 do
    begin
      if FindTrayWindow = 0 then
        Break;
      Sleep(200);
    end;
    if FindTrayWindow = 0 then
    begin
      // Give the shutdown hooks (settings save, tray cleanup) a moment to finish after the window is gone.
      Sleep(500);
      Log('PCPanel closed gracefully.');
      Exit;
    end;
    Log('PCPanel did not close in time; forcing termination.');
  end;

  // Fallback: force-terminate any lingering PCPanel.exe (graceful close timed out, or a process is
  // running without a tray window). Harmless no-op when nothing is running.
  Exec(ExpandConstant('{cmd}'), '/C taskkill /IM "{#MyAppExeName}" /F', '',
    SW_HIDE, ewWaitUntilTerminated, resultCode);
  Sleep(1000); // let the OS release the file handles before we start copying
end;

// Runs after the wizard, just before any file is copied.
function PrepareToInstall(var NeedsRestart: Boolean): String;
begin
  CloseRunningInstance;
  Result := '';
end;

// True when the in-app auto-updater launched this installer (it passes /UPDATE=1). Used to relaunch the
// app after a silent update, since the normal "Launch now" [Run] entry is skipped in silent mode.
function WantsUpdateRelaunch: Boolean;
begin
  Result := ExpandConstant('{param:UPDATE|0}') = '1';
end;

// Create an "on logon" scheduled task that runs the app with highest privileges. This requires
// elevation, so it is launched with the 'runas' verb (a single UAC confirmation). The /tr value is
// quoted and the inner quotes escaped (\") the way schtasks expects, so a profile path containing
// spaces still works.
procedure CreateAdminStartupTask;
var
  ResultCode: Integer;
  Params: string;
begin
  Params := '/Create /TN "' + StartupTaskName + '" /TR "\"' + ExpandConstant('{app}\{#MyAppExeName}') +
            '\" quiet" /SC ONLOGON /RL HIGHEST /F';
  if (not ShellExec('runas', 'schtasks.exe', Params, '', SW_HIDE, ewWaitUntilTerminated, ResultCode)) or (ResultCode <> 0) then
    MsgBox('PCPanel was installed, but the administrator startup task could not be created.' + #13#10 +
           'You can add it later from Task Scheduler, or re-run the installer.', mbInformation, MB_OK);
end;

// True if our scheduled task exists. Querying does not need elevation, so we can check before
// prompting for UAC on uninstall.
function StartupTaskExists: Boolean;
var
  ResultCode: Integer;
begin
  Result := Exec('schtasks.exe', '/Query /TN "' + StartupTaskName + '"', '', SW_HIDE, ewWaitUntilTerminated, ResultCode) and (ResultCode = 0);
end;

procedure RemoveAdminStartupTask;
var
  ResultCode: Integer;
begin
  ShellExec('runas', 'schtasks.exe', '/Delete /TN "' + StartupTaskName + '" /F', '', SW_HIDE, ewWaitUntilTerminated, ResultCode);
end;

procedure CurStepChanged(CurStep: TSetupStep);
begin
  if CurStep = ssPostInstall then
    if WizardIsTaskSelected('startup\admin') then
      CreateAdminStartupTask;
end;

procedure CurUninstallStepChanged(CurUninstallStep: TUninstallStep);
begin
  // Only prompt for elevation if the task is actually present.
  if (CurUninstallStep = usUninstall) and StartupTaskExists then
    RemoveAdminStartupTask;
end;
