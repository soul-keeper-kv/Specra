package dev.specra.api.feature.testcase.dto;

import dev.specra.api.feature.testcase.domain.TestCasePriority;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Set;

/**
 * What an author supplies; everything else — reference, status, flags — is the system's to manage.
 *
 * @param priority optional; {@code MEDIUM} when left out
 * @param steps replaces the whole list, in order; an empty list is a valid draft
 */
public record TestCaseRequest(
    @NotBlank(message = "{validation.testcase.title.required}") @Size(max = 200, message = "{validation.testcase.title.size}") @Schema(example = "Đăng nhập với tài khoản hợp lệ")
        String title,
    @Size(max = 10000, message = "{validation.testcase.description.size}") @Schema(example = "Người dùng đã đăng ký có thể đăng nhập bằng email và mật khẩu.")
        String description,
    @Size(max = 10000, message = "{validation.testcase.preconditions.size}") @Schema(example = "Tài khoản demo@acme.dev tồn tại và đang hoạt động")
        String preconditions,
    @Size(max = 10000, message = "{validation.testcase.expected-result.size}") @Schema(example = "Người dùng vào được Dashboard")
        String expectedResult,
    @Schema(example = "HIGH") TestCasePriority priority,
    @Size(max = 100, message = "{validation.testcase.steps.size}") List<@Valid TestCaseStepRequest> steps,
    @Schema(example = "[\"auth\",\"smoke\"]")
        Set<@Size(max = 64, message = "{validation.testcase.tag.size}") String> tags) {}
