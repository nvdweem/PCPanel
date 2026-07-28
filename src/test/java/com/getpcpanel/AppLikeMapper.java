package com.getpcpanel;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.getpcpanel.commands.CommandModule;
import com.getpcpanel.commands.CommandSubtypeRegistrar;

/**
 * Builds an {@link ObjectMapper} shaped like the one the running app uses, without booting Quarkus.
 * A {@code @QuarkusTest} is unsafe for this: {@code DeviceProviderRegistry} starts every device
 * provider on {@code StartupEvent}, which scans real HID/serial/MIDI hardware, and there is no
 * {@code %test} config gate for it.
 *
 * <p>The shape that matters for JSON compatibility is the registered datatype modules,
 * {@code quarkus.jackson.fail-on-unknown-properties=false} (see {@code application.properties}), and
 * the {@link CommandSubtypeRegistrar} customizer wired with every {@link CommandModule} on the
 * classpath — the real customizer instance, so the legacy-id aliases it installs are exercised
 * rather than re-implemented.
 */
public final class AppLikeMapper {
    private AppLikeMapper() {
    }

    public static ObjectMapper build() {
        return build(scanProjectClasses());
    }

    public static ObjectMapper build(List<Class<?>> projectClasses) {
        var mapper = new ObjectMapper().findAndRegisterModules()
                                       .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        var modules = new ArrayList<CommandModule>();
        for (var clazz : projectClasses) {
            if (CommandModule.class.isAssignableFrom(clazz) && !clazz.isInterface() && !java.lang.reflect.Modifier.isAbstract(clazz.getModifiers())) {
                try {
                    modules.add((CommandModule) clazz.getDeclaredConstructor().newInstance());
                } catch (Exception e) {
                    throw new IllegalStateException("CommandModule " + clazz.getName() + " is not no-arg instantiable", e);
                }
            }
        }
        if (modules.size() < 5) {
            throw new IllegalStateException("expected the CommandModule scan to find the feature modules, found " + modules.size());
        }
        try {
            var registrar = new CommandSubtypeRegistrar();
            var field = CommandSubtypeRegistrar.class.getDeclaredField("modules");
            field.setAccessible(true);
            field.set(registrar, modules);
            registrar.customize(mapper);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("cannot wire CommandSubtypeRegistrar", e);
        }
        return mapper;
    }

    /** Every compiled {@code com.getpcpanel.**} class, for reflective discovery in tests. */
    public static List<Class<?>> scanProjectClasses() {
        try {
            var classesRoot = Path.of(CommandModule.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            var loader = AppLikeMapper.class.getClassLoader();
            var result = new ArrayList<Class<?>>();
            try (Stream<Path> walk = Files.walk(classesRoot.resolve("com").resolve("getpcpanel"))) {
                for (var classFile : walk.filter(p -> p.toString().endsWith(".class")).toList()) {
                    var relative = classesRoot.relativize(classFile).toString();
                    var binaryName = relative.substring(0, relative.length() - ".class".length()).replace(File.separatorChar, '.');
                    try {
                        result.add(Class.forName(binaryName, false, loader));
                    } catch (Throwable e) { // optional platform deps etc.
                    }
                }
            }
            return result;
        } catch (Exception e) {
            throw new IllegalStateException("cannot scan project classes", e);
        }
    }
}
