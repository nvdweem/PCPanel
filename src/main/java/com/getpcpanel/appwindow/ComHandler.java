package com.getpcpanel.appwindow;

import com.sun.jna.Callback;
import com.sun.jna.CallbackReference;
import com.sun.jna.Function;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.Guid.GUID;

/**
 * A COM object implemented in Java: IUnknown plus the single {@code Invoke} method every WebView2 completion and
 * event handler has. WebView2 hands its results to such handlers, so the window needs a few of them. They live as
 * long as the window, so reference counting is a formality: AddRef and Release always answer 1 and nothing is
 * freed.
 */
final class ComHandler {
    private static final GUID IID_IUNKNOWN = new GUID("00000000-0000-0000-C000-000000000046");
    private static final int S_OK = 0;
    private static final int E_NOINTERFACE = 0x80004002;
    private static final int E_POINTER = 0x80004003;

    public interface QueryInterfaceProc extends Callback {
        int invoke(Pointer self, Pointer riid, Pointer ppv);
    }

    public interface RefCountProc extends Callback {
        int invoke(Pointer self);
    }

    /** {@code Invoke(HRESULT errorCode, IUnknown* result)} of a completion handler. */
    public interface CompletedProc extends Callback {
        int invoke(Pointer self, int errorCode, Pointer result);
    }

    /** {@code Invoke(ICoreWebView2* sender, IUnknown* args)} of an event handler. */
    public interface EventProc extends Callback {
        int invoke(Pointer self, Pointer sender, Pointer args);
    }

    private final GUID iid;
    // Held so the callbacks, and the native stubs JNA made for them, stay alive while WebView2 may call them.
    private final QueryInterfaceProc queryInterface = this::queryInterface;
    private final RefCountProc refCount = self -> 1;
    private final Callback invoke;
    private final Memory vtable = new Memory(4L * Native.POINTER_SIZE);
    private final Memory object = new Memory(Native.POINTER_SIZE);

    private ComHandler(String iid, Callback invoke) {
        this.iid = new GUID(iid);
        this.invoke = invoke;
        vtable.setPointer(0, CallbackReference.getFunctionPointer(queryInterface));
        vtable.setPointer(Native.POINTER_SIZE, CallbackReference.getFunctionPointer(refCount));
        vtable.setPointer(2L * Native.POINTER_SIZE, CallbackReference.getFunctionPointer(refCount));
        vtable.setPointer(3L * Native.POINTER_SIZE, CallbackReference.getFunctionPointer(this.invoke));
        object.setPointer(0, vtable);
    }

    static ComHandler completed(String iid, CompletedProc invoke) {
        return new ComHandler(iid, invoke);
    }

    static ComHandler event(String iid, EventProc invoke) {
        return new ComHandler(iid, invoke);
    }

    /** The interface pointer to pass to WebView2. */
    Pointer pointer() {
        return object;
    }

    private int queryInterface(Pointer self, Pointer riid, Pointer ppv) {
        if (ppv == null) {
            return E_POINTER;
        }
        var requested = new GUID(riid);
        if (requested.equals(iid) || requested.equals(IID_IUNKNOWN)) {
            ppv.setPointer(0, self);
            return S_OK;
        }
        ppv.setPointer(0, Pointer.NULL);
        return E_NOINTERFACE;
    }

    /** Calls method {@code index} of the COM interface {@code self} (IUnknown's three come first); returns its HRESULT. */
    static int call(Pointer self, int index, Object... args) {
        var full = new Object[args.length + 1];
        full[0] = self;
        System.arraycopy(args, 0, full, 1, args.length);
        var method = self.getPointer(0).getPointer((long) index * Native.POINTER_SIZE);
        return Function.getFunction(method).invokeInt(full);
    }

    static void addRef(Pointer self) {
        call(self, 1);
    }

    static void release(Pointer self) {
        call(self, 2);
    }
}
