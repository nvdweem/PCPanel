package com.getpcpanel.util.tray;

public interface ITrayService {
    void init();

    class NoOp implements ITrayService {
        @Override
        public void init() {
        }
    }
}
