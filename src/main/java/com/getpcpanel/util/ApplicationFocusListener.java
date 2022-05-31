package com.getpcpanel.util;

import java.util.ArrayList;
import java.util.List;

public enum ApplicationFocusListener {
    instance;
    private final List<FocusListener> listeners = new ArrayList<>();
    private String prev = "";

    public static void setFocusApplication(String application) {
        instance.listeners.forEach(l -> l.focusChanged(instance.prev, application));
        instance.prev = application;
    }

    public static FocusListenerRemover addFocusListener(FocusListener listener) {
        instance.listeners.add(listener);
        return () -> removeFocusListener(listener);
    }

    public static void removeFocusListener(FocusListener listener) {
        instance.listeners.remove(listener);
    }

    public interface FocusListener {
        void focusChanged(String from, String to);
    }

    public interface FocusListenerRemover extends Runnable {
    }
}
