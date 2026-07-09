package com.getpcpanel.integration.osc.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.getpcpanel.commands.CommandMapperTestFactory;
import com.getpcpanel.commands.DialValue;
import com.getpcpanel.commands.command.Command;
import com.getpcpanel.commands.command.DialAction.DialActionParameters;
import com.getpcpanel.commands.command.DialAction.DialCommandParams;
import com.getpcpanel.integration.osc.OSCService;
import com.getpcpanel.integration.testutil.FakeCdi;
import com.getpcpanel.profile.dto.KnobSetting;

/**
 * {@link CommandOscSend}: JSON round-trip through the polymorphic mapper, and the value pipeline —
 * the 0..1 dial position maps through min/max or the exp4j formula and reaches
 * {@link OSCService#send} as a float, while a button press resolves at full scale. The service is a
 * hand-written recording stub served through {@link FakeCdi}.
 */
@DisplayName("CommandOscSend: JSON round-trip + value mapping")
class CommandOscSendTest {
    private final ObjectMapper mapper = CommandMapperTestFactory.mapperFor(new OscCommandModule());
    private final RecordingOscService osc = new RecordingOscService();

    @BeforeEach
    void setUp() {
        // per_class lifecycle (junit-platform.properties) shares one test instance, so the recording
        // service is reused across methods — reset it and re-register before each test.
        osc.reset();
        FakeCdi.register(OSCService.class, osc);
    }

    @AfterEach
    void tearDown() {
        FakeCdi.clear();
    }

    private static final class RecordingOscService extends OSCService {
        private final List<String> addresses = new ArrayList<>();
        private final List<Float> values = new ArrayList<>();

        @Override
        public void send(String address, float value) {
            addresses.add(address);
            values.add(value);
        }

        void reset() {
            addresses.clear();
            values.clear();
        }
    }

    private static DialActionParameters dialAt(int raw) {
        return new DialActionParameters("device", false, new DialValue((KnobSetting) null, raw));
    }

    @Test
    @DisplayName("osc.send round-trips with address, min/max, formula and dial params")
    void roundTrip() throws Exception {
        var json = mapper.writeValueAsString(new CommandOscSend("/mixer/ch1", 0.0, 1.0, "x*x", new DialCommandParams(true, null, null)));
        assertTrue(json.contains("\"osc.send\""), () -> "expected the nice id in: " + json);

        var loaded = assertInstanceOf(CommandOscSend.class, mapper.readValue(json, Command.class));
        assertEquals("/mixer/ch1", loaded.getAddress());
        assertEquals(0.0, loaded.getMin());
        assertEquals(1.0, loaded.getMax());
        assertEquals("x*x", loaded.getFormula());
        assertEquals(new DialCommandParams(true, null, null), loaded.getDialParams());
        assertEquals("OSC: /mixer/ch1", loaded.buildLabel());
    }

    @Test
    @DisplayName("nullable fields stay null through a round-trip")
    void roundTripWithNulls() throws Exception {
        var loaded = assertInstanceOf(CommandOscSend.class,
                mapper.readValue(mapper.writeValueAsString(new CommandOscSend("/a", null, null, null, null)), Command.class));
        assertNull(loaded.getMin());
        assertNull(loaded.getMax());
        assertNull(loaded.getFormula());
        assertNull(loaded.getDialParams());
    }

    @Test
    @DisplayName("a legacy FQCN _type id from an old save still loads")
    void legacyIdLoads() throws Exception {
        var loaded = mapper.readValue("{\"_type\":\"com.getpcpanel.commands.command.CommandOscSend\",\"address\":\"/x\"}", Command.class);
        assertEquals("/x", assertInstanceOf(CommandOscSend.class, loaded).getAddress());
    }

    @Test
    @DisplayName("the dial position maps linearly between min and max")
    void dialMapsLinearly() {
        var command = new CommandOscSend("/mixer/ch1", 0.0, 255.0, null, null);
        command.execute(dialAt(255));
        command.execute(dialAt(0));
        assertEquals(List.of("/mixer/ch1", "/mixer/ch1"), osc.addresses);
        assertEquals(List.of(255f, 0f), osc.values);
    }

    @Test
    @DisplayName("a formula maps the position through exp4j (variable x)")
    void dialMapsThroughFormula() {
        var command = new CommandOscSend("/fx", null, null, "x*10+1", null);
        command.execute(dialAt(255));
        assertEquals(List.of(11f), osc.values);
    }

    @Test
    @DisplayName("a button press sends at full scale (x = 1)")
    void buttonSendsFullScale() {
        var command = new CommandOscSend("/go", 0.0, 5.0, null, null);
        command.execute();
        assertEquals(List.of(5f), osc.values);
    }
}
