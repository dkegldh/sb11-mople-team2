package com.codeit.mople.support;

import com.codeit.mople.domain.auth.repository.AccountLockRepository;
import com.codeit.mople.domain.auth.repository.RefreshTokenRepository;
import com.codeit.mople.domain.auth.repository.SessionTokenRepository;
import com.codeit.mople.domain.notification.repository.NotificationRepository;
import com.codeit.mople.domain.user.repository.UserRepository;
import com.codeit.mople.global.config.CacheNames;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class AbstractRedisCleanupTest {

  @Autowired
  protected UserRepository userRepository;

  @Autowired
  protected SessionTokenRepository sessionTokenRepository;

  @Autowired
  protected RefreshTokenRepository refreshTokenRepository;

  @Autowired
  protected AccountLockRepository accountLockRepository;

  @Autowired
  protected CacheManager cacheManager;
  @Autowired
  private NotificationRepository notificationRepository;

  @AfterEach
  protected void cleanUpRedis() {
    userRepository.findAll().forEach(user -> {
      sessionTokenRepository.invalidate(user.getId());
      refreshTokenRepository.invalidate(user.getId());
      accountLockRepository.unlock(user.getId());
    });

    notificationRepository.deleteAll();
    try {
      userRepository.deleteAll();
    } catch (DataIntegrityViolationException e) {
      notificationRepository.deleteAll();
      userRepository.deleteAll();
    }

    Cache usersCache= cacheManager.getCache(CacheNames.USERS);
    if(usersCache != null) {
      usersCache.clear();
    }
  }
}
