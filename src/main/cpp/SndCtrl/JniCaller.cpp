#include "pch.h"
#include "JniCaller.h"
#include <comdef.h>

JavaVM* pJvm;
std::string name("JNI Callback");
JavaVMAttachArgs JThread::attachArgs{ JNI_VERSION_10, &name[0] };

#ifndef NO_JNI
jstring JThread::jstr(const char* str) {
    return pEnv->NewStringUTF(str);
}

jstring JThread::jstr(const WCHAR* str) {
    _bstr_t b(str);
    const char* c = b;
    return jstr(c);
}

void JThread::jstr(jstring str) {
    DoneWith(str);
}

void JThread::DoneWith(jobject obj) {
    pEnv->DeleteLocalRef(obj);
}

void JniCaller::CheckException(JThread& env) const {
    if (env->ExceptionCheck()) {
        env->ExceptionDescribe();
        env->ExceptionClear();
    }
}
#else
jstring JThread::jstr(const char* str) {
    return nullptr;
}

jstring JThread::jstr(const WCHAR* str) {
    return nullptr;
}

void JThread::jstr(jstring str) {}

void JThread::DoneWith(jobject obj) {}
#endif
