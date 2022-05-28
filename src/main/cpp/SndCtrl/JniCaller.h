#pragma once


extern JavaVM* pJvm;


class JThread {
private:
    static JavaVMAttachArgs attachArgs;
    JNIEnv* pEnv;
    bool needsDetach;
    bool error;
public:
    JThread() : pEnv(nullptr), needsDetach(false), error(false) {
#ifndef NO_JNI
        auto getEnvStat = pJvm->GetEnv((void**)&pEnv, JNI_VERSION_10);
        if (getEnvStat == JNI_EDETACHED) {
            if (pJvm->AttachCurrentThreadAsDaemon((void**)&pEnv, &attachArgs) == 0) {
                needsDetach = true;
            }
            else {
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

    JNIEnv* operator->() {
        return pEnv;
    }

    bool operator*() {
        return !error;
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

    JniCaller(JThread& env, jobject obj) : obj(
#ifndef NO_JNI
        env->NewGlobalRef(obj)
#endif
    ) {}
    ~JniCaller() {
#ifndef NO_JNI
        JThread env;
        if (*env) {
            env->DeleteGlobalRef(this->obj);
        }
        else {
            cout << "Env has error, not cleaning up";
        }
#endif
    }

#ifndef NO_JNI
    void CallVoid(const char* name, const char* sig, ...) {
        JThread env;
        auto method = GetMethod(env, name, sig);

        va_list args;
        va_start(args, sig);
        env->CallVoidMethodV(obj, method, args);
        va_end(args);
    }

    jobject CallObject(const char* name, const char* sig, ...) {
        JThread env;
        auto method = GetMethod(env, name, sig);

        va_list args;
        va_start(args, sig);
        auto result = env->CallObjectMethodV(obj, method, args);
        va_end(args);
        return result;
    }

    float CallFloat(const char* name, const char* sig, ...) {
        JThread env;
        auto method = GetMethod(env, name, sig);

        va_list args;
        va_start(args, sig);
        auto result = env->CallFloatMethod(obj, method, args);
        va_end(args);
        return result;
    }

private:
    jmethodID GetMethod(JThread& env, const char* name, const char* sig) {
     auto cls = env->GetObjectClass(obj);
        if (!cls) {
            cerr << "Unable to find class for method " << name << "(" << sig << ")" << endl;
        }
        auto method = env->GetMethodID(cls, name, sig);
        if (!method) {
            cerr << "Unable to find method " << name << "(" << sig << ")" << endl;
        }
        return method;
    }
#else
    void CallVoid(const char* name, const char* sig, ...) {}
    jobject CallObject(const char* name, const char* sig, ...) {return nullptr;}
    float CallFloat(const char* name, const char* sig, ...) {return 0;}
#endif
};
