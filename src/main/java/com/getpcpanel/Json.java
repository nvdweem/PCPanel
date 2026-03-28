package com.getpcpanel;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;

/**
 * Jackson wrapper
 */
@Log4j2
@ApplicationScoped
@RequiredArgsConstructor
public final class Json {
    private final ObjectMapper mapper;

    @SneakyThrows
    public String write(Object o) {
        return mapper.writeValueAsString(o);
    }

    @SneakyThrows
    public <T> T read(String in, Class<T> clazz) {
        return mapper.readValue(in, clazz);
    }

    @SneakyThrows
    public String writePretty(Object o) {
        return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(o);
    }
}
