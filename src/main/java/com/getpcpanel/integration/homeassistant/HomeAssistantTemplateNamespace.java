package com.getpcpanel.integration.homeassistant;

import java.util.LinkedHashMap;

import com.getpcpanel.integration.homeassistant.dto.HomeAssistantServer;
import com.getpcpanel.profile.SaveService;
import com.getpcpanel.template.LazyMap;
import com.getpcpanel.template.TemplateDoc;
import com.getpcpanel.template.TemplateNamespace;
import com.getpcpanel.template.TemplateScope;

import io.quarkus.qute.TemplateData;
import io.quarkus.runtime.annotations.RegisterForReflection;
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
    @RegisterForReflection
    @TemplateDoc("Home Assistant servers")
    public static final class Root {
        private final SaveService saveService;

        Root(SaveService saveService) {
            this.saveService = saveService;
        }

        @TemplateDoc("Configured servers, by id")
        public LazyMap<ServerView> getServer() {
            var servers = new LinkedHashMap<String, HomeAssistantServer>();
            saveService.get().getHomeAssistantServers().forEach(s -> servers.put(s.id(), s));
            return LazyMap.of(servers, id -> new ServerView(servers.get(id).name()));
        }
    }

    @TemplateData
    @RegisterForReflection
    public record ServerView(@TemplateDoc("Name") String name) {
        @Override
        public String toString() {
            return name;
        }
    }
}
