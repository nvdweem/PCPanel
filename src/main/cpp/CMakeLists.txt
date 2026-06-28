cmake_minimum_required(VERSION 3.16)
project(SndCtrl CXX)

# ---------------------------------------------------------------------------
# Builds SndCtrl.dll, the Windows Core Audio control library loaded over JNI by
# the PCPanel native image. It can be built either natively on Windows (MSVC or
# MinGW) or cross-compiled from Linux with MinGW-w64 — see cpp/README.md and the
# mingw-w64-x86_64.toolchain.cmake toolchain file.
# ---------------------------------------------------------------------------

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# ---------------------------------------------------------------------------
# JNI headers. We need the *Windows* JNI headers (include/ + include/win32/),
# because jni_md.h defines jlong as a 64-bit type and JNIEXPORT as
# __declspec(dllexport) on Windows — using a Linux JDK's headers would both
# break the ABI (jlong -> 32-bit) and fail to export the JNI entry points.
#
# Resolution order: an explicit -DJNI_INCLUDE_DIRS, then WIN_JDK_HOME (cache or
# env), then JAVA_HOME (handy for native Windows builds where it already points
# at a Windows JDK). For cross builds, extract any Windows JDK and point
# WIN_JDK_HOME at it (no install needed).
# ---------------------------------------------------------------------------
if(NOT JNI_INCLUDE_DIRS)
    set(_jdk_home "")
    if(WIN_JDK_HOME)
        set(_jdk_home "${WIN_JDK_HOME}")
    elseif(DEFINED ENV{WIN_JDK_HOME})
        set(_jdk_home "$ENV{WIN_JDK_HOME}")
    elseif(DEFINED ENV{JAVA_HOME})
        set(_jdk_home "$ENV{JAVA_HOME}")
    endif()

    if(_jdk_home)
        set(JNI_INCLUDE_DIRS "${_jdk_home}/include" "${_jdk_home}/include/win32")
    endif()
endif()

if(NOT JNI_INCLUDE_DIRS)
    message(FATAL_ERROR
        "Could not locate the Windows JNI headers. Set -DWIN_JDK_HOME=<windows-jdk> "
        "(or -DJNI_INCLUDE_DIRS=\"<jdk>/include;<jdk>/include/win32\").")
endif()

list(GET JNI_INCLUDE_DIRS 0 _jni_main_include)
if(NOT EXISTS "${_jni_main_include}/jni.h")
    message(FATAL_ERROR "jni.h not found in ${_jni_main_include}. Check WIN_JDK_HOME / JNI_INCLUDE_DIRS.")
endif()
if(NOT EXISTS "${_jni_main_include}/win32/jni_md.h")
    message(FATAL_ERROR
        "win32/jni_md.h not found under ${_jni_main_include}. The Windows JDK headers are "
        "required (a Linux JDK would produce a wrong jlong size / missing exports).")
endif()

# ---------------------------------------------------------------------------
# The DLL.
# ---------------------------------------------------------------------------
set(SNDCTRL_SRC_DIR ${CMAKE_CURRENT_SOURCE_DIR}/SndCtrl)
add_library(SndCtrl SHARED
    ${SNDCTRL_SRC_DIR}/AudioDevice.cpp
    ${SNDCTRL_SRC_DIR}/AudioSession.cpp
    ${SNDCTRL_SRC_DIR}/com_getpcpanel_integration_volume_platform_windows_SndCtrlNative.cpp
    ${SNDCTRL_SRC_DIR}/dllmain.cpp
    ${SNDCTRL_SRC_DIR}/FocusListener.cpp
    ${SNDCTRL_SRC_DIR}/helpers.cpp
    ${SNDCTRL_SRC_DIR}/JniCaller.cpp
    ${SNDCTRL_SRC_DIR}/Listeners.cpp
    ${SNDCTRL_SRC_DIR}/pch.cpp
    ${SNDCTRL_SRC_DIR}/sndctrl.cpp
)

target_include_directories(SndCtrl PRIVATE ${SNDCTRL_SRC_DIR} ${JNI_INCLUDE_DIRS})

# Emit "SndCtrl.dll" (no "lib" prefix) to match the committed artifact.
set_target_properties(SndCtrl PROPERTIES PREFIX "" OUTPUT_NAME "SndCtrl")

# Match the Visual Studio project: Unicode build, target Windows 10 (needed for
# the WinRT AudioPolicyConfig string APIs).
target_compile_definitions(SndCtrl PRIVATE
    UNICODE _UNICODE
    WIN32_LEAN_AND_MEAN
    _WIN32_WINNT=0x0A00
)

# System libraries. ole32/oleaut32: COM; user32: window/event hooks;
# version: file-version info; psapi: process info; runtimeobject + the WinRT
# string runtime (combase): RoGetActivationFactory + WindowsCreateString family.
target_link_libraries(SndCtrl PRIVATE
    ole32
    oleaut32
    user32
    version
    psapi
    runtimeobject
)

# When built with GCC/MinGW, statically link the C++/GCC/pthread runtimes so the
# resulting DLL has no dependency on libstdc++-6.dll / libgcc_s_seh-1.dll /
# libwinpthread-1.dll at load time (it is loaded by the GraalVM native image).
if(MINGW OR CMAKE_CXX_COMPILER_ID STREQUAL "GNU")
    target_link_options(SndCtrl PRIVATE
        -static
        -static-libgcc
        -static-libstdc++
        # Strip symbols for non-debug builds (the static runtimes make the DLL
        # large otherwise). Exports are preserved.
        $<$<NOT:$<CONFIG:Debug>>:-s>
    )
endif()

# Optionally drop the freshly built DLL next to the committed one so a rebuild
# is picked up by the Java/native-image build. On by default.
option(COPY_TO_RESOURCES "Copy SndCtrl.dll into src/main/resources after build" ON)
if(COPY_TO_RESOURCES)
    add_custom_command(TARGET SndCtrl POST_BUILD
        COMMAND ${CMAKE_COMMAND} -E copy_if_different
            $<TARGET_FILE:SndCtrl>
            ${CMAKE_CURRENT_SOURCE_DIR}/../resources/SndCtrl.dll
        COMMENT "Copying SndCtrl.dll to src/main/resources")
endif()
