package com.getpcpanel.util;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ProcessHelper {
    public ProcessBuilder builder(String... command) {
        var result = new ProcessBuilder(command);
        result.environment().put("LC_ALL", "C");
        return result;
    }
}
