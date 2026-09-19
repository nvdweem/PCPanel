package com.getpcpanel.template;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import io.quarkus.arc.All;
import io.quarkus.arc.Unremovable;
import io.quarkus.qute.Engine;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.log4j.Log4j2;
import one.util.streamex.StreamEx;

/**
 * Renders the {@code {{ }}} templates users write in overlay names and output commands (HTTP, MQTT, OSC, Home
 * Assistant). The syntax lives in {@link TemplateDialect}; the variables are {@link CoreTemplateVariables} plus
 * every {@link TemplateNamespace}.
 */
@Log4j2
@Unremovable
@ApplicationScoped
public class TemplateService {
    @Inject
    Engine engine;
    @Inject
    CoreTemplateVariables core;
    @Inject
    @All
    List<TemplateNamespace> namespaces;

    private TemplateRenderer renderer;
    private Map<String, TemplateNamespace> namespacesByName;
    /** Template texts whose render failed, so a broken template is logged once rather than on every knob tick. */
    private final Set<String> reportedFailures = ConcurrentHashMap.newKeySet();

    @PostConstruct
    void init() {
        namespacesByName = StreamEx.of(namespaces).toMap(TemplateNamespace::name, n -> n);
        renderer = new TemplateRenderer(engine, rootNames());
    }

    public Set<String> rootNames() {
        var roots = new HashSet<>(CoreTemplateVariables.NAMES);
        StreamEx.of(namespaces).map(TemplateNamespace::name).forEach(roots::add);
        return Set.copyOf(roots);
    }

    public List<TemplateNamespace> namespaces() {
        return namespaces;
    }

    /** Whether {@code source} contains anything to render; plain text is used as-is. */
    public static boolean hasTags(@Nullable String source) {
        return TemplateRenderer.hasTags(source);
    }

    /**
     * Renders {@code source} for {@code scope}. Never throws: when rendering fails the {@code fallback} is used and
     * the failure is logged once per template text.
     */
    public String render(@Nullable String source, TemplateScope scope, Supplier<String> fallback) {
        if (source == null) {
            return fallback.get();
        }
        if (!hasTags(source)) {
            return source;
        }
        try {
            return renderer.render(source, name -> root(name, scope)).output();
        } catch (RuntimeException e) {
            if (reportedFailures.add(source)) {
                log.warn("Template could not be rendered, using the fallback: {}", source, e);
            }
            return fallback.get();
        }
    }

    /** A render for the editor preview: the output, the tags that stayed text, and any error. */
    public TemplateResult preview(String source, TemplateScope scope) {
        try {
            return renderer.render(source, name -> root(name, scope));
        } catch (RuntimeException e) {
            return new TemplateResult("", List.of(), String.valueOf(e.getMessage()));
        }
    }

    /**
     * Carries a template saved before this syntax existed over unchanged: every tag that would now render becomes
     * literal text, except the ones {@code keep} accepts (the {@code {{ value }}} of a field that already had it).
     */
    public String escapeTags(String source, Predicate<String> keep) {
        return renderer.escapeTags(source, keep);
    }

    @Nullable
    Object root(String name, TemplateScope scope) {
        if (CoreTemplateVariables.NAMES.contains(name)) {
            return core.get(name, scope);
        }
        var namespace = namespacesByName.get(name);
        if (namespace == null) {
            return null;
        }
        try {
            return namespace.root(scope);
        } catch (RuntimeException e) {
            log.debug("Template namespace {} failed", name, e);
            return null;
        }
    }
}
