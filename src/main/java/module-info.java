module PCPanel {
    requires static lombok;
    requires Java.WebSocket;
    requires com.google.gson;
    requires com.sun.jna.platform;
    requires com.sun.jna;
    requires hid4java;
    requires java.desktop;
    requires java.management;
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.swing;
    requires javafx.web;
    requires one.util.streamex;
    requires org.apache.commons.io;
    requires org.apache.commons.lang3;
    requires org.apache.logging.log4j;

    exports com.getpcpanel;
    opens com.getpcpanel to javafx.fxml;
    opens com.getpcpanel.device to javafx.fxml;
    opens com.getpcpanel.ui to javafx.fxml;

    opens com.getpcpanel.profile to com.google.gson;
    opens com.getpcpanel.obs.remote.communication.response to com.google.gson;
}
