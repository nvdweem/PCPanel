package com.getpcpanel;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;

/**
 * Jackson wrapper
 */
@Log4j2
public final class Json {
    private static final ObjectMapper mapper = buildObjectMapper();

    private Json() {
    }

    private static ObjectMapper buildObjectMapper() {
        return new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    @SneakyThrows
    public static String write(Object o) {
        return mapper.writeValueAsString(o);
    }

    @SneakyThrows
    public static <T> T read(String in, Class<T> clazz) {
        log.error("Reading {}: {}", clazz, in);

        return mapper.readValue(in, clazz);
    }

    @SneakyThrows
    public static String writePretty(Object o) {
        return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(o);
    }
}
