package org.anax.framework.integrations.pojo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Builder;
import lombok.Data;

@JsonIgnoreProperties(ignoreUnknown = true)
@Data
@Builder
public class CycleInfo{
    String id;
    String name;
    String build;
    String environment;
    String description;
    String startDate;
    String endDate;
    String projectId;
    String versionId;
}