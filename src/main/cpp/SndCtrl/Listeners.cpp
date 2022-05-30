#include "pch.h"
#include "Listeners.h"

EDataFlow getDataFlow(IMMDevice& device) {
    CComPtr<IMMEndpoint> cpEndPoint = NULL;
    device.QueryInterface(__uuidof(IMMEndpoint), (void**)&cpEndPoint);

    EDataFlow dataflow = eRender;
    cpEndPoint->GetDataFlow(&dataflow);
    return dataflow;
}
