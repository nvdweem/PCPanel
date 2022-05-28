// SndCtrlTest.cpp : This file contains the 'main' function. Program execution begins and ends there.
//

#include <iostream>
#include "..\SndCtrl\pch.h"
#include "..\SndCtrl\sndctrl.h"

int main()
{
    std::cout << "Hello World!\n";
    auto sndctrl = make_unique<SndCtrl>(nullptr);
    Sleep(1000);
    std::cout << "Change default device\n";
    sndctrl->UpdateDefaultDevice(_T("{0.0.0.00000000}.{75599dff-bd64-486e-b5d6-62c94d3ae881}"), eRender, eMultimedia);
    Sleep(1000);
    std::cout << "Change default device\n";
    sndctrl->UpdateDefaultDevice(_T("{0.0.0.00000000}.{106b0363-a14d-46a3-b349-7f7cba73c2ef}"), eRender, eMultimedia);

    Sleep(10000);
    std::cout << "Bye World!\n";

}

