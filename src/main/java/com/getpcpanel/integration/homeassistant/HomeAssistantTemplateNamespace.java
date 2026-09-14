package com.getpcpanel.integration.homeassistant;

import java.util.LinkedHashMap;

import com.getpcpanel.integration.homeassistant.dto.HomeAssistantServer;
import com.getpcpanel.profile.SaveService;
import com.getpcpanel.template.LazyMap;
import com.getpcpanel.template.TemplateNamespace;
import com.getpcpanel.template.TemplateScope;

import io.quarkus.qute.TemplateData;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/** {@code {{ ha.server.<id>.name }}}: the configured Home Assistant servers. */
@ApplicationScoped
public class HomeAssistantTemplateNamespace implements TemplateNamespace {
    @Inject
    SaveService saveService;

    @Override
    public String name() {
        return "ha";
    }

    @Override
    public Class<?> rootType() {
        return Root.class;
    }

    @Override
    public Object root(TemplateScope scope) {
        return new Root(saveService);
    }

    @TemplateData
    public static final class Root {
        private final SaveService saveService;

        Root(SaveService saveService) {
            this.saveService = saveService;
        }

        public LazyMap<ServerView> getServer() {
            var servers = new LinkedHashMap<String, HomeAssistantServer>();
            saveService.get().getHomeAssistantServers().forEach(s -> servers.put(s.id(), s));
            return LazyMap.of(servers, id -> new ServerView(servers.get(id).name()));
        }
    }

    @TemplateData
    public record ServerView(String name) {
        @Override
        public String toString() {
            return name;
        }
    }
}
