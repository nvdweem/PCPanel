package com.getpcpanel.report;

import java.io.File;
import java.io.IOException;

import org.apache.commons.lang3.StringUtils;

import com.getpcpanel.report.dto.BugReportRequest;
import com.getpcpanel.report.dto.BugReportResponse;
import com.getpcpanel.util.app.OpenFolderEvent;
import com.getpcpanel.util.io.FileUtil;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/** Writes a diagnostics bundle for a bug report, and opens the folder it was written to. */
@Path("/api/report")
@ApplicationScoped
@Produces(MediaType.APPLICATION_JSON)
public class BugReportResource {
    @Inject BugReportService service;
    @Inject FileUtil fileUtil;
    @Inject Event<Object> eventBus;

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public BugReportResponse create(BugReportRequest request) throws IOException {
        if (request == null || StringUtils.isBlank(request.summary())) {
            throw new BadRequestException("A description of the problem is required");
        }
        return service.create(request);
    }

    @POST
    @Path("/open")
    public Response openReportsFolder() {
        eventBus.fire(new OpenFolderEvent(new File(fileUtil.getRoot(), BugReportService.REPORTS_DIR).toString()));
        return Response.ok().build();
    }
}
