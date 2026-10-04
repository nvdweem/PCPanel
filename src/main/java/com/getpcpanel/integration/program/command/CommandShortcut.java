package com.getpcpanel.integration.program.command;

import java.util.Locale;

import javax.annotation.Nullable;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.getpcpanel.commands.command.ButtonAction;
import com.getpcpanel.commands.command.Command;
import com.getpcpanel.commands.meta.CommandCategory;
import com.getpcpanel.commands.meta.CommandKind;
import com.getpcpanel.commands.meta.CommandMeta;
import com.getpcpanel.integration.program.IPlatformCommand;
import com.getpcpanel.integration.program.WindowFocuser;
import com.getpcpanel.util.CdiHelper;

import lombok.Getter;
import lombok.ToString;
import lombok.extern.log4j.Log4j2;

/**
 * Button action that opens a program, shortcut, folder, document or website. With {@link #focusIfRunning} a running
 * app is brought to the front instead of being started again, and with {@link #minimizeIfFocused} an app already in
 * front is minimised.
 */
@Getter
@Log4j2
@ToString(callSuper = true)
@JsonTypeName("program.shortcut")
@CommandMeta(label = "Open app, file or website", category = CommandCategory.system, kinds = {CommandKind.button}, icon = "zap", legacyIds = {"com.getpcpanel.commands.command.CommandShortcut"})
public class CommandShortcut extends Command implements ButtonAction {
    /** How a Microsoft Store app is opened: by its app id. */
    private static final String STORE_APP = "shell:AppsFolder\\";
    private final String shortcut;
    /** The executable whose window is brought to the front; blank uses {@link #defaultFocusApp} of the path. */
    @Nullable private final String focusApp;
    private final boolean focusIfRunning;
    private final boolean minimizeIfFocused;

    @JsonCreator
    public CommandShortcut(@JsonProperty("shortcut") String shortcut, @JsonProperty("focusApp") @Nullable String focusApp,
            @JsonProperty("focusIfRunning") boolean focusIfRunning, @JsonProperty("minimizeIfFocused") boolean minimizeIfFocused) {
        this.shortcut = shortcut;
        this.focusApp = StringUtils.trimToNull(focusApp);
        this.focusIfRunning = focusIfRunning;
        this.minimizeIfFocused = minimizeIfFocused;
    }

    /** The file name of {@code shortcut} when it is an {@code .exe}, else null. The UI applies the same rule. */
    public static @Nullable String defaultFocusApp(@Nullable String shortcut) {
        var name = FilenameUtils.getName(StringUtils.strip(shortcut));
        return StringUtils.endsWithIgnoreCase(name, ".exe") ? name : null;
    }

    @Override
    public void execute() {
        execute(CdiHelper.getBean(WindowFocuser.class), CdiHelper.getBean(IPlatformCommand.class));
    }

    void execute(WindowFocuser focuser, IPlatformCommand platform) {
        if (focusIfRunning) {
            var app = focusApp == null ? defaultFocusApp(shortcut) : focusApp;
            if (app != null && focuser.focusOrMinimize(app, minimizeIfFocused) != WindowFocuser.Result.NOT_RUNNING) {
                return;
            }
        }
        platform.openOrRun(shortcut);
    }

    /** An installed app's shortcut, desktop entry or bundle shows as its name; anything else as typed. */
    @Override
    public String buildLabel() {
        var target = StringUtils.strip(shortcut);
        if (StringUtils.startsWithIgnoreCase(target, STORE_APP)) {
            // shell:AppsFolder\Microsoft.WindowsTerminal_8wekyb3d8bbwe!App: the package name's last part.
            return StringUtils.substringAfterLast("." + StringUtils.substringBefore(target.substring(STORE_APP.length()), "_"), ".");
        }
        if (target != null && !target.contains("://") && StringUtils.endsWithAny(target.toLowerCase(Locale.ROOT), ".lnk", ".desktop", ".app")) {
            return FilenameUtils.getBaseName(target);
        }
        return shortcut;
    }
}
