package com.getpcpanel.template;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;

import javax.annotation.Nullable;

import io.quarkus.qute.Engine;
import io.quarkus.qute.Template;

/**
 * Parses and renders {@code {{ }}} templates against a set of root variables. Holds no application state, so tests
 * construct it with a bare engine; {@link TemplateService} is the application-facing wrapper.
 */
final class TemplateRenderer {
    private static final String TAG_START = "{{";
    private static final int CACHE_SIZE = 512;
    private static final String MARK_START = "";
    private static final String MARK_END = "";

    private final SecureRandom random = new SecureRandom();

    private final TemplateDialect dialect;
    private final Engine engine;
    private final Map<String, Parsed> cache = Collections.synchronizedMap(new LinkedHashMap<>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Parsed> eldest) {
            return size() > CACHE_SIZE;
        }
    });

    TemplateRenderer(Engine engine, Set<String> roots) {
        this.engine = engine;
        dialect = new TemplateDialect(engine, roots, Set.of(TemplateFunctions.MATH_NAMESPACE), TemplateFunctions.names());
    }

    record Parsed(Template template, List<String> literals, List<String> literalTags, @Nullable String error) {
    }

    /** See {@link TemplateDialect#escapeTags}. */
    String escapeTags(String source, java.util.function.Predicate<String> keep) {
        return hasTags(source) ? dialect.escapeTags(source, keep) : source;
    }

    /** Whether {@code source} can contain a tag at all; lets consumers skip templating entirely for plain text. */
    static boolean hasTags(@Nullable String source) {
        return source != null && source.contains(TAG_START);
    }

    TemplateResult render(String source, Function<String, Object> roots) {
        if (!hasTags(source)) {
            return new TemplateResult(source, List.of(), null);
        }
        var parsed = cache.computeIfAbsent(source, this::parse);
        var output = parsed.template().instance()
                           .data(new RootMap(parsed.literals(), roots))
                           .render();
        return new TemplateResult(output, parsed.literalTags(), parsed.error());
    }

    /**
     * Renders {@code source} and tells the user's own text apart from what its tags produced. Each literal renders
     * as a marker carrying a key drawn for this render alone, so tag output (which may come from outside the app)
     * cannot pass itself off as literal text.
     */
    List<TemplateSegment> renderSegments(String source, Function<String, Object> roots) {
        if (!hasTags(source)) {
            return List.of(TemplateSegment.literal(source));
        }
        var parsed = cache.computeIfAbsent(source, this::parse);
        var key = Long.toHexString(random.nextLong()) + Long.toHexString(random.nextLong());
        var markers = new ArrayList<String>(parsed.literals().size());
        for (var i = 0; i < parsed.literals().size(); i++) {
            markers.add(MARK_START + key + ':' + i + MARK_END);
        }
        var output = parsed.template().instance()
                           .data(new RootMap(markers, roots))
                           .render();

        var segments = new ArrayList<TemplateSegment>();
        var m = Pattern.compile(MARK_START + Pattern.quote(key) + ":(\\d+)" + MARK_END).matcher(output);
        var last = 0;
        while (m.find()) {
            add(segments, output.substring(last, m.start()), false);
            add(segments, parsed.literals().get(Integer.parseInt(m.group(1))), true);
            last = m.end();
        }
        add(segments, output.substring(last), false);
        return segments;
    }

    private static void add(List<TemplateSegment> segments, String text, boolean literal) {
        if (text.isEmpty()) {
            return;
        }
        if (!segments.isEmpty() && segments.getLast().literal() == literal) {
            segments.set(segments.size() - 1, new TemplateSegment(segments.getLast().text() + text, literal));
        } else {
            segments.add(new TemplateSegment(text, literal));
        }
    }

    private Parsed parse(String source) {
        var translation = dialect.translate(source);
        return new Parsed(engine.parse(translation.qute()), translation.literals(), translation.literalTags(), translation.error());
    }

    /**
     * The data one render sees: the literal segments plus the root variables. A root is resolved the first time
     * the template reaches it and remembered for the rest of that render.
     */
    private static final class RootMap extends java.util.AbstractMap<String, Object> {
        private static final Object ABSENT = new Object();
        private final List<String> literals;
        private final Function<String, Object> roots;
        private final Map<String, Object> resolved = new java.util.HashMap<>();

        RootMap(List<String> literals, Function<String, Object> roots) {
            this.literals = literals;
            this.roots = roots;
        }

        private Object lookup(Object key) {
            if (!(key instanceof String name)) {
                return ABSENT;
            }
            if (TemplateDialect.LITERALS.equals(name)) {
                return literals;
            }
            return resolved.computeIfAbsent(name, n -> {
                var value = roots.apply(n);
                return value == null ? ABSENT : TemplateFunctions.normalize(value);
            });
        }

        @Override
        public boolean containsKey(Object key) {
            return lookup(key) != ABSENT;
        }

        @Override
        @Nullable
        public Object get(Object key) {
            var value = lookup(key);
            return value == ABSENT ? null : value;
        }

        @Override
        public Set<Entry<String, Object>> entrySet() {
            return Set.of();
        }
    }
}
