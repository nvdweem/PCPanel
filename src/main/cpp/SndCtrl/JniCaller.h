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
        auto getEnvStat = pJvm->GetEnv((void**)&pEnv, JNI_VERSION_10);
        if (getEnvStat == JNI_EDETACHED) {
            if (pJvm->AttachCurrentThreadAsDaemon((void**)&pEnv, &attachArgs) == 0) {
                needsDetach = true;
            }
            else {
                error = true;
            }
        }
    }
    ~JThread() {
        if (needsDetach) {
            pJvm->DetachCurrentThread();
        }
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

    JniCaller(JThread& env, jobject obj) : obj(env->NewGlobalRef(obj)) {}
    ~JniCaller() {
        JThread env;
        if (*env) {
            cout << "Cleaning" << endl;
            env->DeleteGlobalRef(this->obj);
            cout << "/Cleaning" << endl;
        }
        else {
            cout << "Env has error, not cleaning up";
        }
    }

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
};
