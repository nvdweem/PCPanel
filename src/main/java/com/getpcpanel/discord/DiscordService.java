package com.getpcpanel.discord;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.getpcpanel.commands.KeyMacro;
import com.getpcpanel.profile.SaveService;

import lombok.extern.log4j.Log4j2;

@Log4j2
@Service
public class DiscordService {
    private final SaveService saveService;
    private final ObjectMapper mapper;
    private final DiscordIpcClient client;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private volatile Boolean lastMuteState;
    private volatile Boolean lastDeafenState;
    private volatile String lastInputModeType;

    public DiscordService(SaveService saveService, ObjectMapper mapper) {
        this.saveService = saveService;
        this.mapper = mapper;
        this.client = new DiscordIpcClient(mapper);
        startAutoReconnect();
    }

    public boolean isEnabled() {
        return saveService.get().isDiscordEnabled();
    }

    public void setMute(Boolean mute) {
        if (mute == null) {
            return;
        }
        var sent = sendVoiceSettings(buildVoiceSettings("mute", mute));
        if (!sent) {
            sendHotkey(saveService.get().getDiscordMuteHotkey());
        }
    }

    public void toggleMute() {
        var settings = getVoiceSettings();
        if (settings != null) {
            setMute(!settings.mute);
            return;
        }
        if (sendHotkey(saveService.get().getDiscordMuteHotkey())) {
            lastMuteState = lastMuteState == null ? true : !lastMuteState;
            return;
        }
        toggleMuteFallback();
    }

    public void setDeafen(Boolean deafen) {
        if (deafen == null) {
            return;
        }
        var sent = sendVoiceSettings(buildVoiceSettings("deaf", deafen));
        if (!sent) {
            sendHotkey(saveService.get().getDiscordDeafenHotkey());
        }
    }

    public void toggleDeafen() {
        var settings = getVoiceSettings();
        if (settings != null) {
            setDeafen(!settings.deaf);
            return;
        }
        if (sendHotkey(saveService.get().getDiscordDeafenHotkey())) {
            lastDeafenState = lastDeafenState == null ? true : !lastDeafenState;
            return;
        }
        toggleDeafenFallback();
    }

    public void togglePttMode() {
        var settings = getVoiceSettings();
        var currentType = settings != null ? settings.inputModeType : lastInputModeType;
        var newType = "VOICE_ACTIVITY".equalsIgnoreCase(currentType) ? "PUSH_TO_TALK" : "VOICE_ACTIVITY";
        List<String> shortcut = settings != null ? settings.shortcut : null;
        var args = mapper.createObjectNode();
        var inputMode = mapper.createObjectNode();
        inputMode.put("type", newType);
        if (shortcut != null) {
            inputMode.set("shortcut", mapper.valueToTree(shortcut));
        } else {
            inputMode.set("shortcut", mapper.createArrayNode());
        }
        args.set("input_mode", inputMode);
        var sent = sendVoiceSettings(args);
        if (sent) {
            lastInputModeType = newType;
        } else {
            sendHotkey(saveService.get().getDiscordPttHotkey());
        }
    }

    private boolean sendVoiceSettings(ObjectNode args) {
        if (!isEnabled()) {
            return false;
        }
        return sendNoResponseWithRetry("SET_VOICE_SETTINGS", args);
    }

    private VoiceSettings getVoiceSettings() {
        if (!isEnabled()) {
            return null;
        }
        var response = sendExpectResponseWithRetry("GET_VOICE_SETTINGS", mapper.createObjectNode());
        return parseVoiceSettings(response);
    }

    public String testConnection(String ipcPathOverride, String clientIdOverride) {
        var tester = new DiscordIpcClient(mapper);
        var connected = tester.connect(ipcPathOverride, clientIdOverride);
        if (!connected) {
            tester.close();
            return "Failed (IPC not reachable)";
        }
        try {
            var response = tester.send("GET_VOICE_SETTINGS", mapper.createObjectNode(), true);
            tester.close();
            return response != null && response.has("data") ? "Success" : "Success (IPC connected, voice settings unavailable)";
        } catch (IOException e) {
            tester.close();
            return "Success (IPC connected, voice settings unavailable)";
        }
    }

    private ObjectNode buildVoiceSettings(String key, boolean value) {
        var args = mapper.createObjectNode();
        args.put(key, value);
        return args;
    }

    private VoiceSettings parseVoiceSettings(JsonNode response) {
        if (response == null) {
            return null;
        }
        var data = response.path("data");
        var mute = data.path("mute").asBoolean(false);
        var deaf = data.path("deaf").asBoolean(false);
        var inputMode = data.path("input_mode");
        var inputModeType = inputMode.path("type").asText("");
        lastMuteState = mute;
        lastDeafenState = deaf;
        if (!inputModeType.isBlank()) {
            lastInputModeType = inputModeType;
        }
        List<String> shortcut = null;
        if (inputMode.has("shortcut") && inputMode.get("shortcut").isArray()) {
            shortcut = mapper.convertValue(inputMode.get("shortcut"), List.class);
        }
        return new VoiceSettings(mute, deaf, inputModeType, shortcut);
    }

    private void toggleMuteFallback() {
        if (lastMuteState == null) {
            setMute(true);
            lastMuteState = true;
            return;
        }
        setMute(!lastMuteState);
        lastMuteState = !lastMuteState;
    }

    private void toggleDeafenFallback() {
        if (lastDeafenState == null) {
            setDeafen(true);
            lastDeafenState = true;
            return;
        }
        setDeafen(!lastDeafenState);
        lastDeafenState = !lastDeafenState;
    }

    private void applyLocalStateFromArgs(ObjectNode args) {
        if (args.has("mute") && args.get("mute").isBoolean()) {
            lastMuteState = args.get("mute").asBoolean();
        }
        if (args.has("deaf") && args.get("deaf").isBoolean()) {
            lastDeafenState = args.get("deaf").asBoolean();
        }
        if (args.has("input_mode")) {
            var type = args.path("input_mode").path("type").asText("");
            if (!type.isBlank()) {
                lastInputModeType = type;
            }
        }
    }

    private boolean connect() {
        var save = saveService.get();
        return client.connect(save.getDiscordIpcPath(), save.getDiscordClientId());
    }

    private JsonNode sendExpectResponseWithRetry(String cmd, ObjectNode args) {
        try {
            if (!client.isConnected()) {
                if (!connect()) {
                    return null;
                }
            }
            return client.send(cmd, args, true);
        } catch (IOException e) {
            log.debug("Discord IPC {} failed: {}", cmd, e.getMessage());
            client.close();
            try {
                if (!connect()) {
                    return null;
                }
                return client.send(cmd, args, true);
            } catch (IOException retryError) {
                log.debug("Discord IPC {} retry failed: {}", cmd, retryError.getMessage());
                client.close();
                return null;
            }
        }
    }

    private boolean sendNoResponseWithRetry(String cmd, ObjectNode args) {
        try {
            if (!client.isConnected()) {
                if (!connect()) {
                    return false;
                }
            }
            client.send(cmd, args, false);
            applyLocalStateFromArgs(args);
            return true;
        } catch (IOException e) {
            log.debug("Discord IPC {} failed: {}", cmd, e.getMessage());
            client.close();
            try {
                if (!connect()) {
                    return false;
                }
                client.send(cmd, args, false);
                applyLocalStateFromArgs(args);
                return true;
            } catch (IOException retryError) {
                log.debug("Discord IPC {} retry failed: {}", cmd, retryError.getMessage());
                client.close();
                return false;
            }
        }
    }

    private boolean sendHotkey(String hotkey) {
        if (hotkey == null || hotkey.isBlank()) {
            return false;
        }
        KeyMacro.executeKeyStroke(hotkey.trim());
        return true;
    }

    private void startAutoReconnect() {
        scheduler.scheduleWithFixedDelay(() -> {
            try {
                if (isEnabled()) {
                    if (!client.isConnected()) {
                        connect();
                    }
                } else if (client.isConnected()) {
                    client.close();
                }
            } catch (Exception e) {
                log.debug("Discord IPC background connect failed: {}", e.getMessage());
            }
        }, 2, 5, TimeUnit.SECONDS);
    }

    private record VoiceSettings(boolean mute, boolean deaf, String inputModeType, List<String> shortcut) {
    }
}
