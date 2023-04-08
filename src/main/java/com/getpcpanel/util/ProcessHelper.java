package com.getpcpanel.util;

import org.springframework.stereotype.Service;

@Service
public class ProcessHelper {
    public ProcessBuilder builder(String... command) {
        var result = new ProcessBuilder(command);
        result.environment().put("LC_ALL", "C");
        return result;
    }
}
