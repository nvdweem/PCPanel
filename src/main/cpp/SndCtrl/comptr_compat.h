#pragma once
// ---------------------------------------------------------------------------
// Minimal drop-in replacements for ATL's CComPtr / CComQIPtr.
//
// ATL ships only with Visual Studio, which is the single reason this DLL used
// to require a full Visual Studio installation. Only the subset of the CComPtr
// API actually used by this project is implemented here, but the reference-
// counting semantics match ATL exactly:
//   - constructing from a raw T*           -> AddRef
//   - copy construction / copy assignment  -> AddRef the new, Release the old
//   - destruction / overwrite              -> Release
//   - operator&()                          -> returns T** for COM out-params
//                                             (callers always use it on an
//                                              empty pointer, like ATL expects)
//   - implicit operator T*()               -> no AddRef (raw view)
//
// This header is portable C++ and works with both MSVC and MinGW-w64 / GCC, so
// the audio-control DLL can now be cross-compiled on Linux. See cpp/README.md.
// ---------------------------------------------------------------------------

#include <unknwn.h>

template <class T>
class CComPtr {
public:
    T* p;

    CComPtr() noexcept : p(nullptr) {}
    CComPtr(T* lp) noexcept : p(lp) {
        if (p) p->AddRef();
    }
    CComPtr(const CComPtr<T>& lp) noexcept : p(lp.p) {
        if (p) p->AddRef();
    }
    CComPtr(CComPtr<T>&& lp) noexcept : p(lp.p) {
        lp.p = nullptr;
    }
    ~CComPtr() noexcept {
        if (p) p->Release();
    }

    void Release() noexcept {
        T* tmp = p;
        if (tmp) {
            p = nullptr;
            tmp->Release();
        }
    }

    operator T*() const noexcept { return p; }
    T& operator*() const { return *p; }
    T* operator->() const noexcept { return p; }
    // Like ATL: hands out the address of the raw pointer for COM out-params.
    // Every call site uses this on a freshly default-constructed (null) pointer.
    T** operator&() noexcept { return &p; }
    bool operator!() const noexcept { return p == nullptr; }

    T* operator=(T* lp) noexcept {
        if (lp != p) {
            T* old = p;
            p = lp;
            if (p) p->AddRef();
            if (old) old->Release();
        }
        return p;
    }
    T* operator=(const CComPtr<T>& lp) noexcept {
        return operator=(lp.p);
    }
    T* operator=(CComPtr<T>&& lp) noexcept {
        if (this != &lp) {
            if (p) p->Release();
            p = lp.p;
            lp.p = nullptr;
        }
        return p;
    }
};

// CComQIPtr<T>: like CComPtr<T>, but constructing from any IUnknown* performs a
// QueryInterface for T's IID (matching ATL). Used to obtain a derived audio
// interface from an existing session pointer.
template <class T>
class CComQIPtr : public CComPtr<T> {
public:
    CComQIPtr() noexcept {}
    CComQIPtr(T* lp) noexcept : CComPtr<T>(lp) {}
    CComQIPtr(const CComQIPtr<T>& lp) noexcept : CComPtr<T>(lp.p) {}
    CComQIPtr(IUnknown* lp) noexcept {
        if (lp) {
            lp->QueryInterface(__uuidof(T), reinterpret_cast<void**>(&this->p));
        }
    }

    T* operator=(T* lp) noexcept { return CComPtr<T>::operator=(lp); }
    T* operator=(const CComQIPtr<T>& lp) noexcept { return CComPtr<T>::operator=(lp.p); }
};
