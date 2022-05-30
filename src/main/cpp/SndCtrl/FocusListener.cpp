#include "pch.h"
#include "FocusListener.h"
#include "helpers.h"
#include <thread>
#include "JniCaller.h"

unique_ptr<JThread> pJThread;
unique_ptr<thread> pThread;
shared_ptr<JniCaller> pJni;

VOID CALLBACK WinEventProcCallback(HWINEVENTHOOK hWinEventHook, DWORD dwEvent, HWND hwnd, LONG idObject, LONG idChild, DWORD dwEventThread, DWORD dwmsEventTime)
{
    auto name = GetProcessName(GetFocusProcessId());

    pJni->CallVoid("focusChanged", "(Ljava/lang/String;)V", pJThread->jstr(name.c_str()));
}

FocusListener::FocusListener(shared_ptr<JniCaller>& pJniCaller) {
    pJni = pJniCaller;

    JThread thread;
    pJni->CallVoid("focusChanged", "(Ljava/lang/String;)V", thread.jstr("Nog een test?!"));

    pThread = make_unique<std::thread>([this]() {
        pJThread = make_unique<JThread>();
        SetWinEventHook(EVENT_SYSTEM_FOREGROUND, EVENT_SYSTEM_FOREGROUND, NULL, WinEventProcCallback, 0, 0, WINEVENT_OUTOFCONTEXT | WINEVENT_SKIPOWNPROCESS);
        MSG msg;
        while (GetMessage(&msg, NULL, 0, 0)) {
            TranslateMessage(&msg);
            DispatchMessage(&msg);
        }
    });
}

FocusListener::~FocusListener()
{
    pThread.release();
    pJni.reset();
    pJThread.release();
}
