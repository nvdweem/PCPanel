@echo off
setlocal enabledelayedexpansion

:: ============================================================================
::  generate-native-configs.cmd
::
::  Runs the native-image coverage tests under the GraalVM tracing agent and
::  merges the resulting JSON config files into the project's hand-maintained
::  native-image directory so they can be committed alongside source code.
::
::  Prerequisites
::  -------------
::  * GraalVM JDK installed and GRAALVM_HOME set  (or JAVA_HOME pointing to it)
::  * Maven wrapper (mvnw.cmd) present at the project root
::  * SndCtrl.dll available on PATH or in target/ (for the JNI tests)
::
::  Usage
::  -----
::    generate-native-configs.cmd
::
::  Output
::  ------
::  Generated JSON files are written to:
::    target\native-agent-output\
::
::  Then MERGED into (existing entries preserved, new ones appended):
::    src\main\resources\META-INF\native-image\com.getpcpanel\pcpanel\
::  ============================================================================

:: ── Locate project root (directory that contains this script) ────────────────
set "PROJECT_ROOT=%~dp0"
if "%PROJECT_ROOT:~-1%" == "\" set "PROJECT_ROOT=%PROJECT_ROOT:~0,-1%"

:: ── Resolve GraalVM home ─────────────────────────────────────────────────────
if not defined GRAALVM_HOME (
    if defined JAVA_HOME (
        set "GRAALVM_HOME=%JAVA_HOME%"
        echo INFO: GRAALVM_HOME not set; falling back to JAVA_HOME=%JAVA_HOME%
    ) else (
        echo ERROR: Neither GRAALVM_HOME nor JAVA_HOME is set.
        echo        Install GraalVM and set GRAALVM_HOME, or set JAVA_HOME to the GraalVM JDK.
        exit /b 1
    )
)

:: Ensure Maven uses the GraalVM JDK (not whatever JAVA_HOME the shell inherited)
set "JAVA_HOME=%GRAALVM_HOME%"
set "PATH=%JAVA_HOME%\bin;%PATH%"

:: Verify the tracing agent JAR / native-image-agent library is available
if not exist "%GRAALVM_HOME%\lib\svm\bin\native-image.cmd" (
    if not exist "%GRAALVM_HOME%\bin\native-image.cmd" (
        echo WARNING: native-image executable not found under GRAALVM_HOME=%GRAALVM_HOME%
        echo          Make sure GraalVM Native Image component is installed:
        echo            gu install native-image
        echo          Continuing anyway – the agent JVM flag may still work.
    )
)

:: ── Paths ────────────────────────────────────────────────────────────────────
set "AGENT_OUTPUT_DIR=%PROJECT_ROOT%\target\native-agent-output"
set "CONFIG_DEST=%PROJECT_ROOT%\src\main\resources\META-INF\native-image\com.getpcpanel\pcpanel"
set "MVNW=%PROJECT_ROOT%\mvnw.cmd"

:: ── Clean previous agent output ──────────────────────────────────────────────
echo.
echo [1/4] Cleaning previous agent output directory...
if exist "%AGENT_OUTPUT_DIR%" (
    rmdir /s /q "%AGENT_OUTPUT_DIR%"
)
mkdir "%AGENT_OUTPUT_DIR%"

:: ── Compile the project (tests + main) ──────────────────────────────────────
echo.
echo [2/4] Compiling project (test-compile)...
call "%MVNW%" test-compile -q
if %ERRORLEVEL% neq 0 (
    echo ERROR: Compilation failed.  Fix build errors before regenerating configs.
    exit /b %ERRORLEVEL%
)

:: ── Run coverage tests with tracing agent ────────────────────────────────────
echo.
echo [3/4] Running native-image coverage tests with tracing agent...
echo       Agent output  : %AGENT_OUTPUT_DIR%
echo       Tests         : SndCtrlNativeConfigTest, VolumeOverlayNativeTest
echo.

::  -Dtest selects only the four coverage tests so unrelated tests don't pollute
::  the config.  maven.test.failure.ignore=true lets the script continue even if
::  a test fails (e.g. on a headless CI machine without a display).
call "%MVNW%" test "-DargLine=-agentlib:native-image-agent=config-output-dir=%AGENT_OUTPUT_DIR% -Djava.awt.headless=false"

echo.
echo [3/4] Tests finished (failures are non-fatal for config generation).

:: ── Merge generated files into the hand-maintained config directory ───────────
echo.
echo [4/4] Merging generated configs into %CONFIG_DEST%
echo.

if not exist "%CONFIG_DEST%" mkdir "%CONFIG_DEST%"

set FILES_UPDATED=0

:: GraalVM 23+ generates a unified reachability-metadata.json that replaces all of the
:: old individual files (jni-config.json, reflect-config.json, proxy-config.json, etc.).
::
:: The following files are intentionally hand-maintained and must NOT be overwritten:
::   jni-config.json   -- 4 project classes SndCtrl.dll calls back into via JNI (C->Java).
::                        Cannot be auto-generated because unit tests never load the native DLL.
::   proxy-config.json -- JNA dynamic proxy interfaces (Shell32Extra, VoicemeeterInstance, …).
::                        Cannot be auto-generated without loading the native libraries.
::
:: Only reachability-metadata.json (and future agent-only files) are copied here.

call "%MVNW%" verify "-DargLine=-Dnative -Dquarkus.native.agent-configuration-apply" -Dnative -Dquarkus.native.agent-configuration-apply


@REM for %%F in (reachability-metadata.json) do (
@REM     if exist "%AGENT_OUTPUT_DIR%\%%F" (
@REM         copy /y "%AGENT_OUTPUT_DIR%\%%F" "%CONFIG_DEST%\%%F" > nul
@REM         echo   Updated : %%F
@REM         set /a FILES_UPDATED=FILES_UPDATED+1
@REM     ) else (
@REM         echo   Skipped : %%F  ^(not generated^)
@REM     )
@REM )

:: ── Summary ──────────────────────────────────────────────────────────────────
echo.
if %FILES_UPDATED% gtr 0 (
    echo SUCCESS: %FILES_UPDATED% config file^(s^) updated.
    echo.
    echo Next steps:
    echo   1. Review the changes in %CONFIG_DEST%
    echo   2. Diff against the previous version to understand what changed
    echo   3. Remove spurious entries captured from test infrastructure
    echo      ^(look for class names ending in Test, or JUnit internals^)
    echo   4. Commit the updated files
) else (
    echo WARNING: No config files were generated.
    echo          Check that:
    echo            * The tests compiled and ran ^(see target\surefire-reports\^)
    echo            * GRAALVM_HOME points to a GraalVM JDK ^(not a regular JDK^)
    echo            * The native-image-agent is installed:  gu install native-image
)

echo.
echo Raw agent output is preserved in:
echo   %AGENT_OUTPUT_DIR%
echo.

endlocal
