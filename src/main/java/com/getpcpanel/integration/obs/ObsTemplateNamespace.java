package com.getpcpanel.integration.obs;

import javax.annotation.Nullable;

import org.apache.commons.lang3.StringUtils;

import com.getpcpanel.integration.obs.command.CommandObsMuteSource;
import com.getpcpanel.integration.obs.command.CommandObsSetSourceVolume;
import com.getpcpanel.template.LazyMap;
import com.getpcpanel.template.TemplateDoc;
import com.getpcpanel.template.TemplateNamespace;
import com.getpcpanel.template.TemplateScope;

import io.quarkus.qute.TemplateData;
import io.quarkus.runtime.annotations.RegisterForReflection;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/** {@code {{ obs.… }}}: OBS connection and the mute state of its audio sources. */
@ApplicationScoped
public class ObsTemplateNamespace implements TemplateNamespace {
    @Inject
    OBS obs;

    @Override
    public String name() {
        return "obs";
    }

    @Override
    public Class<?> rootType() {
        return Root.class;
    }

    @Override
    public Object root(TemplateScope scope) {
        return new Root(obs, scope);
    }

    @TemplateData
    @RegisterForReflection
    @TemplateDoc("OBS: connection and source mute state")
    public static final class Root {
        private final OBS obs;
        private final TemplateScope scope;

        Root(OBS obs, TemplateScope scope) {
            this.obs = obs;
            this.scope = scope;
        }

        @TemplateDoc("Whether it is connected")
        public boolean isConnected() {
            return obs.isConnected();
        }

        @TemplateDoc("Audio sources, by name")
        public LazyMap<SourceView> getSource() {
            var sources = obs.getSourcesWithMuteState();
            return LazyMap.of(sources, name -> new SourceView(name, sources.get(name)));
        }

        /** The source this control's OBS volume or mute action acts on. */
        @TemplateDoc("What this control acts on")
        @Nullable
        public SourceView getTarget() {
            var commands = scope.commands();
            if (commands == null) {
                return null;
            }
            var name = commands.getCommand(CommandObsSetSourceVolume.class).map(CommandObsSetSourceVolume::getSourceName)
                               .or(() -> commands.getCommand(CommandObsMuteSource.class).map(CommandObsMuteSource::getSource))
                               .orElse(null);
            if (StringUtils.isBlank(name)) {
                return null;
            }
            for (var entry : obs.getSourcesWithMuteState().entrySet()) {
                if (StringUtils.equalsIgnoreCase(name, entry.getKey())) {
                    return new SourceView(entry.getKey(), entry.getValue());
                }
            }
            return new SourceView(name, null);
        }
    }

    @TemplateData
    @RegisterForReflection
    public record SourceView(@TemplateDoc("Name") String name, @TemplateDoc("Muted") @Nullable Boolean muted) {
        @Override
        public String toString() {
            return name;
        }
    }
}
