package com.getpcpanel.integration.keyboard;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.annotation.Nullable;

/**
 * Canonicalises the tokens of the cross-platform "{@code modifier+modifier+key}" keystroke format, so
 * every platform backend keys its virtual-key table on one vocabulary.
 *
 * <p>Combos reach the backend written two ways. The Angular key recorder reports display-cased
 * modifier labels and DOM {@code KeyboardEvent.key} names ({@code "Ctrl+Shift+ArrowLeft"}); saved
 * profiles and hand-written values use lowercase modifiers and AWT {@code VK_}-suffix key names
 * ({@code "ctrl+shift+LEFT"}). Both normalise here to the AWT names, so a combo behaves the same
 * however it was authored.
 *
 * <p>Punctuation is resolved against the US layout, matching the platform tables: a shifted character
 * maps to the physical key that produces it ({@code "<"} → {@code COMMA}), which the {@code Shift} the
 * recorder captured alongside it then modifies.
 */
public final class KeystrokeTokens {
    /** The modifiers a combo can carry, independent of how a platform encodes them. */
    public enum Modifier {
        CTRL, SHIFT, ALT, META
    }

    private static final Map<String, Modifier> MODIFIERS = buildModifiers();
    private static final Map<String, String> KEY_ALIASES = buildKeyAliases();

    private KeystrokeTokens() {
    }

    /**
     * Splits a combo into its tokens — the leading ones name modifiers, the last names the key.
     * Whitespace around a token is dropped and stray separators are skipped, so {@code "ctrl + a"} and
     * {@code "ctrl+a"} are the same combo. A token that is only whitespace is the space bar.
     */
    public static List<String> split(String combo) {
        List<String> tokens = new ArrayList<>();
        for (var raw : combo.split("\\+", -1)) {
            if (raw.isEmpty()) {
                continue;
            }
            tokens.add(raw.isBlank() ? "SPACE" : raw.trim());
        }
        return tokens;
    }

    /** The modifier a token names, or null when it names none. */
    @Nullable
    public static Modifier modifier(String token) {
        return MODIFIERS.get(token.trim().toLowerCase(Locale.ROOT));
    }

    /** The AWT {@code VK_}-suffix name a token denotes, uppercased; unknown tokens pass through. */
    public static String key(String token) {
        if (token.isBlank()) {
            return "SPACE";
        }
        var upper = token.trim().toUpperCase(Locale.ROOT);
        return KEY_ALIASES.getOrDefault(upper, upper);
    }

    private static Map<String, Modifier> buildModifiers() {
        Map<String, Modifier> m = new HashMap<>();
        for (var name : List.of("ctrl", "control", "ctl")) {
            m.put(name, Modifier.CTRL);
        }
        m.put("shift", Modifier.SHIFT);
        for (var name : List.of("alt", "option", "opt")) {
            m.put(name, Modifier.ALT);
        }
        for (var name : List.of("cmd", "command", "windows", "win", "meta", "super", "os")) {
            m.put(name, Modifier.META);
        }
        return m;
    }

    @SuppressWarnings("java:S138") // long but flat lookup table
    private static Map<String, String> buildKeyAliases() {
        Map<String, String> m = new HashMap<>();
        // DOM KeyboardEvent.key names, and the short spellings people write by hand.
        m.put("ARROWLEFT", "LEFT"); m.put("ARROWRIGHT", "RIGHT");
        m.put("ARROWUP", "UP"); m.put("ARROWDOWN", "DOWN");
        m.put("PAGEUP", "PAGE_UP"); m.put("PAGEDOWN", "PAGE_DOWN");
        m.put("BACKSPACE", "BACK_SPACE"); m.put("RETURN", "ENTER");
        m.put("ESC", "ESCAPE"); m.put("DEL", "DELETE"); m.put("INS", "INSERT");
        m.put("SPACEBAR", "SPACE"); m.put("PLUS", "EQUALS");
        // Printable punctuation, unshifted and shifted, as the recorder reports it.
        m.put("-", "MINUS"); m.put("_", "MINUS");
        m.put("=", "EQUALS"); m.put("+", "EQUALS");
        m.put("[", "OPEN_BRACKET"); m.put("{", "OPEN_BRACKET");
        m.put("]", "CLOSE_BRACKET"); m.put("}", "CLOSE_BRACKET");
        m.put("\\", "BACK_SLASH"); m.put("|", "BACK_SLASH");
        m.put(";", "SEMICOLON"); m.put(":", "SEMICOLON");
        m.put("'", "QUOTE"); m.put("\"", "QUOTE");
        m.put(",", "COMMA"); m.put("<", "COMMA");
        m.put(".", "PERIOD"); m.put(">", "PERIOD");
        m.put("/", "SLASH"); m.put("?", "SLASH");
        m.put("`", "BACK_QUOTE"); m.put("~", "BACK_QUOTE");
        // Shifted digits resolve to the digit key that produces them.
        m.put("!", "1"); m.put("@", "2"); m.put("#", "3"); m.put("$", "4"); m.put("%", "5");
        m.put("^", "6"); m.put("&", "7"); m.put("*", "8"); m.put("(", "9"); m.put(")", "0");
        return m;
    }
}
