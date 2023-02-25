package com.getpcpanel.ui;

import static java.util.Objects.requireNonNull;

import java.net.URL;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.stereotype.Service;

import com.getpcpanel.commands.command.Command;
import com.getpcpanel.spring.OsHelper;
import com.getpcpanel.ui.command.Cmd;
import com.getpcpanel.ui.command.Cmd.Type;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;

@Log4j2
@Service
@RequiredArgsConstructor
public class MacroControllerService {
    private static final Map<Type, List<ControllerInfo>> typeToControllers = new EnumMap<>(Type.class);
    private static final Map<Class<? extends Command>, ControllerInfo> commandToController = new HashMap<>();
    private final OsHelper osHelper;

    @SneakyThrows
    @PostConstruct
    public void init() {
        var provider = new ClassPathScanningCandidateComponentProvider(false);
        provider.addIncludeFilter(new AnnotationTypeFilter(Cmd.class));

        var beanDefs = provider.findCandidateComponents(getClass().getPackageName());
        for (var bd : beanDefs) {
            var controllerClass = Class.forName(bd.getBeanClassName());
            var cmd = controllerClass.getAnnotation(Cmd.class);

            if (!osHelper.isOs(cmd.os())) {
                continue;
            }

            var info = new ControllerInfo(controllerClass, cmd);
            typeToControllers.computeIfAbsent(cmd.type(), t -> new ArrayList<>()).add(info);
            for (var command : cmd.cmds()) {
                commandToController.put(command, info);
            }
        }

        for (var type : Type.values()) {
            typeToControllers.computeIfAbsent(type, t -> new ArrayList<>()).sort(Comparator.comparing(a -> a.cmd().name()));
        }
    }

    public ControllerInfo getControllerForCommand(Class<? extends Command> cmd) {
        return commandToController.get(cmd);
    }

    public List<ControllerInfo> getControllersForType(Type type) {
        return typeToControllers.get(type);
    }

    public record ControllerInfo(Class<?> controllerClass, Cmd cmd) {
        public URL getFxml() {
            return requireNonNull(getClass().getResource("/assets/command/%s/%s.fxml".formatted(cmd.type().name(), cmd.fxml())));
        }
    }
}
