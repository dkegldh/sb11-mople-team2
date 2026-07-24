package com.codeit.mople.global.config;

import com.codeit.mople.global.filter.MdcLoggingFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FilterConfig {

  @Bean
  public FilterRegistrationBean<MdcLoggingFilter> mdcLoggingFilter() {
    FilterRegistrationBean<MdcLoggingFilter> registrationBean = new FilterRegistrationBean<>();
    registrationBean.setFilter(new MdcLoggingFilter());
    registrationBean.setOrder(1);
    registrationBean.addUrlPatterns("/*");
    return registrationBean;
  }
}
