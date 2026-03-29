package com.getpcpanel;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

import com.getpcpanel.ui.HomePageController;

import io.quarkiverse.fx.FxPostStartupEvent;
import io.quarkiverse.fx.views.FxViewRepository;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.stage.Stage;
import lombok.extern.log4j.Log4j2;

@Singleton
@Log4j2
public class MainFX {
    private static final Map<Class<?>, CacheObject> beanCache = new ConcurrentHashMap<>();
    @Inject FXMLLoader fxmlLoader;
    @Inject FxViewRepository viewRepository;

    void onPostStartup(@Observes FxPostStartupEvent event) throws Exception {
        var stage = event.getPrimaryStage();
        var view = viewRepository.getViewData("HomePage");
        view.<HomePageController>getController().start(stage, view.getRootNode());

        // Based on quiet parameter
        stage.show();
    }

    // @Override
    @SuppressWarnings("AssignmentToStaticFieldFromInstanceMethod")
    public void init() throws Exception {
        //     context = new SpringApplicationBuilder(Main.class)
        //             .headless(false)
        //             .run(getParameters().getRaw().toArray(new String[0]));
    }

    // @Override
    public void start(Stage primaryStage) throws Exception {
        System.out.println("Test");
        // log.info("Starting v{}", context.getEnvironment().getProperty("application.version"));
        // context.getBean(HomePage.class).start(primaryStage, getParameters().getRaw().contains("quiet"));
    }

    // @Override
    public void stop() throws Exception {
        //     context.close();
        Platform.exit();
    }

    @SuppressWarnings("unchecked")
    public static <T> Optional<T> getOptionalBean(Class<T> clazz) {
        return Optional.ofNullable((T) beanCache.computeIfAbsent(clazz, cls -> {
            try {
                // return new CacheObject(context.getBean(clazz));
                return null;
            } catch (Exception e) {
                return CacheObject.NULL;
            }
        }).o);
    }

    public static <T> T getBean(Class<T> clazz) {
        // return context.getBean(clazz);
        return null;
    }

    record CacheObject(@Nullable Object o) {
        private static final CacheObject NULL = new CacheObject(null);
    }
}
