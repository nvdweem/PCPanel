package com.getpcpanel;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import com.getpcpanel.ui.HomePage;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;

@Log4j2
public class MainFX extends Application {
    @Getter @SuppressWarnings("StaticNonFinalField") private static ConfigurableApplicationContext context;
    private static final Map<Class<?>, CacheObject> beanCache = new ConcurrentHashMap<>();

    @Override
    @SuppressWarnings("AssignmentToStaticFieldFromInstanceMethod")
    public void init() throws Exception {
        context = new SpringApplicationBuilder(Main.class)
                .headless(false)
                .run(getParameters().getRaw().toArray(new String[0]));
    }

    @Override
    public void start(Stage primaryStage) throws Exception {
        log.info("Starting v{}", context.getEnvironment().getProperty("application.version"));
        context.getBean(HomePage.class).start(primaryStage, getParameters().getRaw().contains("quiet"));
    }

    @Override
    public void stop() throws Exception {
        context.close();
        Platform.exit();
    }

    @SuppressWarnings("unchecked")
    public static <T> Optional<T> getOptionalBean(Class<T> clazz) {
        return Optional.ofNullable((T) beanCache.computeIfAbsent(clazz, cls -> {
            try {
                return new CacheObject(context.getBean(clazz));
            } catch (Exception e) {
                return CacheObject.NULL;
            }
        }).o);
    }

    public static <T> T getBean(Class<T> clazz) {
        return context.getBean(clazz);
    }

    record CacheObject(@Nullable Object o) {
        private static final CacheObject NULL = new CacheObject(null);
    }
}
