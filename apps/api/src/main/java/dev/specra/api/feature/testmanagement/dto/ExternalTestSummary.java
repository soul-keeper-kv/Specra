package dev.specra.api.feature.testmanagement.dto;

import java.util.List;

/** The smallest provider-neutral input Specra needs before modelling an external manual test. */
public record ExternalTestSummary(
    String externalId,
    String title,
    String status,
    String priority,
    List<String> labels,
    String url) {}
