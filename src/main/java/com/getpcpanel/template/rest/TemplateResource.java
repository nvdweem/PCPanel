package com.getpcpanel.template.rest;

import javax.annotation.Nullable;

import org.apache.commons.lang3.StringUtils;

import com.getpcpanel.commands.Commands;
import com.getpcpanel.commands.DialValue;
import com.getpcpanel.commands.curve.CurveService;
import com.getpcpanel.device.DeviceHolder;
import com.getpcpanel.profile.SaveService;
import com.getpcpanel.template.TemplateCatalog;
import com.getpcpanel.template.TemplateFunctions;
import com.getpcpanel.template.TemplateScope;
import com.getpcpanel.template.TemplateService;
import com.getpcpanel.template.rest.dto.TemplateCatalogDto;
import com.getpcpanel.template.rest.dto.TemplatePreviewDto;
import com.getpcpanel.template.rest.dto.TemplatePreviewRequestDto;
import com.getpcpanel.util.ValueInterpolator;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

/** The template editor's variable completion and live preview. */
@Path("/api/templates")
@ApplicationScoped
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class TemplateResource {
    @Inject TemplateService templates;
    @Inject TemplateCatalog catalog;
    @Inject SaveService saveService;
    @Inject DeviceHolder devices;
    @Inject CurveService curves;

    @GET
    @Path("/catalog")
    public TemplateCatalogDto catalog(@QueryParam("path") @Nullable String path, @QueryParam("serial") @Nullable String serial,
                                      @QueryParam("control") int control, @QueryParam("slot") @Nullable String slot) {
        var normalized = StringUtils.defaultString(path);
        return new TemplateCatalogDto(normalized, catalog.children(normalized, scope(serial, control, slot, null, null, null)));
    }

    @POST
    @Path("/preview")
    public TemplatePreviewDto preview(TemplatePreviewRequestDto request) {
        var scope = scope(request.serial(), request.control(), request.slot(), request.min(), request.max(), request.formula());
        var result = templates.preview(StringUtils.defaultString(request.source()), scope);
        return new TemplatePreviewDto(result.output(), result.literalTags(), result.error());
    }

    /** The scope a template of this control's {@code slot} renders in, from the control's saved actions and current position. */
    private TemplateScope scope(@Nullable String serial, int control, @Nullable String slot, @Nullable Double min, @Nullable Double max, @Nullable String formula) {
        if (StringUtils.isBlank(serial)) {
            return TemplateScope.EMPTY;
        }
        var kind = StringUtils.defaultIfBlank(slot, "rotate");
        var button = kind.equals("press") || kind.equals("dblpress") || kind.equals("release");
        var profile = saveService.getProfile(serial).orElse(null);
        Commands commands = profile == null ? null : switch (kind) {
            case "press" -> profile.getButtonData(control);
            case "dblpress" -> profile.getDblButtonData(control);
            case "release" -> profile.getReleaseButtonData(control);
            default -> profile.getDialData(control);
        };
        DialValue dial = null;
        if (!button && profile != null) {
            var setting = profile.getKnobSettings(control);
            var raw = devices.getDevice(serial).map(d -> d.getKnobRotation(control)).orElse(0);
            dial = new DialValue(setting, curves.forControl(setting), raw);
        }
        Object value;
        if (kind.equals("overlay")) {
            value = dial == null ? null : Math.round(dial.getValue(null, 0f, 100f));
        } else {
            var position = dial == null ? 1d : dial.getValue(null, 0f, 1f);
            value = TemplateFunctions.normalize(ValueInterpolator.translate(position, min, max, formula));
        }
        return new TemplateScope(serial, control, button, commands, dial, value, null);
    }
}
