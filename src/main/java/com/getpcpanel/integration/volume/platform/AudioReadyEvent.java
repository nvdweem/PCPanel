package com.getpcpanel.integration.volume.platform;

/**
 * The audio backend has listed the devices and sessions present when it started, so a level set from now on reaches
 * them. Fired once.
 */
public record AudioReadyEvent() {
}
