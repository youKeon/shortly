package com.io.shortly.click.infrastructure.persistence.jpa;

import com.io.shortly.click.domain.UrlClick;
import com.io.shortly.click.domain.UrlClickRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
public class UrlClickRepositoryJpaImpl implements UrlClickRepository {

    private final UrlClickJpaRepository jpaRepository;

    @Override
    public UrlClick save(UrlClick urlClick) {
        UrlClickJpaEntity entity = UrlClickJpaEntity.fromDomain(urlClick);
        UrlClickJpaEntity saved = jpaRepository.save(entity);
        return saved.toDomain();
    }

    @Override
    public long countByShortCode(String shortCode) {
        return jpaRepository.countByShortCode(shortCode);
    }

    @Override
    public List<UrlClick> findByShortCode(String shortCode) {
        return jpaRepository.findByShortCode(shortCode).stream()
                .map(UrlClickJpaEntity::toDomain)
                .toList();
    }

    @Override
    public List<UrlClick> findByShortCodeWithLimit(String shortCode, int limit) {
        return jpaRepository.findByShortCodeWithLimit(
                shortCode,
                org.springframework.data.domain.PageRequest.of(0, limit)
        ).stream()
                .map(UrlClickJpaEntity::toDomain)
                .toList();
    }

    @Override
    public List<UrlClick> findByShortCodeAndClickedAtBetween(
            String shortCode,
            LocalDateTime start,
            LocalDateTime end
    ) {
        return jpaRepository.findByShortCodeAndClickedAtBetween(shortCode, start, end).stream()
                .map(UrlClickJpaEntity::toDomain)
                .toList();
    }
}
