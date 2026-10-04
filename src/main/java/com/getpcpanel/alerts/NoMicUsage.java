package com.getpcpanel.alerts;

import java.util.Set;

import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;

@DefaultBean
@ApplicationScoped
class NoMicUsage implements MicUsage {
    @Override
    public Set<String> appsUsingMic() {
        return Set.of();
    }
}
