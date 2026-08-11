package com.codeit.mople.domain.user.dto.request;

import com.codeit.mople.domain.user.exception.UserErrorCode;
import com.codeit.mople.domain.user.exception.UserException;
import java.util.NoSuchElementException;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

@Component
public class StringToUserSortByConverter implements Converter<String, UserSortBy> {

  @Override
  public UserSortBy convert(String source) {
    try {
      return UserSortBy.from(source);
    } catch (NoSuchElementException e) {
      throw new UserException(UserErrorCode.INVALID_SORT_BY);
    }
  }
}
