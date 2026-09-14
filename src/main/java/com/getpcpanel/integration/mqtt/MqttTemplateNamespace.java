package com.getpcpanel.integration.mqtt;

import com.getpcpanel.template.TemplateNamespace;
import com.getpcpanel.template.TemplateScope;

import io.quarkus.qute.TemplateData;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/** {@code {{ mqtt.connected }}}. */
@ApplicationScoped
public class MqttTemplateNamespace implements TemplateNamespace {
    @Inject
    MqttService mqtt;

    @Override
    public String name() {
        return "mqtt";
    }

    @Override
    public Class<?> rootType() {
        return Root.class;
    }

    @Override
    public Object root(TemplateScope scope) {
        return new Root(mqtt);
    }

    @TemplateData
    public static final class Root {
        private final MqttService mqtt;

        Root(MqttService mqtt) {
            this.mqtt = mqtt;
        }

        public boolean isConnected() {
            return mqtt.isConnected();
        }
    }
}
