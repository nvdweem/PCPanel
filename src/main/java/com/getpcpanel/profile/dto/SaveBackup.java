package com.getpcpanel.profile.dto;

/** One rolling snapshot of the save file: its file name, when it was taken (epoch ms) and its size in bytes. */
public record SaveBackup(String name, long timestamp, long size) {
}
