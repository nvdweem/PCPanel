#include "pch.h"
#include "com_getpcpanel_cpp_SndCtrlNative.h"
#include "JniCaller.h"
#include "SndCtrl.h"
#include "helpers.h"
#include <iostream>
#include <psapi.h>
#include <set>
#include <windows.h>

std::wstring str(JNIEnv* env, jstring string)
{
    std::wstring value;
    if (string == NULL) {
        return value; // empty string
    }
    const jchar* raw = env->GetStringChars(string, NULL);
    if (raw != NULL) {
        jsize len = env->GetStringLength(string);
        value.assign(raw, raw + len);
        env->ReleaseStringChars(string, raw);
    }
    return value;
}

/*
 * Class:     com_getpcpanel_cpp_SndCtrlNative
 * Method:    start
 * Signature: (Ljava/lang/Object;)V
 */
JNIEXPORT void JNICALL Java_com_getpcpanel_cpp_SndCtrlNative_start(JNIEnv* env, jclass, jobject obj) {
    //cout << "Java_com_getpcpanel_cpp_SndCtrlNative_start" << endl;
    pSndCtrl = make_unique<SndCtrl>(env, obj);
}

/*
 * Class:     com_getpcpanel_cpp_SndCtrlNative
 * Method:    setDeviceVolume
 * Signature: (Ljava/lang/String;F)V
 */
JNIEXPORT void JNICALL Java_com_getpcpanel_cpp_SndCtrlNative_setDeviceVolume(JNIEnv* env, jobject, jstring jDeviceId, jfloat volume) {
    //cout << "Java_com_getpcpanel_cpp_SndCtrlNative_setDeviceVolume" << endl;
    auto deviceId = str(env, jDeviceId);
    pSndCtrl->SetDeviceVolume(deviceId, volume);
}

/*
 * Class:     com_getpcpanel_cpp_SndCtrlNative
 * Method:    setProcessVolume
 * Signature: (Ljava/lang/String;IF)V
 */
JNIEXPORT void JNICALL Java_com_getpcpanel_cpp_SndCtrlNative_setProcessVolume(JNIEnv* env, jobject, jstring jDeviceId, jint pid, jfloat volume) {
    //cout << "Java_com_getpcpanel_cpp_SndCtrlNative_setProcessVolume" << endl;
    auto deviceId = str(env, jDeviceId);
    pSndCtrl->SetProcessVolume(deviceId, pid, volume);
}

/*
 * Class:     com_getpcpanel_cpp_SndCtrlNative
 * Method:    setFocusVolume
 * Signature: (F)V
 */
JNIEXPORT void JNICALL Java_com_getpcpanel_cpp_SndCtrlNative_setFocusVolume(JNIEnv*, jobject, jfloat volume) {
    //cout << "Java_com_getpcpanel_cpp_SndCtrlNative_setFocusVolume" << endl;
    pSndCtrl->SetFocusVolume(volume);
}

/*
 * Class:     com_getpcpanel_cpp_SndCtrlNative
 * Method:    setDefaultDevice
 * Signature: (Ljava/lang/String;)V
 */
JNIEXPORT void JNICALL Java_com_getpcpanel_cpp_SndCtrlNative_setDefaultDevice(JNIEnv* env, jobject, jstring jDevice, jint dataFlow, jint role) {
    //cout << "Java_com_getpcpanel_cpp_SndCtrlNative_setDefaultDevice" << endl;
    auto device = str(env, jDevice);
    pSndCtrl->UpdateDefaultDevice(device, (EDataFlow) dataFlow, (ERole) role);
}

/*
 * Class:     com_getpcpanel_cpp_SndCtrlNative
 * Method:    muteDevice
 * Signature: (Ljava/lang/String;)V
 */
JNIEXPORT void JNICALL Java_com_getpcpanel_cpp_SndCtrlNative_muteDevice(JNIEnv* env, jobject, jstring jDevice, jboolean muted) {
    //cout << "Java_com_getpcpanel_cpp_SndCtrlNative_muteDevice" << endl;
    auto device = str(env, jDevice);
    pSndCtrl->MuteDevice(device, muted);
}

/*
 * Class:     com_getpcpanel_cpp_SndCtrlNative
 * Method:    muteSession
 * Signature: (Ljava/lang/String;I)V
 */
JNIEXPORT void JNICALL Java_com_getpcpanel_cpp_SndCtrlNative_muteSession(JNIEnv* env, jobject, jstring jDevice, jint pid, jboolean muted) {
    //cout << "Java_com_getpcpanel_cpp_SndCtrlNative_muteSession" << endl;
    auto device = str(env, jDevice);
    pSndCtrl->MuteProcess(device, pid, muted);
}

/*
 * Class:     com_getpcpanel_cpp_SndCtrlNative
 * Method:    getFocusApplication
 * Signature: ()Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL Java_com_getpcpanel_cpp_SndCtrlNative_getFocusApplication(JNIEnv* env, jobject) {
    //cout << "Java_com_getpcpanel_cpp_SndCtrlNative_getFocusApplication" << endl;
    auto pid = GetFocusProcessId();
    auto name = GetProcessName(pid);
    return env->NewString((jchar*) name.c_str(), (jsize) name.length());
}

/*
 * Class:     com_getpcpanel_cpp_SndCtrlNative
 * Method:    addAllRunningProcesses
 * Signature: (Ljava/util/Set;)V
 */
JNIEXPORT void JNICALL Java_com_getpcpanel_cpp_SndCtrlNative_addAllRunningProcesses(JNIEnv* env, jobject, jobject target) {
    DWORD aProcesses[1024], cbNeeded;

    if (!EnumProcesses(aProcesses, sizeof(aProcesses), &cbNeeded))
        return;

    auto cProcesses = cbNeeded / sizeof(DWORD);
    set<wstring> seen;

    JThread thread(env);
    JniCaller caller(thread, target);

    for (auto i = 0; i < cProcesses; i++) {
        if (aProcesses[i] != 0) {
            auto pid = aProcesses[i];
            auto szProcessName = GetProcessName(pid);
            if (seen.find(szProcessName) != seen.end()) {
                continue;
            }
            seen.insert(szProcessName);

            auto fileName = env->NewString((jchar*)szProcessName.c_str(), (jsize)szProcessName.length());
            caller.CallBoolean(thread, "add", "(Ljava/lang/Object;)Z", fileName);
        }
    }
}
