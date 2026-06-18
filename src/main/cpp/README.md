# SndCtrl.dll — building without Visual Studio

`SndCtrl.dll` is the Windows Core Audio control library that the PCPanel native
image loads over JNI (per-process/device volume, mute, default device, focus
app). The compiled DLL is committed at `src/main/resources/SndCtrl.dll`; the
Maven/CI build only bundles it, it does not rebuild it.

Historically this DLL required a full **Visual Studio** install (for the
`SndCtrl.sln` / `.vcxproj` project and, more fundamentally, for **ATL**). The
sources have been made compiler-portable, so it now builds with a plain C++
compiler — including a cross-compile from Linux with **MinGW-w64**. The old
Visual Studio solution still works and is unchanged.

## What made it VS-only, and what replaced it

| Was | Now |
| --- | --- |
| ATL `CComPtr` / `CComQIPtr` (`<atlbase.h>`, ships only with VS) | `comptr_compat.h`, a small portable shim with identical refcount semantics |
| `_bstr_t` from `<comdef.h>` (MSVC COM support) | a `WideCharToMultiByte` conversion in `JniCaller.cpp` |
| `__uuidof` reading GUIDs from `DECLSPEC_UUID` (MSVC) | explicit `__CRT_UUID_DECL(...)` for the custom interfaces, guarded by `__MINGW32__` |
| MSVC's implicit transitive std/`tchar` includes | explicit `#include`s in `pch.h` |
| `SndCtrl.sln` / MSBuild | `CMakeLists.txt` (also drives MSVC if you prefer) |

These changes are all `#ifdef`-guarded or behaviour-preserving, so the MSVC
build is unaffected.

## Cross-compiling on Linux

Prerequisites (Debian/Ubuntu):

```bash
sudo apt-get install -y g++-mingw-w64-x86-64 cmake
```

You also need the **Windows** JNI headers (`include/jni.h` + `include/win32/jni_md.h`).
A Linux JDK's headers will **not** work: its `jni_md.h` makes `jlong` 32-bit on
Windows (ABI mismatch with the JVM) and does not mark the exports with
`__declspec(dllexport)`. Just unzip any Windows JDK somewhere — no install
needed — and point `WIN_JDK_HOME` at it.

```bash
cmake -B build -S src/main/cpp \
      -DCMAKE_TOOLCHAIN_FILE="$PWD/src/main/cpp/mingw-w64-x86_64.toolchain.cmake" \
      -DWIN_JDK_HOME=/path/to/windows-jdk
cmake --build build
```

By default the resulting `SndCtrl.dll` is copied over
`src/main/resources/SndCtrl.dll` (`-DCOPY_TO_RESOURCES=OFF` to disable). The DLL
statically links the GCC/C++/pthread runtimes, so it has no dependency on
`libstdc++-6.dll`/`libgcc_s_seh-1.dll`/`libwinpthread-1.dll` at load time
(verify with `x86_64-w64-mingw32-objdump -p build/SndCtrl.dll | grep 'DLL Name'`).

It is larger than the MSVC build (~1 MB stripped vs ~70 KB) because of those
static runtimes; that is expected and harmless.

## Building natively on Windows with CMake (still no VS solution needed)

With a Windows JDK on `JAVA_HOME`, either toolchain works:

```bash
# MSVC (from a "x64 Native Tools" prompt)
cmake -B build -S src/main/cpp -G Ninja
cmake --build build

# or MinGW-w64
cmake -B build -S src/main/cpp -G "MinGW Makefiles"
cmake --build build
```

## Verifying

There is no automated runtime test (it needs real audio hardware and a JVM).
After building, sanity-check the artifact:

```bash
x86_64-w64-mingw32-objdump -p build/SndCtrl.dll | grep -E 'Java_com_getpcpanel|JNI_OnLoad'
```

All 11 `Java_com_getpcpanel_cpp_windows_SndCtrlNative_*` entry points plus
`JNI_OnLoad`/`JNI_OnUnload` must be exported. Final confirmation requires
running the app on Windows against PCPanel hardware.
