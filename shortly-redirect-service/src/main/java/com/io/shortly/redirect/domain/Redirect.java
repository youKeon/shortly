package com.io.shortly.redirect.mvc.domain;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Redirect Domain Model (Pure POJO)
 *
 * - 프레임워크 독립적 (No Lombok, No JPA)
 * - 불변성 유지 (final 필드)
 * - 비즈니스 로직 포함
 * - 도메인 규칙: shortCode는 도메인의 식별자
 */
public class Redirect {

    private final String shortCode;
    private final String targetUrl;
    private final LocalDateTime createdAt;

    /**
     * Private 생성자 - 팩토리 메서드를 통해서만 생성
     */
    private Redirect(String shortCode, String targetUrl, LocalDateTime createdAt) {
        this.shortCode = shortCode;
        this.targetUrl = targetUrl;
        this.createdAt = createdAt;
    }

    /**
     * 새로운 Redirect 생성 (팩토리 메서드)
     *
     * @param shortCode 단축 코드
     * @param targetUrl 원본 URL
     * @return 생성된 Redirect 인스턴스
     */
    public static Redirect create(String shortCode, String targetUrl) {
        return new Redirect(shortCode, targetUrl, LocalDateTime.now());
    }

    /**
     * 기존 데이터로부터 Redirect 재구성 (팩토리 메서드)
     *
     * @param shortCode 단축 코드
     * @param targetUrl 원본 URL
     * @param createdAt 생성 시각
     * @return 재구성된 Redirect 인스턴스
     */
    public static Redirect of(String shortCode, String targetUrl, LocalDateTime createdAt) {
        return new Redirect(shortCode, targetUrl, createdAt);
    }

    // Getters
    public String getShortCode() {
        return shortCode;
    }

    public String getTargetUrl() {
        return targetUrl;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    /**
     * 동등성 비교: shortCode 기준
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Redirect redirect = (Redirect) o;
        return Objects.equals(shortCode, redirect.shortCode);
    }

    /**
     * 해시코드: shortCode 기준
     */
    @Override
    public int hashCode() {
        return Objects.hash(shortCode);
    }

    /**
     * 문자열 표현
     */
    @Override
    public String toString() {
        return "Redirect{" +
            "shortCode='" + shortCode + '\'' +
            ", targetUrl='" + targetUrl + '\'' +
            ", createdAt=" + createdAt +
            '}';
    }
}
