package com.getpcpanel.obs.remote.objects;

import lombok.Data;

@Data
public class SourceType {
    private String typeId;
    private String displayName;
    private String type;
    private Capabilities caps;
}
