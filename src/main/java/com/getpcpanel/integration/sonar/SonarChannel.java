package com.getpcpanel.integration.sonar;

/** A Sonar channel, named as Sonar's UI names it and mapped to the id its API uses. */
public enum SonarChannel {
    Game("game"),
    Chat("chatRender"),
    Mic("chatCapture"),
    Media("media"),
    Aux("aux"),
    Master("master");

    private final String apiId;

    SonarChannel(String apiId) {
        this.apiId = apiId;
    }

    public String apiId() {
        return apiId;
    }
}
