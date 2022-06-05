package com.getpcpanel;

import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import com.getpcpanel.ui.HomePage;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;

public class MainFX extends Application {
    @SuppressWarnings("StaticNonFinalField") private static ConfigurableApplicationContext context;

    @Override
    @SuppressWarnings("AssignmentToStaticFieldFromInstanceMethod")
    public void init() throws Exception {
        context = new SpringApplicationBuilder(Main.class)
                .headless(false)
                .run(getParameters().getRaw().toArray(new String[0]));
    }

    @Override
    public void start(Stage primaryStage) throws Exception {
        context.getBean(HomePage.class).start(primaryStage, getParameters().getRaw().contains("quiet"));
    }

    @Override
    public void stop() throws Exception {
        context.close();
        Platform.exit();
    }

    public static <T> T getBean(Class<T> clazz) {
        return context.getBean(clazz);
    }
}
