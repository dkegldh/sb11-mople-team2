package com.codeit.mople.global.config;

import com.codeit.mople.domain.user.dto.request.StringToUserSortByConverter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

  private final StringToUserSortByConverter stringToUserSortByConverter;

  @Override
  public void addFormatters(FormatterRegistry registry) {
    registry.addConverter(stringToUserSortByConverter);
  }

  @Override
  public void addResourceHandlers(ResourceHandlerRegistry registry) {
    //웹 브라우저에서 /uploads/**로 시작하는 요청이 들어오면
    registry.addResourceHandler("/uploads/**")
        //실제 프로젝트 폴더 최상단의 uploads/ 폴더에서 파일 찾기
        .addResourceLocations("file:uploads/");
  }
}
