package com.getpcpanel.integration.volume.platform.windows;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.annotation.Annotation;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.getpcpanel.AppLikeMapper;
import com.getpcpanel.Json;
import com.getpcpanel.StubBeans;
import com.getpcpanel.commands.Commands;
import com.getpcpanel.commands.CommandsType;
import com.getpcpanel.device.DeviceHolder;
import com.getpcpanel.device.provider.pcpanel.DeviceType;
import com.getpcpanel.integration.volume.command.CommandVolumeDefaultDevice;
import com.getpcpanel.integration.volume.command.CommandVolumeDefaultDeviceAdvanced;
import com.getpcpanel.integration.volume.command.CommandVolumeDefaultDeviceToggle;
import com.getpcpanel.integration.volume.platform.DataFlow;
import com.getpcpanel.profile.Profile;
import com.getpcpanel.profile.SaveService;
import com.getpcpanel.template.TemplateSaveMigration;
import com.getpcpanel.util.concurrent.Debouncer;
import com.getpcpanel.util.io.FileUtil;

import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.NotificationOptions;
import jakarta.enterprise.util.TypeLiteral;

/** The recovery end to end: a configuration keyed to a replaced endpoint id ends up keyed to its replacement. */
class EndpointRecoveryTest {
    private static final String OLD = "{0.0.0.00000000}.{23f7633c-2cd8-44ee-9518-6fa31ed81f28}";
    private static final String NEW = "{0.0.0.00000000}.{11111111-2222-3333-4444-555555555555}";
    private static final String MODI = "{0.0.0.00000000}.{ca7ccf6a-31dd-4ffa-ad71-1880c58f124e}";

    @TempDir Path tmp;
    private SaveService saveService;
    private FakeSndCtrl sndCtrl;
    private EndpointRecovery sut;
    private long now = 1_000;

    @BeforeEach
    void setUp() throws Exception {
        StubBeans.install();
        var fileUtil = new FileUtil();
        var root = FileUtil.class.getDeclaredField("rootPath");
        root.setAccessible(true);
        root.set(fileUtil, tmp.toString());
        var ensure = FileUtil.class.getDeclaredMethod("ensureRoot");
        ensure.setAccessible(true);
        ensure.invoke(fileUtil);

        var mapper = AppLikeMapper.build();
        var json = new Json();
        var mapperField = Json.class.getDeclaredField("mapper");
        mapperField.setAccessible(true);
        mapperField.set(json, mapper);

        saveService = new SaveService();
        set(saveService, "fileUtil", fileUtil);
        set(saveService, "json", json);
        set(saveService, "devices", new DeviceHolder());
        set(saveService, "templateMigration", new TemplateSaveMigration());
        set(saveService, "eventBus", new NoEvents());
        set(saveService, "debouncer", new Debouncer());
        set(saveService, "originalSaveFile", (java.util.function.Supplier<java.io.File>) () -> null);
        saveService.load();
        saveService.get().createSaveForNewDevice("dev", DeviceType.PCPANEL_PRO);
        var profile = new Profile("A", DeviceType.PCPANEL_PRO);
        profile.setButtonData(3, new Commands(List.of(new CommandVolumeDefaultDevice(OLD)), CommandsType.allAtOnce));
        profile.setButtonData(1, new Commands(List.of(new CommandVolumeDefaultDeviceToggle(List.of(OLD, MODI))), CommandsType.allAtOnce));
        saveService.get().getDeviceSave("dev").getProfiles().add(profile);
        saveService.save();

        sndCtrl = new FakeSndCtrl();
        sut = new EndpointRecovery();
        sut.sndCtrl = sndCtrl;
        sut.saveService = saveService;
        sut.json = json;
        sut.mapper = mapper;
        sut.fileUtil = fileUtil;
        sut.clock = () -> now;
    }

    private static void set(Object target, String field, Object value) throws Exception {
        var f = SaveService.class.getDeclaredField(field);
        f.setAccessible(true);
        f.set(target, value);
    }

    private String saveText() throws Exception {
        return Files.readString(tmp.resolve("profiles.json"));
    }

    @Test
    void aReplacedEndpointIsFollowedEverywhereItIsUsed() throws Exception {
        // A session with the old endpoint present: it is remembered.
        sndCtrl.present("LSX II (High Definition Audio Device)", OLD, "High Definition Audio Device");
        sndCtrl.present("Speakers (Schiit Modi 3+)", MODI, "Schiit Modi 3+");
        sut.check();
        assertTrue(Files.isRegularFile(tmp.resolve(EndpointRecovery.FILE)));

        // After an update Windows built the HDA endpoint anew, and the rename went with the old one.
        now = 2_000;
        sndCtrl.clear();
        sndCtrl.present("Speakers (High Definition Audio Device)", NEW, "High Definition Audio Device");
        sndCtrl.present("Speakers (Schiit Modi 3+)", MODI, "Schiit Modi 3+");
        sut.check();

        var text = saveText();
        assertFalse(text.contains(OLD), text);
        assertEquals(2, text.split(java.util.regex.Pattern.quote(NEW), -1).length - 1, "the action and the toggle list both follow");
        assertTrue(text.contains(MODI));
        assertFalse(Files.readString(tmp.resolve(EndpointRecovery.FILE)).contains(OLD), "the replaced endpoint is forgotten");

        assertTrue(saveService.undo(), "the rewrite can be undone");
        assertTrue(saveText().contains(OLD));
    }

    @Test
    void anIdConfiguredBeforeTheAppRememberedItIsDescribedByWindows() throws Exception {
        sndCtrl.described.put(OLD, new KnownEndpoint("Speakers (Schiit Modi 3+)", "Schiit Modi 3+", DataFlow.dfRender, 0, 0));
        sndCtrl.present("Speakers (Schiit Modi 3+)", NEW, "Schiit Modi 3+");
        sut.check();
        assertFalse(saveText().contains(OLD));
        assertTrue(saveText().contains(NEW));
    }

    @Test
    void aDeviceThatIsMerelyUnpluggedKeepsItsActions() throws Exception {
        sndCtrl.present("Speakers (Schiit Modi 3+)", MODI, "Schiit Modi 3+");
        sndCtrl.present("LSX II (High Definition Audio Device)", OLD, "High Definition Audio Device");
        sut.check();
        now = 2_000;
        sndCtrl.clear();
        sndCtrl.present("Speakers (Schiit Modi 3+)", MODI, "Schiit Modi 3+");
        sut.check();
        assertTrue(saveText().contains(OLD));
    }

    @Test
    void anAdvancedDefaultDeviceNameIsPinnedToItsOnlyDeviceInOneUndoableStep() throws Exception {
        var profile = saveService.get().getDeviceSave("dev").getProfiles().stream().filter(p -> "A".equals(p.getName())).findFirst().orElseThrow();
        profile.setButtonData(4, new Commands(List.of(CommandVolumeDefaultDeviceAdvanced.builder().mediaPb("Schiit").build()), CommandsType.allAtOnce));
        saveService.save();
        sndCtrl.present("Speakers (Schiit Modi 3+)", MODI, "Schiit Modi 3+");

        sut.check();
        var pinned = saveText();
        sut.check();

        assertTrue(pinned.contains("\"mediaPb\" : \"" + MODI + "\""), pinned);
        assertTrue(pinned.contains("\"mediaPbName\" : \"Schiit\""), pinned);
        assertEquals(pinned, saveText(), "a second check finds nothing left to pin");
        assertTrue(saveService.undo());
        assertTrue(saveText().contains("\"mediaPb\" : \"Schiit\""), "undo reaches the state before pinning, so the second check added no step");
    }

    private static final class FakeSndCtrl extends SndCtrlWindows {
        private final Map<String, WindowsAudioDevice> present = new HashMap<>();
        final Map<String, KnownEndpoint> described = new HashMap<>();

        void present(String name, String id, String interfaceName) {
            present.put(id, new WindowsAudioDevice(null, null, name, id, interfaceName).dataflow(DataFlow.dfRender));
        }

        void clear() {
            present.clear();
        }

        @Override
        public Map<String, com.getpcpanel.integration.volume.platform.AudioDevice> getDevicesMap() {
            return new HashMap<>(present);
        }

        @Override
        public Optional<KnownEndpoint> describeEndpoint(String deviceId) {
            return Optional.ofNullable(described.get(deviceId));
        }
    }

    private static final class NoEvents implements Event<Object> {
        @Override public void fire(Object event) {}
        @Override public <U> CompletionStage<U> fireAsync(U event) { return null; }
        @Override public <U> CompletionStage<U> fireAsync(U event, NotificationOptions options) { return null; }
        @Override public Event<Object> select(Annotation... qualifiers) { return this; }
        @Override public <U> Event<U> select(Class<U> subtype, Annotation... qualifiers) { return null; }
        @Override public <U> Event<U> select(TypeLiteral<U> subtype, Annotation... qualifiers) { return null; }
    }

}
