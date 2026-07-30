package com.codeit.mople.domain.user.init;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.codeit.mople.domain.user.entity.Role;
import com.codeit.mople.domain.user.entity.User;
import com.codeit.mople.domain.user.repository.UserRepository;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.ApplicationArguments;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AdminInitializerTest {

  @InjectMocks
  private AdminInitializer adminInitializer;

  @Mock
  private UserRepository userRepository;

  @Mock
  private PasswordEncoder passwordEncoder;

  @Mock
  private AdminInserter adminInserter;

  @Mock
  private ApplicationArguments applicationArguments;

  @BeforeEach
  void setUp() {
    AdminProperties adminProperties = new AdminProperties("admin@mople.com", "Admin1234!", "관리자");
    org.springframework.test.util.ReflectionTestUtils.setField(
        adminInitializer, "adminProperties", adminProperties);
  }

  @Test
  void 어드민_계정이_없으면_생성한다() throws Exception {
    given(userRepository.existsByEmail("admin@mople.com")).willReturn(false);
    given(userRepository.existsByRole(Role.ADMIN)).willReturn(false);
    given(passwordEncoder.encode(anyString())).willReturn("encoded-password");

    adminInitializer.run(applicationArguments);

    verify(adminInserter).insert(any(User.class), anyString());
  }

  @Test
  void 어드민_계정이_이미_있으면_생성하지_않는다() throws Exception {
    given(userRepository.existsByRole(Role.ADMIN)).willReturn(true);

    adminInitializer.run(applicationArguments);

    verify(adminInserter, never()).insert(any(User.class), anyString());
  }

  @Test
  void 어드민_이메일을_일반유저가_사용중이면_생성하지_않는다() throws Exception {
    given(userRepository.existsByEmail("admin@mople.com")).willReturn(true);

    adminInitializer.run(applicationArguments);

    verify(adminInserter, never()).insert(any(User.class), anyString());
  }

  @Test
  void 동시_초기화로_이메일_유니크_제약_위반이_발생하면_조용히_넘어간다() throws Exception {
    given(userRepository.existsByEmail("admin@mople.com")).willReturn(false);
    given(userRepository.existsByRole(Role.ADMIN)).willReturn(false);
    given(passwordEncoder.encode(anyString())).willReturn("encoded-password");

    ConstraintViolationException cause =
        new ConstraintViolationException("unique constraint", null, "uq_users_email");
    DataIntegrityViolationException ex = new DataIntegrityViolationException("", cause);
    willThrow(ex).given(adminInserter).insert(any(User.class), anyString());

    adminInitializer.run(applicationArguments);  // 예외 없이 정상 종료
  }
}
