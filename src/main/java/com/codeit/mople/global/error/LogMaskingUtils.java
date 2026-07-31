package com.codeit.mople.global.error;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class LogMaskingUtils {

  private static final Set<String> SENSITIVE_KEYS = Set.of("email", "password");

  private LogMaskingUtils() {

  }

  public static Map<String, Object> maskSensitiveDetails(Map<String, Object> details) {
    if(details == null || details.isEmpty()) {
      return details;
    }
    Map<String, Object> masked = new LinkedHashMap<>();
    for (Map.Entry<String, Object> entry : details.entrySet()) {
      String key = entry.getKey();
      if(key != null && SENSITIVE_KEYS.contains(entry.getKey().toLowerCase(Locale.ROOT))) {
        Object value = entry.getValue();
        masked.put(entry.getKey(), value == null ? null : maskValue(String.valueOf(value)));
      } else {
        masked.put(entry.getKey(), entry.getValue());
      }
    }
    return masked;
  }

  private static String maskValue(String value) {
    if(value.length() <= 2) {
      return "***";
    }
    int visibleLength = Math.min(2, value.length() / 3);
    return value.substring(0, visibleLength) + "***";
  }
}
