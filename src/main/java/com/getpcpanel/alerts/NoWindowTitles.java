package com.getpcpanel.alerts;

import java.util.List;
import java.util.Map;

import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;

@DefaultBean
@ApplicationScoped
class NoWindowTitles implements WindowTitles {
    @Override
    public Map<String, List<String>> titles() {
        return Map.of();
    }
}
