package com.codeit.mople.global;

import static org.assertj.core.api.Assertions.assertThat;

import com.codeit.mople.global.error.LogMaskingUtils;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class LogMaskingUtilsTest {

  @Test
  @DisplayName("email 키의 값은 마스킹됨")
  void maskSensitiveDetails_masksEmail() {
    Map<String, Object> details = Map.of("email", "dup@test.com");
    Map<String, Object> result = LogMaskingUtils.maskSensitiveDetails(details);
    assertThat(result).containsKey("email");
    assertThat(result.get("email"))
        .isNotNull()
        .isNotEqualTo("dup@test.com");
  }

  @Test
  @DisplayName("민감하지 않은 키는 그대로 유지됨")
  void maskSensitiveDetails_keepNonSensitiveKeys() {
    Map<String, Object> details = Map.of("count", 5);
    Map<String, Object> result = LogMaskingUtils.maskSensitiveDetails(details);
    assertThat(result.get("count")).isEqualTo(5);
  }

  @Test
  @DisplayName("민감 키의 값이 null이면 마스킹하지 않고 null을 그대로 유지한다")
  void maskSensitiveDetails_keepsNull_whenSensitiveValueIsNull() {
    Map<String, Object> details = new HashMap<>();
    details.put("email", null);

    Map<String, Object> result = LogMaskingUtils.maskSensitiveDetails(details);

    assertThat(result.get("email")).isNull();
  }
}
