package com.getpcpanel.integration.program;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.getpcpanel.template.TemplateSegment;

class CommandLineTest {
    /** Values a Discord member, a device or a session could be named, aimed at cmd.exe and at argument parsing. */
    private static final List<String> HOSTILE = List.of(
            "plain", "John Smith", "x\" & calc & \"", "a\"b", "\"", "\"\"", "%PATH%", "%%", "100%", "!x!",
            "a\\", "a\\\\", "a\\\"b", "\\\"", "\\", "C:\\Program Files\\", "^&|<>()", "& calc", "| calc",
            "\"%\"%\"", "x\"^&calc", "line1\nline2", "%\"PATH\"%", "\\\\\"&calc&\\\\\"");

    /**
     * A template (a {@code {{v}}} marks where the value goes), what cmd.exe should run, and each command's arguments
     * with {@code V} standing for the value as the program must receive it.
     */
    static Stream<Arguments> contexts() {
        return Stream.of(
                Arguments.of("prog.exe {{v}}", List.of(List.of("prog.exe", "V"))),
                Arguments.of("prog.exe \"{{v}}\"", List.of(List.of("prog.exe", "V"))),
                Arguments.of("prog.exe --name={{v}}", List.of(List.of("prog.exe", "--name=V"))),
                Arguments.of("prog.exe \"Hello {{v}}, bye\"", List.of(List.of("prog.exe", "Hello V, bye"))),
                Arguments.of("prog.exe C:\\dir\\{{v}}", List.of(List.of("prog.exe", "C:\\dir\\V"))),
                Arguments.of("prog.exe \"C:\\dir\\{{v}}\"", List.of(List.of("prog.exe", "C:\\dir\\V"))),
                Arguments.of("prog.exe {{v}}{{v}} last", List.of(List.of("prog.exe", "VV", "last"))),
                Arguments.of("prog.exe \"{{v}}\" \"{{v}}\"", List.of(List.of("prog.exe", "V", "V"))),
                Arguments.of("prog.exe {{v}} && other.exe {{v}}", List.of(List.of("prog.exe", "V"), List.of("other.exe", "V"))),
                Arguments.of("prog.exe \"{{v}}\" | more", List.of(List.of("prog.exe", "V"), List.of("more"))));
    }

    static Stream<Arguments> cases() {
        return contexts().flatMap(c -> HOSTILE.stream().map(v -> Arguments.of(c.get()[0], c.get()[1], v)));
    }

    @ParameterizedTest(name = "{0} with {2}")
    @MethodSource("cases")
    void cmdLineKeepsTemplateOutputAsTextInItsArgument(String template, List<List<String>> expected, String value) {
        var line = CommandLine.cmdLine(segments(template, value));
        var cmd = Cmd.parse(line);

        assertFalse(cmd.expandable, () -> "a % reaches variable expansion: " + line);
        var received = value.replace('\n', ' ');
        var want = expected.stream().map(args -> args.stream().map(a -> a.replace("V", received)).toList()).toList();
        assertEquals(want, cmd.commands.stream().map(Crt::argv).toList(), line);
    }

    @Test
    void userTextIsCopiedAsWritten() {
        var line = "prog.exe \"a b\" ^& %USERPROFILE% && echo done | more > out.txt";
        assertEquals(line, CommandLine.cmdLine(List.of(TemplateSegment.literal(line))));
    }

    @Test
    void valueAfterAUserTextTheParsersReadDifferentlyKeepsOnlyPlainCharacters() {
        // ^" is a quote for the program but not for cmd.exe; no encoding is right for both.
        var line = CommandLine.cmdLine(segments("prog.exe ^\"{{v}}", "a\" & calc & \"b"));
        assertEquals("prog.exe ^\"a  calc  b", line);
        assertEquals(1, Cmd.parse(line).commands.size());
    }

    @Test
    void argvSplitsOnlyTheUsersText() {
        assertArrayEquals(new String[] { "notify-send", "John Smith", "--urgency=low" },
                CommandLine.argv(segments("notify-send {{v}} --urgency=low", "John Smith")));
        assertArrayEquals(new String[] { "tool", "--name=a b\tc", "x" }, CommandLine.argv(segments("tool  --name={{v}} x", "a b\tc")));
        assertArrayEquals(new String[] { "tool", "\"; rm -rf ~; \"" }, CommandLine.argv(segments("tool {{v}}", "\"; rm -rf ~; \"")));
    }

    @Test
    void argvDropsAnArgumentThatRendersEmpty() {
        assertArrayEquals(new String[] { "tool", "x" }, CommandLine.argv(segments("tool {{v}} x", "")));
        assertArrayEquals(new String[] { "tool", "-a", "b" }, CommandLine.argv(List.of(TemplateSegment.literal(" tool\t-a  b "))));
        assertThrows(IllegalArgumentException.class, () -> CommandLine.argv(segments("{{v}}", "")));
    }

    private static List<TemplateSegment> segments(String template, String value) {
        var out = new ArrayList<TemplateSegment>();
        var parts = template.split(Pattern.quote("{{v}}"), -1);
        for (var i = 0; i < parts.length; i++) {
            if (!parts[i].isEmpty()) {
                out.add(TemplateSegment.literal(parts[i]));
            }
            if (i < parts.length - 1) {
                out.add(TemplateSegment.value(value));
            }
        }
        return out;
    }

    /** What cmd.exe makes of a {@code /c} line: the commands its operators separate, carets removed. */
    private record Cmd(List<String> commands, boolean expandable) {
        static Cmd parse(String line) {
            var commands = new ArrayList<String>();
            var current = new StringBuilder();
            var quoted = false;
            var expandable = false;
            for (var i = 0; i < line.length(); i++) {
                var c = line.charAt(i);
                if (c == '%' && (quoted || i == 0 || line.charAt(i - 1) != '^')) {
                    expandable = true;
                }
                if (!quoted && c == '^' && i + 1 < line.length()) {
                    current.append(line.charAt(++i));
                } else if (!quoted && "&|<>".indexOf(c) >= 0) {
                    if (!current.toString().isBlank()) {
                        commands.add(current.toString().strip());
                    }
                    current.setLength(0);
                } else {
                    if (c == '"') {
                        quoted = !quoted;
                    }
                    current.append(c);
                }
            }
            if (!current.toString().isBlank()) {
                commands.add(current.toString().strip());
            }
            // A literal % the template's user text carries is theirs; the hostile values are what is checked.
            return new Cmd(commands, expandable);
        }
    }

    /** The C runtime's argument parsing (the rules of the Universal CRT, including "" inside quotes). */
    private static final class Crt {
        static List<String> argv(String line) {
            var args = new ArrayList<String>();
            var current = new StringBuilder();
            var inArg = false;
            var quoted = false;
            var i = 0;
            while (i < line.length()) {
                var c = line.charAt(i);
                if (c == '\\') {
                    var n = 0;
                    while (i < line.length() && line.charAt(i) == '\\') {
                        n++;
                        i++;
                    }
                    if (i < line.length() && line.charAt(i) == '"') {
                        current.append("\\".repeat(n / 2));
                        if (n % 2 == 1) {
                            current.append('"');
                            i++;
                        }
                    } else {
                        current.append("\\".repeat(n));
                    }
                    inArg = true;
                    continue;
                }
                if (c == '"') {
                    if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        current.append('"');
                        i += 2;
                    } else {
                        quoted = !quoted;
                        i++;
                    }
                    inArg = true;
                    continue;
                }
                if (!quoted && (c == ' ' || c == '\t')) {
                    if (inArg) {
                        args.add(current.toString());
                        current.setLength(0);
                        inArg = false;
                    }
                    i++;
                    continue;
                }
                current.append(c);
                inArg = true;
                i++;
            }
            if (inArg) {
                args.add(current.toString());
            }
            return args;
        }
    }
}
