# SndCtrl.dll — building

`SndCtrl.dll` is the Windows Core Audio control library that the PCPanel native
image loads over JNI (per-process/device volume, mute, default device, focus
app). The compiled DLL is committed at `src/main/resources/SndCtrl.dll`; the
Maven/CI build only bundles it — it does **not** rebuild it. You only need to
rebuild this DLL when you change the C++ sources under `cpp/SndCtrl/`.

It used to require a full **Visual Studio** install (for the `.sln`/`.vcxproj`
project and, more fundamentally, for **ATL**). That's no longer the case: the
sources are now compiler-portable and build with **CMake**, so all you need is
the standalone C++ compiler — **no Visual Studio IDE, no ATL**.

---

## Quick start (Windows, recommended): one command

From this directory (`src/main/cpp`), in PowerShell:

```powershell
# First time — installs everything it needs, then builds:
powershell -ExecutionPolicy Bypass -File build-windows.ps1 -InstallTools

# Afterwards — just rebuild:
powershell -ExecutionPolicy Bypass -File build-windows.ps1
```

`build-windows.ps1 -InstallTools` will, via `winget`:

1. install **CMake**,
2. install the **Visual Studio C++ Build Tools** (the compiler only — no IDE),
3. install a **JDK** if none is found (needed only for the JNI headers),

and then configure + build, copying the result to `src/main/resources/SndCtrl.dll`.
Without `-InstallTools` it skips steps 1–3's installs and just builds.

That's it. The rest of this file is for doing the same thing manually, or for
building without any Microsoft tooling at all.

---

## What you actually need (manual Windows build)

Three things, all installable by download-and-double-click or by `winget`:

| Tool | Why | Install |
| --- | --- | --- |
| **Visual Studio C++ Build Tools 2022** | the `cl.exe` compiler + linker + Windows SDK (no IDE) | [download](https://visualstudio.microsoft.com/downloads/#build-tools-for-visual-studio-2022) and tick **"Desktop development with C++"**, or `winget install Microsoft.VisualStudio.2022.BuildTools --override "--quiet --add Microsoft.VisualStudio.Workload.VCTools --includeRecommended"` |
| **CMake** ≥ 3.21 | drives the build | [download](https://cmake.org/download/) or `winget install Kitware.CMake` |
| **A JDK** | the `jni.h` / `win32\jni_md.h` headers | any Windows JDK; e.g. `winget install Microsoft.OpenJDK.21`. Point `JAVA_HOME` at it. |

> You do **not** need the ATL component or the full Visual Studio IDE anymore.

Then build:

```powershell
cmake -S . -B build -G "Visual Studio 17 2022" -A x64 -DWIN_JDK_HOME="$env:JAVA_HOME"
cmake --build build --config Release
```

The DLL is written to `build\Release\SndCtrl.dll` and copied to
`src\main\resources\SndCtrl.dll` (pass `-DCOPY_TO_RESOURCES=OFF` to skip the copy).
An MSVC build is ~70 KB and depends only on DLLs already present on Windows.

The legacy `SndCtrl.sln` / `.vcxproj` still work too (open in VS or build with
MSBuild), and the `SndCtrlTest` harness is still there — but CMake is now the
recommended, IDE-free path.

---

## Building without any Microsoft tooling (MinGW-w64 / Linux cross-compile)

If you'd rather not install Microsoft's compiler at all, MinGW-w64 builds the
same DLL — on Windows *or* cross-compiled from Linux.

```bash
# Debian/Ubuntu
sudo apt-get install -y g++-mingw-w64-x86-64 cmake

cmake -B build -S . \
      -DCMAKE_TOOLCHAIN_FILE="$PWD/mingw-w64-x86_64.toolchain.cmake" \
      -DWIN_JDK_HOME=/path/to/windows-jdk
cmake --build build
```

You still need the **Windows** JNI headers (`include/` + `include/win32/`) — a
Linux JDK's headers won't do (its `jni_md.h` makes `jlong` 32-bit on Windows and
omits the `__declspec(dllexport)` exports). Just unzip any Windows JDK and point
`WIN_JDK_HOME` at it; no install needed.

Trade-off: a MinGW build statically links the GCC/C++/pthread runtimes (so it
needs no extra runtime DLLs at load time) and is therefore ~1 MB stripped, vs
~70 KB for MSVC. For a desktop app bundled with the native image that size is
irrelevant. The much larger alternative — dynamic linking — would force shipping
`libstdc++-6.dll` (~26 MB) alongside, so static is deliberately the leaner
choice here.

---

## How it was made VS-independent

All changes are `#ifdef`-guarded or behaviour-preserving, so the MSVC build is
functionally unchanged:

| Was (VS-only) | Now |
| --- | --- |
| ATL `CComPtr` / `CComQIPtr` (`<atlbase.h>`) | `comptr_compat.h`, a portable shim with identical refcount semantics |
| `_bstr_t` from `<comdef.h>` | a `WideCharToMultiByte` conversion in `JniCaller.cpp` |
| `__uuidof` reading `DECLSPEC_UUID` | explicit `__CRT_UUID_DECL(...)`, guarded by `__MINGW32__` |
| MSVC's implicit transitive std / `tchar` includes | explicit `#include`s in `pch.h` |
| `SndCtrl.sln` / MSBuild | `CMakeLists.txt` (drives MSVC or MinGW) |

---

## Verifying

There is no automated runtime test (it needs real audio hardware and a JVM).
After building, sanity-check the export surface — it must match the committed
DLL exactly (11 `Java_com_getpcpanel_integration_volume_platform_windows_SndCtrlNative_*` entry points
plus `JNI_OnLoad` / `JNI_OnUnload`):

```powershell
dumpbin /exports build\Release\SndCtrl.dll      # MSVC
# or, with MinGW tools:
x86_64-w64-mingw32-objdump -p build/SndCtrl.dll | findstr "Java_ JNI_On"
```

Final confirmation requires running the app on Windows against PCPanel hardware.
