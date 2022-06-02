package com.getpcpanel.obs.remote.objects;

import java.util.List;

import lombok.Data;

@Data
public class Scene {
    private String name;
    private List<Source> sources;
}
