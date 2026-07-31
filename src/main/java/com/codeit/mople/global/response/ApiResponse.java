package com.codeit.mople.global.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

  private final boolean success;
  private final T data;
  private final ErrorDetail error;

  private ApiResponse(boolean success, T data, ErrorDetail error) {
    this.success = success;
    this.data = data;
    this.error = error;
  }

  public static <T> ApiResponse<T> success(T data) {
    return new ApiResponse<>(true, data, null);
  }

  public static ApiResponse<Void> success() {
    return new ApiResponse<>(true, null, null);
  }

  public static ApiResponse<Void> error(String code, String message) {
    return new ApiResponse<>(false, null, new ErrorDetail(code, message, null));
  }

  public static ApiResponse<Void> error(String code, String message, Map<String, Object> details) {
    return new ApiResponse<>(false, null, new ErrorDetail(code, message, (details == null || details.isEmpty()) ? null : details));
  }

  public boolean isSuccess() { return success; }
  public T getData() { return data; }
  public ErrorDetail getError() { return error; }

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record ErrorDetail(String code, String message, Map<String, Object> details) {}
}
