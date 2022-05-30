#pragma once
#include "JniCaller.h"

class FocusListener {
public:
    FocusListener(shared_ptr<JniCaller>& jniCaller);
    ~FocusListener();
};
