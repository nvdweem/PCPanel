package com.getpcpanel.alerts;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.Response;

/** Notification lights for the web UI. */
@Path("/api/alerts")
@ApplicationScoped
public class AlertResource {
    @Inject AlertService alerts;

    /** Notification senders seen (Windows handler ids, Linux app names), sorted, for the source picker. */
    @GET
    @Path("/notification-sources")
    public List<String> notificationSources() {
        return alerts.notificationSources().stream().sorted(String.CASE_INSENSITIVE_ORDER).toList();
    }

    /** Shows the saved notification light at {@code index} for a few seconds. */
    @POST
    @Path("/{index}/preview")
    public Response preview(@PathParam("index") int index) {
        return alerts.preview(index) ? Response.noContent().build() : Response.status(Response.Status.NOT_FOUND).build();
    }
}
