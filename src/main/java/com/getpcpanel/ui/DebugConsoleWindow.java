package com.getpcpanel.ui;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.TextArea;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;

public class DebugConsoleWindow {
    private static final int MAX_TEXT_CHARS = 200_000;
    private static final int INITIAL_READ_BYTES = 20_000;
    private static final long POLL_INTERVAL_MS = 500;

    private final File logFile;
    private Stage stage;
    private TextArea textArea;
    private ScheduledExecutorService executor;
    private long lastPosition;
    private boolean missingNotified;

    public DebugConsoleWindow(File logFile) {
        this.logFile = logFile;
    }

    public void show(Stage owner) {
        if (stage != null) {
            stage.show();
            stage.toFront();
            return;
        }

        textArea = new TextArea();
        textArea.setEditable(false);
        textArea.setWrapText(false);

        var root = new BorderPane(textArea);
        var scene = new Scene(root, 900, 600);
        scene.getStylesheets().add(getClass().getResource("/assets/dark_theme.css").toExternalForm());
        scene.getStylesheets().add(getClass().getResource("/assets/1.css").toExternalForm());

        stage = new Stage();
        stage.setTitle("Debug Console");
        stage.initOwner(owner);
        stage.setScene(scene);
        stage.setOnHidden(e -> {
            stopTail();
            stage = null;
            textArea = null;
        });
        stage.show();

        loadInitial();
        startTail();
    }

    public void close() {
        stopTail();
        if (stage != null) {
            stage.close();
            stage = null;
            textArea = null;
        }
    }

    public boolean isShowing() {
        return stage != null && stage.isShowing();
    }

    private void loadInitial() {
        if (!logFile.exists()) {
            appendText("Log file not found: " + logFile.getAbsolutePath() + System.lineSeparator());
            missingNotified = true;
            return;
        }
        try (var raf = new RandomAccessFile(logFile, "r")) {
            var length = raf.length();
            lastPosition = Math.max(0, length - INITIAL_READ_BYTES);
            raf.seek(lastPosition);
            readAndAppend(raf);
        } catch (IOException e) {
            appendText("Failed to read log file: " + e.getMessage() + System.lineSeparator());
        }
    }

    private void startTail() {
        stopTail();
        executor = Executors.newSingleThreadScheduledExecutor();
        executor.scheduleAtFixedRate(this::poll, 0, POLL_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    private void stopTail() {
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
    }

    private void poll() {
        if (textArea == null) {
            return;
        }
        if (!logFile.exists()) {
            if (!missingNotified) {
                appendText("Log file not found: " + logFile.getAbsolutePath() + System.lineSeparator());
                missingNotified = true;
            }
            return;
        }
        missingNotified = false;

        try (var raf = new RandomAccessFile(logFile, "r")) {
            var length = raf.length();
            if (length < lastPosition) {
                lastPosition = 0;
            }
            if (length == lastPosition) {
                return;
            }
            raf.seek(lastPosition);
            readAndAppend(raf);
        } catch (IOException e) {
            appendText("Failed to read log file: " + e.getMessage() + System.lineSeparator());
        }
    }

    private void readAndAppend(RandomAccessFile raf) throws IOException {
        var buffer = new byte[8192];
        int read;
        var sb = new StringBuilder();
        while ((read = raf.read(buffer)) != -1) {
            sb.append(new String(buffer, 0, read, StandardCharsets.UTF_8));
        }
        lastPosition = raf.getFilePointer();
        if (sb.length() == 0) {
            return;
        }
        appendText(sb.toString());
    }

    private void appendText(String text) {
        Platform.runLater(() -> {
            if (textArea == null) {
                return;
            }
            textArea.appendText(text);
            if (textArea.getLength() > MAX_TEXT_CHARS) {
                textArea.deleteText(0, textArea.getLength() - MAX_TEXT_CHARS);
            }
        });
    }
}
