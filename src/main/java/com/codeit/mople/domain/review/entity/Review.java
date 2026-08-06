package com.codeit.mople.domain.review.entity;

import com.codeit.mople.domain.content.entity.Content;
import com.codeit.mople.domain.user.entity.User;
import com.codeit.mople.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "reviews",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_review_content_author",
            columnNames = {"content_id", "author_id"}
        )
    }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Review extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "content_id", nullable = false)
  private Content content;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "author_id", nullable = false)
  private User author;

  @Column(columnDefinition = "TEXT", nullable = false)
  private String text;

  @Column(nullable = false)
  private double rating;

  private Review(Content content, User author, String text, double rating) {
    this.content = content;
    this.author = author;
    this.text = text;
    this.rating = rating;
  }

  public static Review create(Content content, User author, String text, double rating) {
    return new Review(content, author, text, rating);
  }

  public void updateText(String text) {
    this.text = text;
  }

  public void updateRating(Double rating) {
    this.rating = rating;
  }

}
