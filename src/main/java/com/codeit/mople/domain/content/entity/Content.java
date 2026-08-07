package com.codeit.mople.domain.content.entity;


import com.codeit.mople.global.entity.BaseTimeEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "contents")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Content extends BaseTimeEntity {

  @Enumerated(EnumType.STRING)
  @Column(name = "type", nullable = false)
  private ContentType type;

  @Column(name = "title", nullable = false)
  private String title;

  //긴 텍스트를 저장하기 위해 columnDefinition을 TEXT로 명시
  @Column(name = "description", columnDefinition = "TEXT")
  private String description;

  @Column(name = "thumbnailUrl")
  private String thumbnailUrl;

  @ElementCollection(fetch = FetchType.LAZY)
  @CollectionTable(name = "content_tags", joinColumns = @JoinColumn(name = "content_id"))
  @Column(name = "tags")
  private List<String> tags;

  @Column(name = "average_rating", nullable = false)
  private double averageRating = 0.0; //리뷰 평균 별점

  @Column(name = "review_count", nullable = false)
  private int reviewCount = 0; //리뷰 개수

  @Column(name = "watcher_count", nullable = false)
  private long watcherCount = 0L; //실시간 사용자 수

  //외부 API 중복 방지용 식별자 필드
  @Column(name = "external_id", unique = true)
  private String externalId;

  public Content(ContentType type, String title, String description, String thumbnailUrl, List<String> tags) {
    this.type = type;
    this.title = title;
    this.description = description;
    this.thumbnailUrl = thumbnailUrl;

    //생성 시 복사본(new ArrayList)을 사용하여 불변 리스트 참조 방지
    this.tags = tags != null ? new ArrayList<>(tags) : new ArrayList<>();
  }

  //외부 데이터 전용 생성자 오버로딩
  public Content(ContentType type, String title, String description, String thumbnailUrl, List<String> tags, String externalId) {
    this.type = type;
    this.title = title;
    this.description = description;
    this.thumbnailUrl = thumbnailUrl;
    this.tags = tags != null ? new ArrayList<>(tags) : new ArrayList<>();
    this.externalId = externalId;
  }

  //새로운 리뷰가 작성되거나 삭제 될 때,
  //서비스 계층에서 계산된 새로운 평점 평균과 리뷰 총 개수를 DB에 갱신하기 위한 메서드
  public void updateRatingStats(Double averageRating, Integer reviewCount) {
    this.averageRating = averageRating;
    this.reviewCount = reviewCount;
  }

  //관리자가 콘텐츠의 기본 정보를 수정할 때 사용하는 메서드
  public void updateContentInfo(String title, String description, String thumbnailUrl, List<String> tags) {
    if (title != null) {
      this.title = title;
    }

    if (description != null) {
      this.description = description;
    }

    if (thumbnailUrl != null) {
      this.thumbnailUrl = thumbnailUrl;
    }

    //Hibernate 컬렉션 래퍼 유지를 위한 clear/addAll 적용
    if (tags != null) {
      this.tags.clear();
      this.tags.addAll(tags);
    }
  }
}
