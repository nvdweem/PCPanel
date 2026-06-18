#include "pch.h"
#include "JniCaller.h"
#include <vector>

JavaVM* pJvm;
std::string name("JNI Callback");
JavaVMAttachArgs JThread::attachArgs{ JNI_VERSION_10, &name[0] };

#ifndef NO_JNI
jstring JThread::jstr(const char* str) {
    return pEnv->NewStringUTF(str);
}

jstring JThread::jstr(const WCHAR* str) {
    // Convert the wide string to the system code page, matching the previous
    // ATL _bstr_t(const wchar_t*) behaviour, then hand it to NewStringUTF. This
    // drops the <comdef.h>/_bstr_t dependency (MSVC's COM support layer) so the
    // DLL builds without Visual Studio.
    if (str == nullptr) {
        return jstr("");
    }
    int needed = WideCharToMultiByte(CP_ACP, 0, str, -1, nullptr, 0, nullptr, nullptr);
    if (needed <= 0) {
        return jstr("");
    }
    std::vector<char> buffer(needed);
    WideCharToMultiByte(CP_ACP, 0, str, -1, buffer.data(), needed, nullptr, nullptr);
    return jstr(buffer.data());
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
