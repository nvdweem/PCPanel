#include "pch.h"
#include "sndctrl.h"

using namespace std;

CComPtr<IMMDeviceEnumerator> pEnumerator;
CComPtr<DeviceListener> pDeviceListener;


void init() {
    if (CoInitialize(nullptr) != S_OK) {
        cerr << "Unable to CoInitialize" << endl;
    }

    const CLSID CLSID_MMDeviceEnumerator = __uuidof(MMDeviceEnumerator);
    const IID IID_IMMDeviceEnumerator = __uuidof(IMMDeviceEnumerator);

    IMMDeviceEnumerator* enumerator = NULL;
    if (FAILED(CoCreateInstance(CLSID_MMDeviceEnumerator, NULL, CLSCTX_ALL, IID_IMMDeviceEnumerator, (void**)&enumerator))) {
        cerr << "Unable to create device enumerator, more will fail later :(" << endl;
    }
    pEnumerator = enumerator;
    pDeviceListener = new DeviceListener(pEnumerator);

    cout << "Device enumerator created" << endl;
}

void destroy() {

}
