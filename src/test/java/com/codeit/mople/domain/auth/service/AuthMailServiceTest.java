package com.codeit.mople.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
public class AuthMailServiceTest {

  @Mock
  private JavaMailSender mailSender;

  @InjectMocks
  private AuthMailService authMailService;

  private MimeMessage mimeMessage;

  @BeforeEach
  void setUp() {
    ReflectionTestUtils.setField(authMailService, "fromAddress", "no-reply@test.com");
    mimeMessage = mock(MimeMessage.class);
    when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
  }

  @Test
  @DisplayName("임시 비밀번호 이메일을 정상적으로 발송함")
  void sendTemporaryPassword_success() {
    authMailService.sendTemporaryPassword("test@test.com", "tempPassword123!");

    verify(mailSender).send(mimeMessage);
  }

  @Test
  @DisplayName("메일 발송에 실패해도 예외를 던지지 않고 조용히 종료됨")
  void sendTemporaryPassword_doesNotThrow_whenSendFails() {
    doThrow(new MailSendException("SMTP error")).when(mailSender).send(mimeMessage);

    assertThatCode(() ->
        authMailService.sendTemporaryPassword("test@test.com", "tempPassword123!"))
        .doesNotThrowAnyException();
  }
}
