package com.getpcpanel.obs.remote.objects;

import lombok.Data;

@Data
public class Source {
    private String name;
    private double cx;
    private double cy;
    private boolean render;
    private int source_cx;
    private int source_cy;
    private String type;
    private double volume;
    private double x;
    private double y;
    private String typeId;
}
