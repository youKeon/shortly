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
    public void saveAll(List<UrlClick> urlClicks) {
        List<UrlClickJpaEntity> entities = urlClicks.stream()
                .map(UrlClickJpaEntity::fromDomain)
                .toList();
        jpaRepository.saveAll(entities);
    }

    @Override
    public long countByShortCode(String shortCode) {
        return jpaRepository.countByShortCode(shortCode);
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
