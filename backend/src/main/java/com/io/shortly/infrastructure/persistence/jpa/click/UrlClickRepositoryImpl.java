package com.io.shortly.infrastructure.persistence.jpa.click;

import com.io.shortly.domain.click.UrlClick;
import com.io.shortly.domain.click.UrlClickRepository;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public class UrlClickRepositoryImpl implements UrlClickRepository {

    private final UrlClickJpaRepository jpaRepository;

    public UrlClickRepositoryImpl(UrlClickJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public List<UrlClick> saveAll(List<UrlClick> urlClicks) {
        List<UrlClickJpaEntity> entities = urlClicks.stream()
            .map(UrlClickJpaEntity::fromDomain)
            .toList();

        List<UrlClickJpaEntity> savedEntities = jpaRepository.saveAll(entities);

        return savedEntities.stream()
            .map(UrlClickJpaEntity::toDomain)
            .toList();
    }
}

