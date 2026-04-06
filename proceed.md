# Proceed.md - Investigation Log

## Current Date: 2026-04-06

## Problem Summary
Native Quarkus executable starts on wrong port (8080 instead of 7654), shows "No scheduled business methods found", `/api/devices` returns 404, devices don't connect.

## Root Causes Identified

### 1. Port/Scheduled Methods Issue
The native image is likely using a stale/wrong build. The `application.properties` correctly has port 7654.
The "No scheduled business methods found" indicates that CDI beans with `@Scheduled` may not be active 
or the scheduler extension is not detecting the beans correctly in native mode.

### 2. Resource Include Pattern (FIXED)
- OLD: `SndCtrl.dll,*.so,*.dll,*.dylib` — flat patterns, don't match nested paths
- NEW: `SndCtrl.dll,**/*.dll,**/*.so,**/*.dylib` — should match `win32-x86-64/hidapi.dll` etc.
- STATUS: Applied, but need to verify the pattern syntax is correct for Quarkus

### 3. JNI Config NOT Being Applied (CRITICAL - UNRESOLVED)
- Created `src/main/resources/META-INF/native-image/jni-config.json` 
- After rebuild, runner JAR's `jni-config.json` is STILL `[]` (empty)
- Quarkus is NOT merging user-provided jni-config
- C++ SndCtrl.dll calls back into Java via JNI on: SndCtrlWindows, WindowsAudioDevice, AudioDevice, AudioSession
- WITHOUT JNI registration, GetMethodID returns NULL and callbacks silently fail

## Plan

### Step 1: Verify current state of files
- Check application.properties
- Check existing META-INF/native-image directory

### Step 2: Fix JNI Registration (Primary Fix)
Option A: Use quarkus.native.additional-build-args with -H:JNIConfigurationFiles
Option B: Create proper native-image.properties in subdirectory  
Option C: Create Quarkus @BuildStep with JniRuntimeAccessBuildItem (PREFERRED)

Going with Option C as it's the proper Quarkus way.

### Step 3: Verify resource includes work for nested DLLs
- Check if **/*.dll is supported or if we need explicit patterns

### Step 4: Rebuild and test

## Progress Log

### 15:05 - Session resumed (new prompt)
- User confirmed: port issue (8080) is NOT a problem, don't worry about it
- Main focus: DEVICES not showing up
- Ran the executable - it starts, frontend works, but no devices
- From app startup log: "No scheduled business methods found" - still an issue
- `/api/devices` → need to check if it's 404 or empty list
- The executable is 65MB (from 2:47 PM build) - larger than before, DLLs are bundled
- JNI config fix is still needed (Quarkus overwrites our jni-config.json)

### 15:06 - Root cause found!
- /api/devices returns 404 (NOT empty list)
- "No scheduled business methods found" 
- target/classes is EMPTY (no compiled classes!)
- runner JAR has ZERO getpcpanel classes
- ROOT CAUSE: Native image was built WITHOUT application classes
  → Quarkus augmentation found nothing to scan
  → No CDI beans, no REST resources, no scheduled methods
  → Just the core extensions (quinoa frontend works)

### 15:12 - Investigation of cause + Fixes applied
- 313 classes compile fine with mvnw compile
- target/classes was empty because the PREVIOUS build either ran clean then failed to compile, or ran package without compile
- The quarkus-app app JAR is only 325 bytes (empty - confirms no classes during prev build)
- ROOT CAUSE OF ALL RUNTIME ISSUES: empty target/classes during Quarkus augmentation

### 15:14 - JNI config fix applied
- Created META-INF/native-image/com.getpcpanel/pcpanel/native-image.properties
- Created META-INF/native-image/com.getpcpanel/pcpanel/jni-config.json
- WHY THIS WORKS: GraalVM auto-discovers all META-INF/native-image/*/native-image.properties dirs and MERGES their configs
- Quarkus overwrites META-INF/native-image/jni-config.json (empty [])
- Our file at com.getpcpanel/pcpanel/jni-config.json will NOT be overwritten (different path)
- Result: both configs merged, 4 JNI classes registered

### 15:16 - Build completed successfully!
BUILD SUCCESS in 01:18 min

Key metrics (MAJOR IMPROVEMENT vs previous):
- 17,300 types reachable (was ~5000 in empty build)
- 5,540 types for reflection (was ~1900)
- 158 types / 233 methods for JNI (was 79 types / 68 methods) ← JNI config WORKING!
- 210 resources (was 75) ← **/*.dll pattern WORKING, DLLs bundled!
- 83.32MB image (was 65MB) ← 2.34MB from pcpanel-runner.jar (app code is IN there now)

Warning: "Unknown attribute(s) [comment]" - jni-config.json uses "comment" field which isn't valid
  → Harmless warning, but should clean up the jni-config.json (remove "comment" fields)

### 15:19 - Two new errors found!

**Error 1: JNA jnidispatch.dll fails to initialize**
```
UnsatisfiedLinkError: Unsupported JNI version 0x0
Caused by: FindClass("java/lang/Object") returning null
```
Root cause: GraalVM native image requires ALL classes accessed via JNI to be registered in jni-config.json.
Even java.lang.Object must be explicitly registered!
jnidispatch.dll's JNI_OnLoad calls FindClass for: Object, String, Class, various NIO buffers, JNA classes.
→ Fix: Add core Java classes to JNI config

**Error 2: Jackson can't deserialize Save class**
```
Cannot construct instance of `com.getpcpanel.profile.Save`: 
cannot deserialize from Object value (no delegate- or property-based Creator)
```
Root cause: Save class not registered for reflection in native image
→ Fix: Add Save and related profile classes to @RegisterForReflection

### 15:25 - Fixes applied, rebuilding

**Changes made:**
1. JNI config (com.getpcpanel/pcpanel/jni-config.json):
   - Added core Java classes: Object, Class, String, Number, all boxed types, all NIO buffers
   - Added JNA classes: Pointer, Memory, NativeLong, WString, Structure, Callback, etc.
   - These are needed by jnidispatch.dll's JNI_OnLoad
   
2. NativeImageConfig.java:
   - Added Save, DeviceSave, Profile, LightingConfig + LightingMode, all Single*LightingConfig + enums
   - Added KnobSetting, MqttSettings, WaveLinkSettings, OSCConnectionInfo, OSCBinding, OverlayPosition
   - Jackson can now deserialize the user's save file!

3. DeviceScanner.java:
   - Wrapped init() in try-catch in onStart() observer
   - Prevents app crash if HID init fails (defensive coding)

### 15:27 - Build in progress (background)
- Native image compilation takes ~1 minute 
- Waiting for output from background terminal
- While waiting, analyzing what other classes may need registration:
  - CommandMapDeserializer - uses reflection to read command classes → already uses class names in JSON
  - All Command subclasses already in @RegisterForReflection
  - Profile model classes just added
  - TODO after build: Check for more Jackson "cannot construct" errors
  - If jnidispatch.dll fix works, expect: HID devices discovered at startup

## SESSION 2 RESUMED (new prompt)

### User feedback:
- mvn quarkus:build produces JVM-only build, does NOT run native compilation → no progress
- Application runs but still no devices (the JVM build doesn't have the fixes we applied)
- User wants: if we build differently (package -Pnative), update pom.xml to do that by default

### Plan:
1. Run `mvnw package -Pnative` to get a fresh native build with all previous fixes
2. Test the executable - look for UnsatisfiedLinkError, Jackson errors, device issues
3. Fix any new errors found
4. Update pom.xml so that `mvn package` also produces the native build (or at minimum document the correct command)

## Build 4 results (15:47)

**HUGE PROGRESS:**
- App starts on correct port 7654 ✅
- /api/devices returns [] (empty list, not 404) ✅ - REST endpoint works!
- JNI: 187 types / 1756 methods (was 158/233 in build 3)
- All features installed: cdi, rest, scheduler, etc. ✅
- pom.xml updated: native profile now `activeByDefault=true` ✅

**Still broken:**
1. `JNA: Problems loading core IDs: java.lang.reflect.Method` (stderr)
   - JNA's native jnidispatch.dll calls FindClass("java/lang/reflect/Method") → NULL
   - This breaks JNA's callback mechanism → device connect/disconnect events never fire
   - Fix: add java.lang.reflect.Method/Field/Constructor/InvocationHandler etc. to JNI config
   
2. `Failed to get charset for native.encoding value: 'Cp1252'` 
   - Quarkus sets -H:-AddAllCharsets, Cp1252 not in native image
   - Fix: add quarkus.native.add-all-charsets=true to pom.xml native profile

**Changes made for build 5:**
- jni-config.json: added java.lang.reflect.{Method, Field, Constructor, AccessibleObject, Proxy, InvocationHandler} + java.lang.ref.{WeakReference, Reference} + java.util.{Collection, ArrayList}
- pom.xml: added quarkus.native.add-all-charsets=true

### 16:05 - Build 5 starting

## Build 5 results (16:01)

**New error found:**
```
UnsatisfiedLinkError: Can't obtain static method dispose from class com.sun.jna.Native
    at com.sun.jna.Native.initIDs(Native Method)
    at com.sun.jna.Native.<clinit>
```
Root cause: `com.sun.jna.Native` was NOT in jni-config.json. JNA's native initIDs() needs
all JNA classes registered so FindClass/GetMethodID succeed.

**Fixed:**
- Cp1252 charset warning: GONE ✅ (add-all-charsets=true works)  
- "Problems loading core IDs: java.lang.reflect.Method": GONE ✅
- pom.xml: moved native properties to main section → `mvn package` now builds native ✅

**JNI config v6 (build 6):**
- Added: java.lang.ClassLoader
- Added: com.sun.jna.Native (the main JNA class - was missing!)
- Added: com.sun.jna.Function, NativeLibrary, Platform, PointerType, Union
- Added: com.sun.jna.Structure$ByReference, ByValue, FFIType
- Added: com.sun.jna.Callback$UncaughtExceptionHandler
- Added: com.sun.jna.CallbackReference$DefaultCallbackProxy, CallbackThreadInitializer
- Added: com.sun.jna.ptr.* (ByteByReference, IntByReference, LongByReference, etc.)
- Added: com.sun.jna.win32.StdCallLibrary, StdCallLibrary$StdCallCallback

### 16:08 - Build 6 running


