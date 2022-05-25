package com.getpcpanel;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * GSon wrapper
 */
public final class Json {
    private static final GsonBuilder builder = new GsonBuilder();
    private static final Gson g = builder.create();
    private static final Gson pp = builder.setPrettyPrinting().create();

    private Json() {
    }

    public static String write(Object o) {
        return g.toJson(o);
    }

    public static <T> T read(String in, Class<T> clazz) {
        return g.fromJson(in, clazz);
    }

    public static String writePretty(Object o) {
        return pp.toJson(o);
    }
}
