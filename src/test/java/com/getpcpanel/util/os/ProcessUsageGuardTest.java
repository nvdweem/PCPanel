package com.getpcpanel.util.os;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.classfile.ClassFile;
import java.lang.classfile.instruction.InvokeInstruction;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * Every external process goes through {@link ProcessHelper}, which is what guarantees a deadline for anything
 * that is waited on, drained output, and an environment that only changes when the caller asks. Starting a
 * process directly bypasses all of that, so the compiled application must contain no other call that does.
 */
class ProcessUsageGuardTest {
    /** {@code owner.name} of every JDK method that starts a process. */
    static final Set<String> PROCESS_STARTERS = Set.of(
            "java/lang/ProcessBuilder.start",
            "java/lang/ProcessBuilder.startPipeline",
            "java/lang/Runtime.exec");
    private static final String ALLOWED_PREFIX = "com/getpcpanel/util/os/ProcessHelper";

    @Test
    void onlyProcessHelperStartsProcesses() throws Exception {
        var classes = applicationClasses();
        assertTrue(classes.size() > 100, "expected to scan the compiled application, found " + classes.size() + " classes in " + classesRoot());

        var violations = new ArrayList<String>();
        for (var file : classes) {
            violations.addAll(processStartsIn(Files.readAllBytes(file)));
        }

        assertEquals(List.of(), violations, "start processes through ProcessHelper instead");
    }

    /** The calls in one class file that start a process, as {@code Class.method -> owner.name}; empty for ProcessHelper. */
    static List<String> processStartsIn(byte[] classBytes) {
        var model = ClassFile.of().parse(classBytes);
        var className = model.thisClass().asInternalName();
        if (className.startsWith(ALLOWED_PREFIX)) {
            return List.of();
        }
        var found = new ArrayList<String>();
        for (var method : model.methods()) {
            method.code().ifPresent(code -> code.forEach(element -> {
                if (element instanceof InvokeInstruction invoke) {
                    var target = invoke.owner().asInternalName() + "." + invoke.name().stringValue();
                    if (PROCESS_STARTERS.contains(target)) {
                        found.add(className + "." + method.methodName().stringValue() + " -> " + target);
                    }
                }
            }));
        }
        return found;
    }

    private static List<Path> applicationClasses() throws IOException, URISyntaxException {
        try (var files = Files.walk(classesRoot())) {
            return files.filter(p -> p.toString().endsWith(".class")).toList();
        }
    }

    private static Path classesRoot() throws URISyntaxException {
        return Path.of(ProcessHelper.class.getProtectionDomain().getCodeSource().getLocation().toURI());
    }
}
