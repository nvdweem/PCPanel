package com.getpcpanel.util.tray.wayland;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.freedesktop.dbus.types.UInt32;
import org.freedesktop.dbus.types.Variant;

import com.getpcpanel.util.app.AppEvents;
import com.getpcpanel.util.CdiHelper;
import com.getpcpanel.util.io.FileUtil;
import com.getpcpanel.util.app.OpenFolderEvent;
import com.getpcpanel.util.app.ShowMainEvent;

import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.extern.log4j.Log4j2;

/**
 * The tray context menu shown on right-click. Mirrors the Windows tray: <b>Open PCPanel</b> (opens the
 * UI), <b>Open settings folder</b> (reveals the data dir holding {@code profiles.json}, with the
 * {@code logs/} subdir inside it) and <b>Quit</b> (shuts the app down, like the in-UI Quit button).
 * Exported at {@code /MenuBar}, which the StatusNotifierItem advertises via its {@code Menu} property.
 */
@Log4j2
@RegisterForReflection
public class DBusMenuImpl implements DBusMenu {
    private static final int ID_OPEN = 1;
    private static final int ID_SETTINGS = 2;
    private static final int ID_QUIT = 3;
    private static final int[] ITEM_IDS = {ID_OPEN, ID_SETTINGS, ID_QUIT};

    @Override
    public String getObjectPath() {
        return "/MenuBar";
    }

    @Override
    public MenuLayoutReturn<UInt32, MenuItemLayout> GetLayout(int parentId, int recursionDepth, List<String> propertyNames) {
        var children = new ArrayList<Variant<?>>(ITEM_IDS.length);
        for (var id : ITEM_IDS) {
            children.add(new Variant<>(new MenuItemLayout(id, labelProps(label(id)), List.of())));
        }
        var root = new MenuItemLayout(0, Map.of("children-display", new Variant<>("submenu")), children);
        return new MenuLayoutReturn<>(new UInt32(1), root);
    }

    @Override
    public List<MenuItemProperties> GetGroupProperties(List<Integer> ids, List<String> propertyNames) {
        var result = new ArrayList<MenuItemProperties>(ITEM_IDS.length);
        for (var id : ITEM_IDS) {
            result.add(new MenuItemProperties(id, labelProps(label(id))));
        }
        return result;
    }

    @Override
    public Variant<?> GetProperty(int id, String name) {
        return "label".equals(name) ? new Variant<>(label(id)) : new Variant<>(true);
    }

    @Override
    public void Event(int id, String eventId, Variant<?> data, UInt32 timestamp) {
        if (!"clicked".equals(eventId)) {
            return;
        }
        switch (id) {
            case ID_QUIT -> {
                log.info("Quit selected from the tray menu; shutting down");
                Quarkus.asyncExit(0);
            }
            case ID_SETTINGS -> {
                log.debug("Open settings folder selected from the tray menu");
                AppEvents.fire(new OpenFolderEvent(settingsRoot().getRoot().toString()));
            }
            default -> {
                log.debug("Open selected from the tray menu");
                AppEvents.fire(new ShowMainEvent());
            }
        }
    }

    @Override
    public boolean AboutToShow(int id) {
        return false;
    }

    /**
     * The live data directory ({@code profiles.json} + {@code logs/}). Resolved through {@link FileUtil}
     * (the {@code pcpanel.root} config the rest of the app reads), not {@code PcPanelRoot.resolve()} - in
     * dev mode the configured root ({@code ~/.pcpaneldev}) differs from the bare XDG/legacy resolution, so
     * the latter would open a folder that does not hold the running app's settings.
     */
    private static FileUtil settingsRoot() {
        return CdiHelper.getBean(FileUtil.class);
    }

    private static String label(int id) {
        return switch (id) {
            case ID_SETTINGS -> "Open settings folder";
            case ID_QUIT -> "Quit";
            default -> "Open PCPanel";
        };
    }

    private static Map<String, Variant<?>> labelProps(String label) {
        var props = new LinkedHashMap<String, Variant<?>>();
        props.put("label", new Variant<>(label));
        props.put("enabled", new Variant<>(true));
        props.put("visible", new Variant<>(true));
        return props;
    }
}
