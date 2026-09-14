package com.getpcpanel.template;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import io.quarkus.qute.Engine;
import io.quarkus.qute.ReflectionValueResolver;

/**
 * Renders templates through the real dialect and functions for tests outside this package. Views are resolved
 * reflectively here (JVM); {@code TemplateDataCoverageTest} guards that the native image can resolve them too.
 */
public final class TestTemplates {
    private TestTemplates() {
    }

    public static String render(String source, Map<String, Object> roots) {
        var engine = Engine.builder().addDefaults().addValueResolver(new ReflectionValueResolver()).strictRendering(false);
        TemplateFunctions.resolvers().forEach(engine::addValueResolver);
        TemplateFunctions.namespaceResolvers().forEach(engine::addNamespaceResolver);
        engine.addResultMapper(new io.quarkus.qute.ResultMapper() {
            @Override
            public boolean appliesTo(io.quarkus.qute.TemplateNode.Origin origin, Object result) {
                return io.quarkus.qute.Results.isNotFound(result);
            }

            @Override
            public String map(Object result, io.quarkus.qute.Expression expression) {
                return "";
            }
        });
        var names = new HashMap<String, Object>(roots);
        return new TemplateRenderer(engine.build(), Set.copyOf(names.keySet())).render(source, names::get).output();
    }
}
