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
        return pEnv;
    }

    JNIEnv* raw() {
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
        JThread env;
        return JniCaller(env, obj);
    }

    // For owners that outlive the object holding them: a listener keeps its caller alive for as long
    // as a notification can still be in flight, which is not bounded by the lifetime of whoever
    // registered it.
    static shared_ptr<JniCaller> CreateShared(jobject obj) {
        JThread env;
        return shared_ptr<JniCaller>(new JniCaller(env, obj));
    }

private:
    JniCaller(JThread& env, jobject obj): obj(nullptr) {
#ifndef NO_JNI
        if (*env && obj) {
            this->obj = env->NewGlobalRef(obj);
        }
#endif
    }
public:
    JniCaller(JNIEnv* env, jobject obj) {
#ifndef NO_JNI
        this->obj = env->NewGlobalRef(obj);
#endif
    }
    ~JniCaller() {
#ifndef NO_JNI
        JThread env;
        if (*env && this->obj) {
            env->DeleteGlobalRef(this->obj);
        }
#endif
    }

    // The global ref is owned, so a copy would release it twice. Construction always goes through
    // Create/CreateShared, whose returned prvalue is elided rather than copied.
    JniCaller(const JniCaller&) = delete;
    JniCaller& operator=(const JniCaller&) = delete;

#ifndef NO_JNI
    void CallVoid(JThread& env, const char* name, const char* sig, ...) {
        if (*env && obj) {
            auto method = GetMethod(env, name, sig);
            NULLRETURN(method);
            va_list args;
            va_start(args, sig);
            env->CallVoidMethodV(obj, method, args);
            va_end(args);
            CheckException(env);
        }
    }

    jobject CallObjectMethodV(JThread& env, const char* name, const char* sig, va_list args) {
        if (*env && obj) {
            auto method = GetMethod(env, name, sig);
            NULLRETURNVAL(method, nullptr);
            auto result = env->CallObjectMethodV(obj, method, args);
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
        if (*env && obj) {
            auto method = GetMethod(env, name, sig);
            NULLRETURNVAL(method, 0);
            va_list args;
            va_start(args, sig);
            auto result = env->CallFloatMethodV(obj, method, args);
            va_end(args);
            CheckException(env);
            return result;
        }
        return 0;
    }
    jboolean CallBoolean(JThread& env, const char* name, const char* sig, ...) {
        if (*env && obj) {
            auto method = GetMethod(env, name, sig);
            NULLRETURNVAL(method, false);
            va_list args;
            va_start(args, sig);
            auto result = env->CallBooleanMethodV(obj, method, args);
            va_end(args);
            CheckException(env);
            return result;
        }
        return false;
    }

private:
    // Returns nullptr when the callback method is not there, e.g. after a rename on the Java side.
    // Callers must skip the call: handing a null jmethodID to a Call*Method is a JVM crash, not an
    // exception.
    jmethodID GetMethod(JThread& env, const char* name, const char* sig) {
        auto cls = env->GetObjectClass(obj);
        if (!cls) {
            cerr << "Unable to find class for method " << name << "(" << sig << ")" << endl;
            return nullptr;
        }
        auto method = env->GetMethodID(cls, name, sig);
        if (!method) {
            cerr << "Unable to find method " << name << "(" << sig << ")" << endl;
            // GetMethodID leaves a pending NoSuchMethodError that would otherwise surface at whatever
            // JNI call happens to come next.
            CheckException(env);
        }
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
