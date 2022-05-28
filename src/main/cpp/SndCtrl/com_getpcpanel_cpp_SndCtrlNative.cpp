#include "pch.h"
#include "com_getpcpanel_cpp_SndCtrlNative.h"
#include <iostream>
#include "SndCtrl.h"

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
JNIEXPORT void JNICALL Java_com_getpcpanel_cpp_SndCtrlNative_start(JNIEnv*, jclass, jobject obj) {
    pSndCtrl = make_unique<SndCtrl>(obj);
}

/*
 * Class:     com_getpcpanel_cpp_SndCtrlNative
 * Method:    setDeviceVolume
 * Signature: (Ljava/lang/String;F)V
 */
JNIEXPORT void JNICALL Java_com_getpcpanel_cpp_SndCtrlNative_setDeviceVolume(JNIEnv* env, jobject, jstring jDeviceId, jfloat volume) {
    auto deviceId = str(env, jDeviceId);
    pSndCtrl->SetDeviceVolume(deviceId, volume);
}

/*
 * Class:     com_getpcpanel_cpp_SndCtrlNative
 * Method:    setProcessVolume
 * Signature: (Ljava/lang/String;IF)V
 */
JNIEXPORT void JNICALL Java_com_getpcpanel_cpp_SndCtrlNative_setProcessVolume(JNIEnv* env, jobject, jstring jDeviceId, jint pid, jfloat volume) {
    auto deviceId = str(env, jDeviceId);
    pSndCtrl->SetProcessVolume(deviceId, pid, volume);
}

/*
 * Class:     com_getpcpanel_cpp_SndCtrlNative
 * Method:    setFocusVolume
 * Signature: (F)V
 */
JNIEXPORT void JNICALL Java_com_getpcpanel_cpp_SndCtrlNative_setFocusVolume(JNIEnv*, jobject, jfloat volume) {
    pSndCtrl->SetFocusVolume(volume);
}
