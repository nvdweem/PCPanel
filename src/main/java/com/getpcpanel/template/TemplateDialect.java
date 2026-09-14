package com.getpcpanel.template;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Nullable;

import io.quarkus.qute.Engine;
import io.quarkus.qute.Expression;
import io.quarkus.qute.TemplateException;

/**
 * Translates the user-facing {@code {{ … }}} template syntax into a Qute template.
 *
 * <p>Qute's own delimiters are single braces, which would give every {@code {} in a JSON body, a YAML flow map
 * or Home Assistant Jinja a meaning. So nothing but our own tags reaches Qute's lexer: every literal segment
 * becomes a {@code {_lit.get(n)}} placeholder whose text is supplied as data at render time, and a
 * {@code {{ … }}} tag becomes a Qute tag only when it is ours — it parses, it starts with a known variable,
 * namespace or literal, and every function in it is one we register. Anything else (Jinja, a typo, a
 * built-in Qute namespace such as {@code config:}) stays literal text, which is what keeps templates written
 * before this syntax existed rendering exactly as they did.
 */
final class TemplateDialect {
    /** Data key holding the literal segments. Not a valid user root: roots are checked against an allow-list. */
    static final String LITERALS = "_lit";

    private static final Pattern TAG = Pattern.compile("\\{\\{(.*?)}}", Pattern.DOTALL);
    private static final Pattern SECTION = Pattern.compile("^([#/])\\s*([A-Za-z]+)\\b(.*)$", Pattern.DOTALL);
    private static final Pattern FOR_ALIAS = Pattern.compile("^\\s*([A-Za-z_][A-Za-z0-9_]*)\\s+in\\s+(.+)$", Pattern.DOTALL);
    private static final Pattern LET_NAME = Pattern.compile("([A-Za-z_][A-Za-z0-9_]*)\\s*=");
    private static final Set<String> SECTIONS = Set.of("if", "else", "for", "each", "let");
    /** Qute's operators and the collection helpers we allow; everything else must be one of our functions. */
    private static final Set<String> BUILT_IN_METHODS = Set.of("?", ":", "?:", "or", "ifTruthy", "&&", "||", "and", "==", "!=", "eq", "ne", "is",
            ">", ">=", "<", "<=", "gt", "ge", "lt", "le", "+", "-", "plus", "minus", "mod", "get", "containsKey");

    private final Engine engine;
    private final Set<String> roots;
    private final Set<String> namespaces;
    private final Set<String> functions;

    TemplateDialect(Engine engine, Set<String> roots, Set<String> namespaces, Set<String> functions) {
        this.engine = engine;
        this.roots = roots;
        this.namespaces = namespaces;
        this.functions = functions;
    }

    /** The Qute source plus the literal text its placeholders refer to, and the tags that were left literal. */
    record Translation(String qute, List<String> literals, List<String> literalTags, @Nullable String error) {
    }

    Translation translate(String source) {
        var withSections = translate(source, true);
        var error = parseError(withSections);
        if (error == null) {
            return withSections;
        }
        // A section structure that does not close (e.g. an {{#if}} without {{/if}}) invalidates the whole
        // template; fall back to rendering the expressions and keeping every section tag as text.
        var withoutSections = translate(source, false);
        return new Translation(withoutSections.qute(), withoutSections.literals(), withoutSections.literalTags(), error);
    }

    private Translation translate(String source, boolean sections) {
        var qute = new StringBuilder();
        var literals = new ArrayList<String>();
        var literalTags = new ArrayList<String>();
        var aliases = new HashSet<String>();
        var pending = new StringBuilder();
        var m = TAG.matcher(source);
        var last = 0;
        while (m.find()) {
            pending.append(source, last, m.start());
            last = m.end();
            if (ESCAPE_INNER.equals(m.group(1).strip())) {
                pending.append('{');
                continue;
            }
            var qTag = toQute(m, sections, aliases);
            if (qTag == null) {
                pending.append(m.group());
                if (!m.group(1).isBlank()) {
                    literalTags.add(m.group());
                }
                continue;
            }
            flush(pending, qute, literals);
            qute.append(qTag);
        }
        pending.append(source, last, source.length());
        flush(pending, qute, literals);
        return new Translation(qute.toString(), literals, literalTags, null);
    }

    /**
     * Rewrites every tag that would render — except those {@code keep} accepts — into its literal form
     * {@code {{ '{' }}{ … }}}, which renders as the original text. Used to carry templates saved before this
     * syntax existed over unchanged: there a {@code {{ … }}} was literal text unless it was {@code {{ value }}}.
     * Idempotent: the literal form itself is kept.
     */
    String escapeTags(String source, java.util.function.Predicate<String> keep) {
        var out = new StringBuilder();
        var aliases = new HashSet<String>();
        var m = TAG.matcher(source);
        var last = 0;
        while (m.find()) {
            out.append(source, last, m.start());
            last = m.end();
            var inner = m.group(1).strip();
            if (toQute(m, true, aliases) != null && !ESCAPE_INNER.equals(inner) && !keep.test(inner)) {
                out.append(ESCAPE).append(m.group(), 1, m.group().length());
            } else {
                out.append(m.group());
            }
        }
        return out.append(source, last, source.length()).toString();
    }

    /** A tag rendering a single {@code {}; followed by the rest of an escaped tag it spells the original out. */
    static final String ESCAPE = "{{ '{' }}";
    private static final String ESCAPE_INNER = "'{'";

    private static void flush(StringBuilder pending, StringBuilder qute, List<String> literals) {
        if (pending.isEmpty()) {
            return;
        }
        literals.add(pending.toString());
        qute.append('{').append(LITERALS).append(".get(").append(literals.size() - 1).append(")}");
        pending.setLength(0);
    }

    /** The Qute form of one {@code {{ … }}} tag, or null when the tag is not ours and stays literal. */
    @Nullable
    private String toQute(Matcher m, boolean sections, Set<String> aliases) {
        var inner = m.group(1).strip();
        if (inner.isEmpty()) {
            return null;
        }
        var section = SECTION.matcher(inner);
        if (section.matches()) {
            return sections ? sectionTag(section.group(1), section.group(2), section.group(3).strip(), aliases) : null;
        }
        var qute = "{" + inner + "}";
        return isOurs(qute, aliases) ? qute : null;
    }

    @Nullable
    private String sectionTag(String kind, String name, String params, Set<String> aliases) {
        if (!SECTIONS.contains(name)) {
            return null;
        }
        if ("/".equals(kind)) {
            return params.isEmpty() ? "{/" + name + "}" : null;
        }
        var open = "{#" + name + (params.isEmpty() ? "" : " " + params) + "}";
        switch (name) {
            case "else" -> {
                // "{{#else}}" or "{{#else if cond}}"; checked inside a throw-away if so it parses on its own.
                return params.isEmpty() || isOurs("{#if true}" + open + "{/if}", aliases) ? open : null;
            }
            case "for" -> {
                var alias = FOR_ALIAS.matcher(params);
                if (!alias.matches() || !isOurs("{" + alias.group(2).strip() + "}", aliases)) {
                    return null;
                }
                aliases.add(alias.group(1));
                aliases.add(alias.group(1) + "_index");
                return open;
            }
            case "each" -> {
                if (!isOurs("{" + params + "}", aliases)) {
                    return null;
                }
                aliases.add("it");
                return open;
            }
            case "let" -> {
                var declared = new ArrayList<String>();
                var names = LET_NAME.matcher(params);
                while (names.find()) {
                    declared.add(names.group(1));
                }
                if (declared.isEmpty() || !isOurs(open + "{/let}", aliases)) {
                    return null;
                }
                aliases.addAll(declared);
                return open;
            }
            default -> {
                return isOurs(open + "{/" + name + "}", aliases) ? open : null;
            }
        }
    }

    private boolean isOurs(String qute, Set<String> aliases) {
        try {
            var expressions = engine.parse(qute).getExpressions();
            return !expressions.isEmpty() && expressions.stream().allMatch(e -> isOurs(e, aliases));
        } catch (TemplateException | IllegalArgumentException e) {
            return false;
        }
    }

    private boolean isOurs(Expression expression, Set<String> aliases) {
        if (expression.isLiteral()) {
            return true;
        }
        if (expression.hasNamespace()) {
            if (!namespaces.contains(expression.getNamespace())) {
                return false;
            }
        } else {
            var parts = expression.getParts();
            if (parts.isEmpty()) {
                return false;
            }
            var root = parts.getFirst();
            if (!root.isVirtualMethod() && !isLiteral(root.getName()) && !roots.contains(root.getName()) && !aliases.contains(root.getName())) {
                return false;
            }
        }
        for (var part : expression.getParts()) {
            if (part.isVirtualMethod()) {
                if (!BUILT_IN_METHODS.contains(part.getName()) && !functions.contains(part.getName())) {
                    return false;
                }
                for (var param : part.asVirtualMethod().getParameters()) {
                    if (!isOurs(param, aliases)) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private static boolean isLiteral(String partName) {
        return !partName.isEmpty() && (partName.charAt(0) == '\'' || partName.charAt(0) == '"' || Character.isDigit(partName.charAt(0))
                || "true".equals(partName) || "false".equals(partName) || "null".equals(partName));
    }

    @Nullable
    private String parseError(Translation translation) {
        try {
            engine.parse(translation.qute());
            return null;
        } catch (TemplateException | IllegalArgumentException e) {
            return e.getMessage();
        }
    }
}
