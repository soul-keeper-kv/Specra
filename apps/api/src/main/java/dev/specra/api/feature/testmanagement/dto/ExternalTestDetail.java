package dev.specra.api.feature.testmanagement.dto;

import java.util.List;

/** The external case material needed by the automation workspace and the modelling pipeline. */
public record ExternalTestDetail(
    String externalId,
    String title,
    String description,
    String priority,
    List<String> labels,
    String url,
    List<ExternalTestStep> steps) {}
