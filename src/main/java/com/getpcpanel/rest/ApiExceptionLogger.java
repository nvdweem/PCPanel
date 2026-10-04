package com.getpcpanel.rest;

import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Response;
import lombok.extern.log4j.Log4j2;

/**
 * Gives every {@link WebApplicationException} a line in the application log. A resource that rejects a
 * request answers the browser with a status code and a message, but the message reaches only the
 * caller — so a user's log shows nothing at all for the request that failed, and a bug report built
 * from it cannot say why. The status code alone does not identify the cause either: the same 404 is
 * produced both by a resource reporting that a named thing does not exist and by a request whose path
 * matched no resource at all.
 *
 * <p>It logs the reason the resource gave ({@code Profile not found: work}); a path that matches no resource
 * logs a bare {@code HTTP 404 Not Found}. Installing a mapper also means the REST layer answers such a path
 * itself instead of passing it on, which is why app deep links are routed before it (see
 * {@link StaticCacheControl}).
 *
 * <p>The response is the exception's own, returned untouched, so the API behaves exactly as it does
 * without this mapper.
 */
@Log4j2
@ApplicationScoped
public class ApiExceptionLogger {
    @ServerExceptionMapper
    public Response logAndPassThrough(WebApplicationException e, ContainerRequestContext request) {
        var response = e.getResponse();
        var status = response == null ? -1 : response.getStatus();
        log.warn("{} {} -> {}: {}", request.getMethod(), request.getUriInfo().getPath(), status, e.getMessage());
        return response;
    }
}
