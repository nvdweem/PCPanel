package com.getpcpanel.profile.compat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.getpcpanel.AppLikeMapper;
import com.getpcpanel.StubBeans;
import com.getpcpanel.commands.Commands;
import com.getpcpanel.commands.CommandsType;
import com.getpcpanel.commands.command.ButtonAction;
import com.getpcpanel.commands.command.Command;
import com.getpcpanel.commands.command.CommandNoOp;
import com.getpcpanel.commands.command.DialAction;
import com.getpcpanel.device.provider.pcpanel.DescriptorFactory;
import com.getpcpanel.device.provider.pcpanel.DeviceType;
import com.getpcpanel.profile.DeviceSave;
import com.getpcpanel.profile.Profile;
import com.getpcpanel.profile.Save;

/**
 * Produces the newest link in the fixture chain {@code LegacySaveCompatibilityTest} loads, currently
 * {@code src/test/resources/legacy-saves/profiles-2.0.json}. It upgrades the previous version's
 * fixture exactly as running this build over it would: read it with today's model, then write it back,
 * filling in properties this version added and assigning commands this version introduced.
 *
 * <p>Not a test — run it by hand when the save format changes, review the diff, and commit it:
 * <pre>{@code
 * mvn -q test-compile -Dquarkus.native.enabled=false -Dquarkus.quinoa.enabled=false
 * java -cp target/classes:target/test-classes:<deps> com.getpcpanel.profile.compat.SaveFixtureGenerator \
 *      src/test/resources/legacy-saves/profiles-1.8.json src/test/resources/legacy-saves/profiles-2.0.json
 * }</pre>
 *
 * <p>The older fixtures are frozen artifacts of the versions that wrote them and must never be
 * regenerated — see {@code src/test/resources/legacy-saves/README.md}.
 */
public final class SaveFixtureGenerator {
    /** Structural members laid out below rather than by the reflective populator. */
    private static final Set<String> SKIP = Set.of(
            "Save#devices",
            "DeviceSave#profiles", "DeviceSave#displayName", "DeviceSave#currentProfileName",
            "DeviceSave#providerId", "DeviceSave#deviceKindId", "DeviceSave#capabilities",
            "Profile#name", "Profile#lightingConfig", "Profile#isMainProfile", "Profile#isBaseLayer",
            "Profile#knobSettings", "Profile#oscBinding");

    /** Which PCPanel model each fixture device stands for, so its descriptor is the real one. */
    private static final Map<String, DeviceType> DEVICE_KINDS = Map.of(
            "pro-panel", DeviceType.PCPANEL_PRO,
            "rgb-panel", DeviceType.PCPANEL_RGB,
            "mini-panel", DeviceType.PCPANEL_MINI);

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            throw new IllegalArgumentException("usage: SaveFixtureGenerator <previous-fixture.json> <output.json>");
        }
        StubBeans.install();
        var mapper = AppLikeMapper.build();
        var save = mapper.readValue(Files.readString(Path.of(args[0]), StandardCharsets.UTF_8), Save.class);
        var carriedOver = commandTypesIn(save);
        System.out.println("Read " + args[0] + ": " + save.getDevices().size() + " devices, " + carriedOver.size() + " command types");

        var populator = new SavePopulator(SKIP);
        identifyDevices(save);
        for (var device : save.getDevices().values()) {
            populator.topUp(device);
            for (var profile : device.getProfiles()) {
                populator.topUp(profile);
                var lighting = profile.lightingConfig();
                populator.topUp(lighting);
                profile.setLightingConfig(lighting);
                for (var knob : profile.getKnobSettings().values()) {
                    populator.topUp(knob);
                }
            }
        }
        populator.topUp(save);
        assignNewCommands(populator, save, carriedOver);
        assignReleaseSlots(save);

        var json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(save);
        Files.writeString(Path.of(args[1]), json, StandardCharsets.UTF_8);
        verify(mapper, json);
        System.out.println("Wrote " + args[1] + " (" + json.length() + " bytes)");
    }

    /**
     * Gives each device the provider identity a save written by this version carries. Legacy files
     * have none; {@code SaveService.migrateProviderIds} back-fills {@code providerId} on load and the
     * rest arrives from the live descriptor at connect time, which is what is reproduced here.
     */
    private static void identifyDevices(Save save) {
        for (var device : save.getDevices().values()) {
            var descriptor = DescriptorFactory.forType(DEVICE_KINDS.getOrDefault(device.getDisplayName(), DeviceType.PCPANEL_PRO));
            device.setProviderId(descriptor.providerId());
            device.setDeviceKindId(descriptor.deviceKindId());
            device.setCapabilities(descriptor);
        }
    }

    /** Assigns every command type the previous fixture lacks to a free slot on the busiest profile. */
    private static void assignNewCommands(SavePopulator populator, Save save, Set<Class<?>> present) {
        var target = busiestProfile(save);
        var dial = nextFree(target.getDialData().keySet());
        var button = nextFree(target.getButtonData().keySet());
        var added = new ArrayList<String>();
        for (var clazz : concreteCommands()) {
            if (present.contains(clazz)) {
                continue;
            }
            var command = (Command) populator.construct(clazz, 0);
            if (command instanceof DialAction) {
                target.setDialData(dial++, new Commands(List.of(command), CommandsType.allAtOnce));
            } else {
                target.setButtonData(button++, new Commands(List.of(command), CommandsType.allAtOnce));
            }
            added.add(clazz.getSimpleName());
        }
        System.out.println("Commands new since the previous fixture: " + (added.isEmpty() ? "(none)" : added));
    }

    /** Mirrors a couple of button assignments onto the release map, which older versions had no notion of. */
    private static void assignReleaseSlots(Save save) {
        var target = busiestProfile(save);
        var buttons = target.getButtonData().values().stream()
                            .flatMap(c -> c.getCommands().stream())
                            .filter(ButtonAction.class::isInstance)
                            .limit(2)
                            .toList();
        if (buttons.size() == 2) {
            target.getReleaseButtonData().put(0, new Commands(List.of(buttons.get(0)), CommandsType.allAtOnce));
            target.getReleaseButtonData().put(1, new Commands(buttons, CommandsType.sequential));
        }
    }

    private static Profile busiestProfile(Save save) {
        return save.getDevices().values().stream()
                   .flatMap(d -> d.getProfiles().stream())
                   .max(Comparator.comparingInt(p -> p.getDialData().size() + p.getButtonData().size()))
                   .orElseThrow();
    }

    private static int nextFree(Set<Integer> used) {
        return used.stream().mapToInt(Integer::intValue).max().orElse(-1) + 1;
    }

    private static void verify(ObjectMapper mapper, String json) throws Exception {
        var reread = mapper.readValue(json, Save.class);
        var seen = commandTypesIn(reread);
        var expected = new HashSet<>(concreteCommands());
        if (!seen.equals(expected)) {
            var missing = new TreeSet<String>();
            expected.stream().filter(c -> !seen.contains(c)).map(Class::getName).forEach(missing::add);
            throw new IllegalStateException("The generated fixture does not round-trip every command. Missing: " + missing);
        }
        System.out.println("Round-tripped " + seen.size() + " command types");
    }

    /**
     * Every command that can appear in a saved file, sorted for a stable layout. {@code CommandNoOp}
     * is excluded because the {@link Commands} constructor strips it, so no save can contain one.
     */
    static List<Class<?>> concreteCommands() {
        return AppLikeMapper.scanProjectClasses().stream()
                            .<Class<?>>map(c -> c)
                            .filter(c -> Command.class.isAssignableFrom(c) && c != Command.class
                                    && !c.isInterface() && !java.lang.reflect.Modifier.isAbstract(c.getModifiers())
                                    && c != CommandNoOp.class)
                            .sorted(Comparator.comparing(Class::getName))
                            .toList();
    }

    static Set<Class<?>> commandTypesIn(Save save) {
        var seen = new HashSet<Class<?>>();
        for (var device : save.getDevices().values()) {
            for (var profile : device.getProfiles()) {
                for (var map : List.of(profile.getButtonData(), profile.getDblButtonData(), profile.getReleaseButtonData(), profile.getDialData())) {
                    for (var commands : map.values()) {
                        for (var command : commands.getCommands()) {
                            seen.add(command.getClass());
                        }
                    }
                }
            }
        }
        return seen;
    }

    private SaveFixtureGenerator() {
    }
}
