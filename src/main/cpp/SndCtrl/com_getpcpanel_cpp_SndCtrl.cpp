#include "pch.h"
#include "com_getpcpanel_cpp_SndCtrl.h"
#include <iostream>
#include "SndCtrl.h"

JNIEXPORT void JNICALL Java_com_getpcpanel_cpp_SndCtrl_sayHello(JNIEnv*, jobject) {
    std::cout << "Hello from C++ !!!!" << std::endl;
}

JNIEXPORT void JNICALL Java_com_getpcpanel_cpp_SndCtrl_start(JNIEnv* env, jobject obj) {
    pSndCtrl = make_unique<SndCtrl>(obj);
}
