package dev.specra.api.feature.testcase.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * @param action what the tester does, in their own words
 * @param data input or fixture data used by this action
 * @param expected what they should observe, if this step checks anything
 */
public record TestCaseStepRequest(
    @NotBlank(message = "{validation.testcase.step.action.required}") @Size(max = 2000, message = "{validation.testcase.step.action.size}") @Schema(example = "Nhập email hợp lệ và bấm Đăng nhập")
        String action,
    @Size(max = 2000, message = "{validation.testcase.step.data.size}") @Schema(example = "demo@acme.dev / QA_USER_PASSWORD")
        String data,
    @Size(max = 2000, message = "{validation.testcase.step.expected.size}") @Schema(example = "Trang Dashboard hiển thị tên người dùng")
        String expected) {}
