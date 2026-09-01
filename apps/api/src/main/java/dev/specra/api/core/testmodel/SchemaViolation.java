package dev.specra.api.core.testmodel;

/**
 * One reason a document is not a valid Test Model.
 *
 * @param path a JSON Pointer into the document, so the UI can highlight the step that broke
 * @param message what the schema objected to
 */
public record SchemaViolation(String path, String message) {}
