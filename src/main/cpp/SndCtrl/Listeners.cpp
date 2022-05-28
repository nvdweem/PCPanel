#include "pch.h"
#include "Listeners.h"


EDataFlow getDataFlow(IMMDevice& device)
{
    CComPtr<IMMEndpoint> pEndPoint = NULL;
    device.QueryInterface(__uuidof(IMMEndpoint), (void**)&pEndPoint);
    EDataFlow dataflow = eRender;
    pEndPoint->GetDataFlow(&dataflow);
    return dataflow;
}
