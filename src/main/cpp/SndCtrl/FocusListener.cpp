#include "pch.h"
#include "FocusListener.h"
#include "helpers.h"
#include <thread>
#include "JniCaller.h"

namespace {
// SetWinEventHook passes no user-data pointer to its callback, so file scope is the only channel a
// free callback has to reach the JVM. The hook thread owns both: it publishes them before installing
// the hook and clears them only after its message loop has ended, so the callback never observes a
// half-built or already-released value.
unique_ptr<JThread> pJThread;
shared_ptr<JniCaller> pJni;
}

VOID CALLBACK WinEventProcCallback(HWINEVENTHOOK hWinEventHook, DWORD dwEvent, HWND hwnd, LONG idObject, LONG idChild, DWORD dwEventThread, DWORD dwmsEventTime) {
    if (!pJThread || !pJni) {
        return;
    }
    auto name = GetProcessName(GetFocusProcessId());
    auto nameStr = pJThread->jstr(name.c_str());
    pJni->CallVoid(*pJThread, "focusChanged", "(Ljava/lang/String;)V", nameStr);
    pJThread->jstr(nameStr);
}

FocusListener::FocusListener(shared_ptr<JniCaller>& pJniCaller) {
    pJni = pJniCaller;

    std::promise<DWORD> threadIdPromise;
    threadId = threadIdPromise.get_future();

    thread = std::thread([p = std::move(threadIdPromise)]() mutable {
        pJThread = make_unique<JThread>();

        auto hook = SetWinEventHook(EVENT_SYSTEM_FOREGROUND, EVENT_SYSTEM_FOREGROUND, NULL, WinEventProcCallback, 0, 0, WINEVENT_OUTOFCONTEXT | WINEVENT_SKIPOWNPROCESS);
        if (!hook) {
            cerr << "Unable to install the foreground-window hook, focus tracking is inactive" << endl;
        }

        // A thread only gets a message queue once it calls a message function, and PostThreadMessage
        // is dropped without one. Forcing the queue into existence before publishing the thread id is
        // what makes the destructor's WM_QUIT land.
        MSG msg;
        PeekMessage(&msg, nullptr, WM_USER, WM_USER, PM_NOREMOVE);
        p.set_value(GetCurrentThreadId());

        while (GetMessage(&msg, nullptr, 0, 0) > 0) {
            TranslateMessage(&msg);
            DispatchMessage(&msg);
        }

        if (hook) {
            UnhookWinEvent(hook);
        }
        pJThread.reset();
    });
}

FocusListener::~FocusListener() {
    // Stop the hook thread and wait for it before dropping what its callback reads. Waiting is what
    // makes this safe: WM_QUIT only ends the loop between dispatches, so the join returns once any
    // in-flight callback has finished.
    if (threadId.valid()) {
        PostThreadMessage(threadId.get(), WM_QUIT, 0, 0);
    }
    if (thread.joinable()) {
        thread.join();
    }
    pJni.reset();
}
