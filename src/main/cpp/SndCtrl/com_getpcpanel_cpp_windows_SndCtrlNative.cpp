#include "pch.h"
#include "com_getpcpanel_cpp_windows_SndCtrlNative.h"
#include "JniCaller.h"
#include "SndCtrl.h"
#include "helpers.h"
#include <iostream>
#include <psapi.h>
#include <set>
#include <windows.h>
#include <string>

std::wstring str(JNIEnv* env, jstring string) {
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
JNIEXPORT void JNICALL Java_com_getpcpanel_cpp_windows_SndCtrlNative_start(JNIEnv* env, jclass, jobject obj) {
    //cout << "Java_com_getpcpanel_cpp_SndCtrlNative_start" << endl;
    pSndCtrl = make_unique<SndCtrl>(env, obj);
}

/*
 * Class:     com_getpcpanel_cpp_SndCtrlNative
 * Method:    setDeviceVolume
 * Signature: (Ljava/lang/String;F)V
 */
JNIEXPORT void JNICALL Java_com_getpcpanel_cpp_windows_SndCtrlNative_setDeviceVolume(JNIEnv* env, jobject, jstring jDeviceId, jfloat volume) {
    //cout << "Java_com_getpcpanel_cpp_SndCtrlNative_setDeviceVolume" << endl;
    auto deviceId = str(env, jDeviceId);
    pSndCtrl->SetDeviceVolume(deviceId, volume);
}

/*
 * Class:     com_getpcpanel_cpp_SndCtrlNative
 * Method:    setProcessVolume
 * Signature: (Ljava/lang/String;IF)V
 */
JNIEXPORT void JNICALL Java_com_getpcpanel_cpp_windows_SndCtrlNative_setProcessVolume(JNIEnv* env, jobject, jstring jDeviceId, jint pid, jfloat volume) {
    //cout << "Java_com_getpcpanel_cpp_SndCtrlNative_setProcessVolume" << endl;
    auto deviceId = str(env, jDeviceId);
    pSndCtrl->SetProcessVolume(deviceId, pid, volume);
}

/*
 * Class:     com_getpcpanel_cpp_SndCtrlNative
 * Method:    setFocusVolume
 * Signature: (F)V
 */
JNIEXPORT void JNICALL Java_com_getpcpanel_cpp_windows_SndCtrlNative_setFocusVolume(JNIEnv*, jobject, jfloat volume) {
    //cout << "Java_com_getpcpanel_cpp_SndCtrlNative_setFocusVolume" << endl;
    pSndCtrl->SetFocusVolume(volume);
}

/*
 * Class:     com_getpcpanel_cpp_SndCtrlNative
 * Method:    setDefaultDevice
 * Signature: (Ljava/lang/String;)V
 */
JNIEXPORT void JNICALL Java_com_getpcpanel_cpp_windows_SndCtrlNative_setDefaultDevice(JNIEnv* env, jobject, jstring jDevice, jint dataFlow, jint role) {
    //cout << "Java_com_getpcpanel_cpp_SndCtrlNative_setDefaultDevice" << endl;
    auto device = str(env, jDevice);
    pSndCtrl->UpdateDefaultDevice(device, (EDataFlow) dataFlow, (ERole) role);
}

/*
 * Class:     com_getpcpanel_cpp_SndCtrlNative
 * Method:    muteDevice
 * Signature: (Ljava/lang/String;)V
 */
JNIEXPORT void JNICALL Java_com_getpcpanel_cpp_windows_SndCtrlNative_muteDevice(JNIEnv* env, jobject, jstring jDevice, jboolean muted) {
    //cout << "Java_com_getpcpanel_cpp_SndCtrlNative_muteDevice" << endl;
    auto device = str(env, jDevice);
    pSndCtrl->MuteDevice(device, muted);
}

/*
 * Class:     com_getpcpanel_cpp_SndCtrlNative
 * Method:    muteSession
 * Signature: (Ljava/lang/String;I)V
 */
JNIEXPORT void JNICALL Java_com_getpcpanel_cpp_windows_SndCtrlNative_muteSession(JNIEnv* env, jobject, jstring jDevice, jint pid, jboolean muted) {
    //cout << "Java_com_getpcpanel_cpp_SndCtrlNative_muteSession" << endl;
    auto device = str(env, jDevice);
    pSndCtrl->MuteProcess(device, pid, muted);
}

/*
 * Class:     com_getpcpanel_cpp_SndCtrlNative
 * Method:    getFocusApplication
 * Signature: ()Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL Java_com_getpcpanel_cpp_windows_SndCtrlNative_getFocusApplication(JNIEnv* env, jobject) {
    //cout << "Java_com_getpcpanel_cpp_SndCtrlNative_getFocusApplication" << endl;
    auto pid = GetFocusProcessId();
    auto name = GetProcessName(pid);
    return env->NewString((jchar*) name.c_str(), (jsize) name.length());
}

/*
 * Class:     com_getpcpanel_cpp_windows_SndCtrlNative
 * Method:    setPersistedDefaultAudioEndpoint
 * Signature: (IILjava/lang/String;)Z
 */
JNIEXPORT jboolean JNICALL Java_com_getpcpanel_cpp_windows_SndCtrlNative_setPersistedDefaultAudioEndpoint(JNIEnv* env, jobject, jint pid, jint flow, jstring jDeviceId) {
    auto device = str(env, jDeviceId);
    return pSndCtrl->SetPersistedDefaultAudioEndpoint(pid, (EDataFlow) flow, device);
}

/*
 * Class:     com_getpcpanel_cpp_windows_SndCtrlNative
 * Method:    getPersistedDefaultAudioEndpoint
 * Signature: (II)Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL Java_com_getpcpanel_cpp_windows_SndCtrlNative_getPersistedDefaultAudioEndpoint(JNIEnv* env, jobject, jint pid, jint flow) {
    auto result = pSndCtrl->GetPersistedDefaultAudioEndpoint(pid, (EDataFlow) flow);
    return env->NewString((jchar*) result.c_str(), (jsize)result.length());
}

/*
 * Class:     com_getpcpanel_cpp_windows_SndCtrlNative
 * Method:    hasAudioPolicyConfigFactory
 * Signature: ()Z
 */
JNIEXPORT jboolean JNICALL Java_com_getpcpanel_cpp_windows_SndCtrlNative_hasAudioPolicyConfigFactory(JNIEnv*, jobject) {
    return pSndCtrl->HasAudioPolicyConfigFactory();
}

/*
 * Class:     com_getpcpanel_cpp_windows_SndCtrlNative
 * Method:    triggerAv
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_com_getpcpanel_cpp_windows_SndCtrlNative_triggerAv(JNIEnv*, jobject) {
    new std::thread([](){
        Sleep(1000);
        pSndCtrl->TriggerAv();
    });
}
