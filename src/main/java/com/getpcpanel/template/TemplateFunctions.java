package com.getpcpanel.template;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import javax.annotation.Nullable;

import org.apache.commons.lang3.StringUtils;

import io.quarkus.qute.EngineConfiguration;
import io.quarkus.qute.EvalContext;
import io.quarkus.qute.NamespaceResolver;
import io.quarkus.qute.Results;
import io.quarkus.qute.ValueResolver;

/**
 * The functions a template may call. Each is a plain resolver class: {@code @EngineConfiguration} registers it
 * on the application's Qute engine at build time (no reflection in the native image), and tests add the same
 * instances to a bare engine through {@link #resolvers()}.
 */
public final class TemplateFunctions {
    static final Set<String> NUMBER = Set.of("round", "floor", "ceil", "abs", "clamp", "scale", "format", "*", "/");
    static final Set<String> STRING = Set.of("upper", "lower", "trim", "pad", "padStart", "truncate", "replace", "urlencode", "json");
    static final Set<String> MATH = Set.of("min", "max");
    static final String MATH_NAMESPACE = "math";

    private TemplateFunctions() {
    }

    static Set<String> names() {
        var all = new java.util.HashSet<String>();
        all.addAll(NUMBER);
        all.addAll(STRING);
        all.addAll(MATH);
        return Set.copyOf(all);
    }

    static List<ValueResolver> resolvers() {
        return List.of(new NumberFunctions(), new StringFunctions());
    }

    static List<NamespaceResolver> namespaceResolvers() {
        return List.of(new MathFunctions());
    }

    /** Whole numbers as {@code Long} so they print as {@code 50}, not {@code 50.0}; everything else unchanged. */
    public static Object normalize(@Nullable Object value) {
        if (value instanceof Double || value instanceof Float) {
            var d = ((Number) value).doubleValue();
            if (Double.isFinite(d) && d == Math.rint(d) && Math.abs(d) < 1e15) {
                return (long) d;
            }
        }
        return value;
    }

    private static CompletionStage<Object> params(EvalContext context, java.util.function.Function<List<Object>, Object> fn) {
        var futures = new ArrayList<CompletableFuture<Object>>();
        for (var param : context.getParams()) {
            futures.add(context.evaluate(param).toCompletableFuture());
        }
        return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).thenApply(v -> {
            var values = futures.stream().map(CompletableFuture::join).toList();
            if (values.stream().anyMatch(Results::isNotFound)) {
                return Results.NotFound.from(context);
            }
            return fn.apply(values);
        });
    }

    private static double num(Object o) {
        if (o instanceof Number n) {
            return n.doubleValue();
        }
        return Double.parseDouble(String.valueOf(o).trim());
    }

    @EngineConfiguration
    public static class NumberFunctions implements ValueResolver {
        @Override
        public boolean appliesTo(EvalContext context) {
            return context.getBase() instanceof Number && NUMBER.contains(context.getName());
        }

        @Override
        public CompletionStage<Object> resolve(EvalContext context) {
            var base = ((Number) context.getBase()).doubleValue();
            return params(context, p -> normalize(switch (context.getName()) {
                case "round" -> BigDecimal.valueOf(base).setScale(p.isEmpty() ? 0 : (int) num(p.getFirst()), RoundingMode.HALF_UP).doubleValue();
                case "floor" -> Math.floor(base);
                case "ceil" -> Math.ceil(base);
                case "abs" -> Math.abs(base);
                case "clamp" -> Math.max(num(p.get(0)), Math.min(num(p.get(1)), base));
                case "scale" -> {
                    var fromMin = num(p.get(0));
                    var fromMax = num(p.get(1));
                    var toMin = num(p.get(2));
                    var toMax = num(p.get(3));
                    yield fromMax == fromMin ? toMin : toMin + (base - fromMin) * (toMax - toMin) / (fromMax - fromMin);
                }
                case "format" -> new DecimalFormat(String.valueOf(p.getFirst()), DecimalFormatSymbols.getInstance(Locale.ROOT)).format(base);
                case "*" -> base * num(p.getFirst());
                case "/" -> base / num(p.getFirst());
                default -> Results.NotFound.from(context);
            }));
        }
    }

    @EngineConfiguration
    public static class StringFunctions implements ValueResolver {
        @Override
        public boolean appliesTo(EvalContext context) {
            return context.getBase() != null && !(context.getBase() instanceof Number && NUMBER.contains(context.getName()))
                    && !Results.isNotFound(context.getBase()) && STRING.contains(context.getName());
        }

        @Override
        public CompletionStage<Object> resolve(EvalContext context) {
            var base = String.valueOf(context.getBase());
            return params(context, p -> switch (context.getName()) {
                case "upper" -> base.toUpperCase(Locale.ROOT);
                case "lower" -> base.toLowerCase(Locale.ROOT);
                case "trim" -> base.strip();
                case "pad" -> StringUtils.rightPad(base, (int) num(p.getFirst()));
                case "padStart" -> StringUtils.leftPad(base, (int) num(p.getFirst()), p.size() > 1 ? String.valueOf(p.get(1)) : " ");
                case "truncate" -> StringUtils.abbreviate(base, "…", Math.max(2, (int) num(p.getFirst())));
                case "replace" -> base.replace(String.valueOf(p.get(0)), String.valueOf(p.get(1)));
                case "urlencode" -> URLEncoder.encode(base, StandardCharsets.UTF_8);
                case "json" -> json(base);
                default -> Results.NotFound.from(context);
            });
        }

        private static String json(String value) {
            var sb = new StringBuilder("\"");
            for (var c : value.toCharArray()) {
                switch (c) {
                    case '"' -> sb.append("\\\"");
                    case '\\' -> sb.append("\\\\");
                    case '\n' -> sb.append("\\n");
                    case '\r' -> sb.append("\\r");
                    case '\t' -> sb.append("\\t");
                    default -> {
                        if (c < 0x20) {
                            sb.append(String.format("\\u%04x", (int) c));
                        } else {
                            sb.append(c);
                        }
                    }
                }
            }
            return sb.append('"').toString();
        }
    }

    @EngineConfiguration
    public static class MathFunctions implements NamespaceResolver {
        @Override
        public String getNamespace() {
            return MATH_NAMESPACE;
        }

        @Override
        public CompletionStage<Object> resolve(EvalContext context) {
            if (!MATH.contains(context.getName()) || context.getParams().size() < 2) {
                return CompletableFuture.completedFuture(Results.NotFound.from(context));
            }
            return params(context, p -> normalize(switch (context.getName()) {
                case "min" -> p.stream().mapToDouble(TemplateFunctions::num).min().orElse(0);
                default -> p.stream().mapToDouble(TemplateFunctions::num).max().orElse(0);
            }));
        }
    }
}
