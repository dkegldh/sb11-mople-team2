package com.codeit.mople.domain.auth.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuthMailService {

  private final JavaMailSender mailSender;

  @Value("${mail.from}")
  private String fromAddress;

  public void sendTemporaryPassword(String toEmail, String temporaryPassword) {
    MimeMessage message = mailSender.createMimeMessage();
    try {
      MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
      helper.setFrom(fromAddress);
      helper.setTo(toEmail);
      helper.setSubject("[Mople] 임시 비밀번호 안내");
      helper.setText(buildBody(temporaryPassword), false);
      mailSender.send(message);
    } catch (MessagingException | MailException e) {
      log.error("임시 비밀번호 이메일 발송 실패 : {}", toEmail, e);
    }
  }

  private String buildBody(String temporaryPassword) {
    return """
        임시 비밀번호가 발급되었습니다.
        
        임시 비밀번호: %s
        
        보안을 위하여 로그인 후 반드시 비밀번호를 변경해 주세요.
        임시 비밀번호는 발급 후 3분간 유효합니다.
        """.formatted(temporaryPassword);
  }
}
