package dev.specra.api.feature.codegen.dto;

/**
 * One file a generation proposes.
 *
 * <p>The body travels with the proposal because there is nowhere else for it to be yet — Git is the
 * source of truth for applied code, and this file has not been applied. Once it is, the database
 * keeps only the path and a hash ({@code automation_files}).
 *
 * @param status NEW when the repository has no such file, MODIFIED when it does and differs,
 *     UNCHANGED when the projection matches what is already committed
 * @param previous the current contents in the working copy, for the diff; null when NEW
 */
public record GeneratedFileResponse(
    String path, String role, String status, String contents, String previous) {}
