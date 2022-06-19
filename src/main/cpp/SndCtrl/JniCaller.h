#pragma once
#include "helpers.h"
extern JavaVM* pJvm;

class JThread {
private:
    static JavaVMAttachArgs attachArgs;
    JNIEnv* pEnv;
    bool needsDetach;
    bool error;
public:
    JThread(JNIEnv* pEnv) : pEnv(pEnv), needsDetach(false), error(false) {}
    JThread() : pEnv(nullptr), needsDetach(false), error(false) {
#ifndef NO_JNI
        auto getEnvStat = pJvm->GetEnv((void**)&pEnv, JNI_VERSION_10);
        if (getEnvStat == JNI_EDETACHED) {
            if (pJvm->AttachCurrentThreadAsDaemon((void**)&pEnv, &attachArgs) == 0) {
                needsDetach = true;
            } else {
                error = true;
            }
        }
#endif // !NO_JNI
    }
    ~JThread() {
#ifndef NO_JNI
        if (needsDetach) {
            pJvm->DetachCurrentThread();
        }
#endif
    }

    jstring jstr(const char* str);
    jstring jstr(const WCHAR* str);
    void jstr(jstring str);
    void DoneWith(jobject obj);

    JNIEnv* operator->() {
        NOTNULL(pEnv);
        return pEnv;
    }

    bool operator*() {
        if (error) {
            cout << "Env has error :(";
            return false;
        }
        return true;
    }
};


class JniCaller {
private:
    jobject obj;

public:
    static JniCaller Create(jobject obj) {
        NOTNULL(obj);
        JThread env;
        return JniCaller(env, obj);
    }

    JniCaller(JThread& env, jobject obj) {
#ifndef NO_JNI
        if (*env) {
            this->obj = env->NewGlobalRef(obj);
            NOTNULL(this->obj);
        }
#endif
    }
    JniCaller(JNIEnv* env, jobject obj) {
#ifndef NO_JNI
        this->obj = env->NewGlobalRef(obj);
        NOTNULL(this->obj);
#endif
    }
    ~JniCaller() {
#ifndef NO_JNI
        JThread env;
        if (*env) {
            NOTNULL(this->obj);
            env->DeleteGlobalRef(this->obj);
        }
#endif
    }

#ifndef NO_JNI
    void CallVoid(JThread& env, const char* name, const char* sig, ...) {
        if (*env) {
            auto method = GetMethod(env, name, sig);
            va_list args;
            va_start(args, sig);
            env->CallVoidMethodV(obj, method, args);
            va_end(args);
            CheckException(env);
        }
    }

    jobject CallObjectMethodV(JThread& env, const char* name, const char* sig, va_list args) {
        if (*env) {
            auto method = GetMethod(env, name, sig);
            auto result = env->CallObjectMethodV(obj, method, args);
            NOTNULL(result);
            CheckException(env);
            return result;
        }
        return nullptr;
    }

    jobject CallObject(JThread& env, const char* name, const char* sig, ...) {
        va_list args;
        va_start(args, sig);
        auto result = CallObjectMethodV(env, name, sig, args);
        va_end(args);
        return result;
    }

    void CallObjectFreeResult(JThread& env, const char* name, const char* sig, ...) {
        if (*env) {
            va_list args;
            va_start(args, sig);
            env.DoneWith(CallObjectMethodV(env, name, sig, args));
            va_end(args);
        }
    }

    float CallFloat(JThread& env, const char* name, const char* sig, ...) {
        if (*env) {
            auto method = GetMethod(env, name, sig);
            va_list args;
            va_start(args, sig);
            auto result = env->CallFloatMethod(obj, method, args);
            va_end(args);
            CheckException(env);
            return result;
        }
        return 0;
    }
    jboolean CallBoolean(JThread& env, const char* name, const char* sig, ...) {
        if (*env) {
            auto method = GetMethod(env, name, sig);
            va_list args;
            va_start(args, sig);
            auto result = env->CallBooleanMethod(obj, method, args);
            va_end(args);
            CheckException(env);
            return result;
        }
        return false;
    }

private:
    jmethodID GetMethod(JThread& env, const char* name, const char* sig) {
        auto cls = env->GetObjectClass(obj);
        NOTNULL(cls);
        if (!cls) {
            cerr << "Unable to find class for method " << name << "(" << sig << ")" << endl;
        }
        auto method = env->GetMethodID(cls, name, sig);
        if (!method) {
            cerr << "Unable to find method " << name << "(" << sig << ")" << endl;
        }
        NOTNULL(method);
        env.DoneWith(cls);
        return method;
    }

    void CheckException(JThread& env) const;
#else
    void CallVoid(JThread& env, const char* name, const char* sig, ...) {}
    jobject CallObject(JThread& env, const char* name, const char* sig, ...) {return nullptr;}
    void CallObjectFreeResult(JThread& env, const char* name, const char* sig, ...) {}
    float CallFloat(JThread& env, const char* name, const char* sig, ...) {return 0;}
    jboolean CallBoolean(JThread& env, const char* name, const char* sig, ...) {return 0;}
    void CheckException(JThread& env) const {}
#endif
};
