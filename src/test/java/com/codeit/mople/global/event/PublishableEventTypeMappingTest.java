package com.codeit.mople.global.event;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.type.filter.AssignableTypeFilter;

@DisplayName("타입 이름 매핑 테스트")
class PublishableEventTypeMappingTest {

  static final String DOMAIN_PACKAGE = "com.codeit.mople.domain";
  static final String MAPPING_KEY = "mople.kafka.type-mapping";

  static List<String> entries;

  @BeforeAll
  static void loadMapping() {
    YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
    yaml.setResources(new ClassPathResource("application.yaml"));

    Properties properties = yaml.getObject();
    String mapping = properties == null ? null : properties.getProperty(MAPPING_KEY);

    assertThat(mapping).as(MAPPING_KEY).isNotBlank();
    entries = Arrays.stream(mapping.split(","))
        .map(String::trim)
        .filter(entry -> !entry.isEmpty())
        .toList();
  }

  private Set<String> publishableEventNames() {
    ClassPathScanningCandidateComponentProvider scanner =
        new ClassPathScanningCandidateComponentProvider(false);
    scanner.addIncludeFilter(new AssignableTypeFilter(PublishableEvent.class));

    return scanner.findCandidateComponents(DOMAIN_PACKAGE).stream()
        .map(BeanDefinition::getBeanClassName)
        .collect(Collectors.toSet());
  }

  private String aliasOf(String entry) {
    return entry.substring(0, entry.indexOf(':'));
  }

  private String classNameOf(String entry) {
    return entry.substring(entry.indexOf(':') + 1);
  }

  @Test
  @DisplayName("발행하는 이벤트가 매핑에 하나도 빠지지 않았는지")
  void mapsEveryPublishableEvent() {
    // given
    Set<String> mapped = entries.stream().map(this::classNameOf).collect(Collectors.toSet());

    // when
    Set<String> publishable = publishableEventNames();

    // then
    assertThat(publishable).isNotEmpty();
    assertThat(mapped).containsAll(publishable);
  }

  @Test
  @DisplayName("타입 이름이 겹치지 않는지")
  void hasNoDuplicateAlias() {
    // when
    List<String> aliases = entries.stream().map(this::aliasOf).toList();

    // then
    assertThat(aliases).doesNotHaveDuplicates();
  }

  @Test
  @DisplayName("타입 이름 끝에 버전이 붙어 있는지")
  void versionsEveryAlias() {
    // when
    List<String> aliases = entries.stream().map(this::aliasOf).toList();

    // then
    assertThat(aliases)
        .as("끝에 버전이 있어야 나중에 .v2로 넘어갈 수 있음")
        .allMatch(alias -> alias.matches(".+\\.v\\d+"));
  }

  @Test
  @DisplayName("매핑에 적힌 클래스가 전부 실재하는지")
  void mapsExistingClassesOnly() {
    // when
    List<String> classNames = entries.stream().map(this::classNameOf).toList();

    // then
    assertThat(classNames).allSatisfy(className ->
        assertThat(Class.forName(className)).isNotNull());
  }
}