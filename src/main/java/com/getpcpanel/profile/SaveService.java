package com.getpcpanel.profile;

import java.io.IOException;
import java.nio.charset.Charset;

import javax.annotation.PostConstruct;

import org.apache.commons.io.FileUtils;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import com.getpcpanel.Json;
import com.getpcpanel.util.FileUtil;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import one.util.streamex.StreamEx;

@Log4j2
@Service
@RequiredArgsConstructor
public class SaveService {
    private static final String saveFileName = "profiles.json";
    private final ApplicationEventPublisher eventPublisher;
    private final FileUtil fileUtil;
    private Save save;

    public Save get() {
        return save;
    }

    @PostConstruct
    public void load() {
        var saveFile = fileUtil.getFile(saveFileName);
        if (!saveFile.exists()) {
            log.info("No save file found, creating new one");
            save = new Save();
            eventPublisher.publishEvent(new SaveEvent(save, true));
            return;
        }

        try {
            save = Json.read(FileUtils.readFileToString(saveFile, Charset.defaultCharset()), Save.class);
            StreamEx.ofValues(save.getDevices()).forEach(d -> StreamEx.of(d.getProfiles()).findFirst(Profile::isMainProfile).ifPresent(p -> d.setCurrentProfile(p.getName())));
            eventPublisher.publishEvent(new SaveEvent(save, false));
        } catch (Exception e) {
            log.error("Unable to read file", e);
            save = new Save();
        }
    }

    public void save() {
        var saveFile = fileUtil.getFile(saveFileName);
        for (var ds : save.getDevices().values()) {
            var p = ds.getCurrentProfile();
            p.setButtonData(ds.getButtonData());
            p.setDialData(ds.getDialData());
            p.setLightingConfig(ds.getLightingConfig());
            p.setKnobSettings(ds.getKnobSettings());
        }
        try {
            FileUtils.writeStringToFile(saveFile, Json.writePretty(save), Charset.defaultCharset());
        } catch (IOException e) {
            log.error("Unable to save file", e);
        }

        eventPublisher.publishEvent(new SaveEvent(save, false));
    }

    public record SaveEvent(Save save, boolean isNew) {
    }
}

