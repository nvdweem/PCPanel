package com.getpcpanel.integration.program;

import java.util.ArrayList;
import java.util.List;

import com.getpcpanel.template.TemplateSegment;

/**
 * Builds the process a Run command starts from its rendered template. The user's own text keeps its meaning (their
 * spaces split arguments, their quotes, pipes and {@code &&} work as they wrote them); what a {@code {{ … }}} tag
 * produced is always data. Tag output can come from outside the app (a Discord member's display name, a device or
 * session name), so it must never add an argument of its own or reach a shell as syntax.
 */
final class CommandLine {
    /** What {@link java.util.StringTokenizer} splits on, as {@code Runtime.exec} does. */
    private static final String WHITESPACE = " \t\n\r\f";

    private CommandLine() {
    }

    /** The full text, as a plain render would produce it. */
    static String text(List<TemplateSegment> segments) {
        var out = new StringBuilder();
        segments.forEach(s -> out.append(s.text()));
        return out.toString();
    }

    /**
     * The arguments for a process started without a shell (Linux, macOS): the user's text splits on whitespace as
     * {@code Runtime.exec} splits it, tag output never does. A tag that renders nothing adds no argument.
     */
    static String[] argv(List<TemplateSegment> segments) {
        var args = new ArrayList<String>();
        var current = new StringBuilder();
        var started = false;
        for (var segment : segments) {
            if (!segment.literal()) {
                current.append(segment.text());
                started |= !segment.text().isEmpty();
                continue;
            }
            for (var c : segment.text().toCharArray()) {
                if (WHITESPACE.indexOf(c) >= 0) {
                    if (started) {
                        args.add(current.toString());
                        current.setLength(0);
                        started = false;
                    }
                } else {
                    current.append(c);
                    started = true;
                }
            }
        }
        if (started) {
            args.add(current.toString());
        }
        if (args.isEmpty()) {
            throw new IllegalArgumentException("Empty command");
        }
        return args.toArray(String[]::new);
    }

    /**
     * The line {@code cmd.exe /c} runs (Windows). The user's text is copied as written. Tag output is encoded so
     * that {@code cmd.exe} sees no syntax in it (no operator, quote or {@code %} expansion) and the started program
     * receives it as written, inside the argument it appears in: inside the user's quotes it stays in that quoted
     * argument, elsewhere it is quoted so its spaces do not split it.
     *
     * <p>Two parsers read the line: {@code cmd.exe} (a {@code "} toggles quoting, {@code ^} escapes the next character
     * outside quotes) and then the program (the C runtime's rules: a {@code "} toggles quoting unless an odd number
     * of backslashes precedes it). Their state is tracked over everything emitted, the user's text included, so each
     * value is encoded for where it really lands.
     */
    static String cmdLine(List<TemplateSegment> segments) {
        var line = new CmdLineWriter();
        for (var i = 0; i < segments.size(); i++) {
            var segment = segments.get(i);
            if (segment.literal()) {
                line.literal(segment.text());
            } else {
                var next = i + 1 < segments.size() && segments.get(i + 1).literal() ? segments.get(i + 1).text() : "";
                line.value(segment.text(), startsWithQuote(next));
            }
        }
        return line.toString();
    }

    /** Whether {@code text} starts with a {@code "}, possibly after backslashes (which then escape or pair with it). */
    private static boolean startsWithQuote(String text) {
        var i = 0;
        while (i < text.length() && text.charAt(i) == '\\') {
            i++;
        }
        return i < text.length() && text.charAt(i) == '"';
    }

    private static final class CmdLineWriter {
        private final StringBuilder out = new StringBuilder();
        // cmd.exe's state
        private boolean cmdQuoted;
        private boolean cmdCaret;
        // the program's state, over what cmd.exe passes on
        private boolean progQuoted;
        private int backslashes;
        /** A value's closing quote not yet written: a value straight after it would make it a literal "". */
        private boolean closePending;

        void literal(String text) {
            if (!text.isEmpty()) {
                closeValue();
            }
            text.chars().forEach(c -> emit((char) c));
        }

        void value(String text, boolean quoteFollows) {
            if (cmdCaret || cmdQuoted != progQuoted) {
                // The user's text left the two parsers disagreeing; no encoding is right for both, so only plain
                // characters pass.
                text.chars().filter(CmdLineWriter::plain).forEach(c -> emit((char) c));
                return;
            }
            if (!cmdQuoted && !closePending && !text.isEmpty() && text.chars().allMatch(CmdLineWriter::bare)) {
                // Nothing in it means anything to either parser, so it goes as is: a number or a name arrives exactly
                // as before templates were encoded, also for a built-in such as echo that prints quotes.
                text.chars().forEach(c -> emit((char) c));
                return;
            }
            // Ordinary characters go inside quotes, where cmd.exe reads nothing as syntax and spaces do not split the
            // argument. A quote and %/! cannot be made safe there, so they go outside, caret-escaped. Quotes are only
            // opened and closed when the next character needs it: a close directly followed by an open ("") is read
            // by the C runtime as a literal quote.
            var endQuoted = cmdQuoted && !closePending;
            for (var c : text.toCharArray()) {
                if (c == '"') {
                    leaveQuotes();
                    evenBackslashes(); // so the ones before it stay text, and this one escapes the quote
                    emit('\\'); // a literal quote for the program,
                    emit('^'); // and for cmd.exe
                    emit('"');
                } else if (c == '%' || c == '!') {
                    leaveQuotes(); // cmd.exe expands variables inside quotes too, and has no escape there
                    emit('^');
                    emit(c);
                } else {
                    enterQuotes();
                    emit(Character.isISOControl(c) ? ' ' : c); // a line break would end the command
                }
            }
            if (endQuoted) {
                enterQuotes(); // back in the user's quotes, which their text closes
                if (quoteFollows) {
                    evenBackslashes(); // so that closing quote keeps its meaning
                }
            } else if (cmdQuoted) {
                closePending = true; // a value straight after it continues inside these quotes
            }
        }

        private void enterQuotes() {
            if (closePending) {
                closePending = false;
            } else if (!cmdQuoted) {
                quote();
            }
        }

        private void leaveQuotes() {
            if (closePending) {
                closeValue();
            } else if (cmdQuoted) {
                quote();
            }
        }

        private void closeValue() {
            if (closePending) {
                closePending = false;
                quote();
            }
        }

        /** A {@code "} that toggles both parsers: the backslashes before it are doubled so they stay text. */
        private void quote() {
            evenBackslashes();
            emit('"');
        }

        private void evenBackslashes() {
            for (var i = backslashes; i > 0; i--) {
                emit('\\');
            }
        }

        /**
         * Safe outside quotes for cmd.exe and the C runtime alike: ASCII letters and digits and a few marks. No space
         * (it splits), no backslash (it escapes a quote the user's text may put next), no non-ASCII (a program reading
         * its arguments in the ANSI code page may turn a look-alike into a quote).
         */
        private static boolean bare(int c) {
            return c < 128 && Character.isLetterOrDigit(c) || "-_.:/+@#~".indexOf(c) >= 0;
        }

        private static boolean plain(int c) {
            return Character.isLetterOrDigit(c) || " -_.,:/+=@#~".indexOf(c) >= 0;
        }

        private void emit(char c) {
            out.append(c);
            if (cmdCaret) {
                cmdCaret = false;
                program(c);
            } else if (!cmdQuoted && c == '^') {
                cmdCaret = true; // cmd.exe drops it and keeps the next character as text
            } else {
                if (c == '"') {
                    cmdQuoted = !cmdQuoted;
                }
                program(c);
            }
        }

        private void program(char c) {
            if (c == '\\') {
                backslashes++;
                return;
            }
            if (c == '"' && backslashes % 2 == 0) {
                progQuoted = !progQuoted;
            }
            backslashes = 0;
        }

        @Override
        public String toString() {
            closeValue();
            return out.toString();
        }
    }
}
