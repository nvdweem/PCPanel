#pragma once
#include "JniCaller.h"
#include <future>
#include <thread>

class FocusListener {
public:
    FocusListener(shared_ptr<JniCaller>& jniCaller);
    ~FocusListener();
    FocusListener(const FocusListener&) = delete;
    FocusListener& operator=(const FocusListener&) = delete;

private:
    std::thread thread;
    std::future<DWORD> threadId;
};
