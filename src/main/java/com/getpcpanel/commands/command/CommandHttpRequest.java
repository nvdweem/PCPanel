package com.getpcpanel.commands.command;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nullable;

import org.apache.commons.lang3.StringUtils;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.getpcpanel.util.CdiHelper;
import com.getpcpanel.util.Debouncer;
import com.getpcpanel.util.SharedHttpClient;
import com.getpcpanel.util.ValueInterpolator;

import lombok.Getter;
import lombok.ToString;
import lombok.extern.log4j.Log4j2;

/**
 * Fires an arbitrary HTTP request. The {@code url}, every header value, and the {@code body} may
 * contain <code>{{ value }}</code>, replaced with the dial-mapped value (or the configured max on a
 * button press). Headers are one {@code Name: Value} per line. The request is sent asynchronously so
 * it never blocks the command thread; a dial stream is leading-edge throttled so a quick sweep does
 * not flood the endpoint.
 */
@Getter
@Log4j2
@ToString(callSuper = true)
public class CommandHttpRequest extends CommandValueOutput {
    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    /** Min spacing between requests while a dial is being swept (leading edge fires instantly). */
    private static final long DIAL_THROTTLE_MS = 150;

    private final String url;
    private final String method;
    private final String headers;
    private final String body;

    @JsonCreator
    public CommandHttpRequest(
            @JsonProperty("url") String url,
            @JsonProperty("method") String method,
            @JsonProperty("headers") @Nullable String headers,
            @JsonProperty("body") @Nullable String body,
            @JsonProperty("min") @Nullable Double min,
            @JsonProperty("max") @Nullable Double max,
            @JsonProperty("formula") @Nullable String formula,
            @JsonProperty("dialParams") @Nullable DialCommandParams dialParams) {
        super(min, max, formula, dialParams);
        this.url = url;
        this.method = method;
        this.headers = headers;
        this.body = body;
    }

    @Override
    protected void send(double value, boolean immediate) {
        if (StringUtils.isBlank(url)) {
            return;
        }
        if (immediate) {
            fire(value);
        } else {
            CdiHelper.getBean(Debouncer.class).throttleLeading(this, () -> fire(value), DIAL_THROTTLE_MS, TimeUnit.MILLISECONDS);
        }
    }

    private void fire(double value) {
        HttpRequest request;
        try {
            request = buildRequest(value);
        } catch (RuntimeException e) {
            log.warn("HTTP command: invalid request ({} {}): {}", method, url, e.getMessage());
            return;
        }
        SharedHttpClient.get().sendAsync(request, BodyHandlers.discarding())
                .whenComplete((resp, ex) -> {
                    if (ex != null) {
                        log.warn("HTTP command: {} {} failed: {}", method, url, ex.getMessage());
                    }
                });
    }

    private HttpRequest buildRequest(double value) {
        var resolvedUrl = ValueInterpolator.interpolate(url, value);
        var resolvedBody = ValueInterpolator.interpolate(StringUtils.defaultString(body), value);
        var verb = StringUtils.isBlank(method) ? "GET" : method.trim().toUpperCase();
        var bodyPublisher = StringUtils.isEmpty(resolvedBody) ? BodyPublishers.noBody() : BodyPublishers.ofString(resolvedBody);
        var builder = HttpRequest.newBuilder(URI.create(resolvedUrl)).timeout(TIMEOUT).method(verb, bodyPublisher);
        for (var line : StringUtils.defaultString(headers).split("\\R")) {
            var idx = line.indexOf(':');
            if (idx <= 0) {
                continue;
            }
            var name = line.substring(0, idx).trim();
            var headerValue = ValueInterpolator.interpolate(line.substring(idx + 1).trim(), value);
            try {
                builder.header(name, headerValue);
            } catch (IllegalArgumentException e) {
                // java.net.http rejects restricted headers (Host, Connection, …); skip rather than drop the request.
                log.debug("HTTP command: ignoring header {}: {}", name, e.getMessage());
            }
        }
        return builder.build();
    }

    @Override
    public String buildLabel() {
        return "HTTP " + (StringUtils.isBlank(method) ? "GET" : method.trim().toUpperCase()) + ": " + StringUtils.defaultString(url);
    }
}
