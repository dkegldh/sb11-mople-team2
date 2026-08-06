package com.codeit.mople.domain.review.dto.request;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ReviewUpdateRequest (
    
    // (?s) : .이 붙으면 줄바꾼 다음 줄에도 해당 규칙 포함
    // (?! ... ) : ...은 금지되는 패턴
    // \p{javaWhitespace} : 공백문자 " ", 탭, 줄바꿈 등(\p... 앞에 \는 \p...를 문자열로 표시하기 위해 추가)
    // ...* : ... 패턴이 0개 이상 반복(""를 검증하기 위해 0개 이상으로 설정, 1개 이상은 *가 아닌 +를 사용)
    // $ : 표현식 끝
    // null은 허용(null 체크는 Review 엔티티의 update()에서 체크)
    @Pattern(regexp = "(?s)(?!\\p{javaWhitespace}*$).+", message = "리뷰 내용을 입력해주세요.")
    @Size(max = 500, message = "리뷰 내용은 500자 이하여야 합니다.")
    String text,

    @DecimalMin(value = "1.0", message = "별점은 1점 이상이어야 합니다.")
    @DecimalMax(value = "5.0", message = "별점은 5점 이하여야 합니다.")
    Double rating
) {

    @AssertTrue(message = "수정할 리뷰 내용 또는 별점 중 하나가 필요합니다.")
    public boolean isAnyFieldPresent() {
        return text != null || rating != null;
    }

}