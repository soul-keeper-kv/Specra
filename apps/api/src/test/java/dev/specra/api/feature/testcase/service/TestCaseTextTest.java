package dev.specra.api.feature.testcase.service;

import static org.assertj.core.api.Assertions.assertThat;

import dev.specra.api.feature.testcase.domain.AutomationStatus;
import dev.specra.api.feature.testcase.domain.TestCasePriority;
import dev.specra.api.feature.testcase.dto.TestCaseResponse;
import dev.specra.api.feature.testcase.dto.TestCaseStepResponse;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TestCaseTextTest {

  @Test
  void rendersEverySectionInOrderAndSkipsTheEmptyOnes() {
    TestCaseResponse testCase =
        new TestCaseResponse(
            UUID.randomUUID(),
            UUID.randomUUID(),
            "TC-4",
            "Đăng nhập hợp lệ",
            "Người dùng đã đăng ký đăng nhập được.",
            null,
            "Vào được Dashboard",
            TestCasePriority.HIGH,
            AutomationStatus.NOT_AUTOMATED,
            false,
            List.of(
                new TestCaseStepResponse(1, "Mở trang đăng nhập", null),
                new TestCaseStepResponse(2, "Nhập thông tin", "Nút bật")),
            Set.of("auth"),
            null,
            Instant.now(),
            Instant.now());

    String text = TestCaseText.compose(testCase);

    assertThat(text)
        .startsWith("TC-4 — Đăng nhập hợp lệ")
        .contains("Người dùng đã đăng ký đăng nhập được.")
        .contains("Steps:\n1. Mở trang đăng nhập\n2. Nhập thông tin => Nút bật")
        .contains("Expected result: Vào được Dashboard")
        .doesNotContain("Preconditions");
  }
}
