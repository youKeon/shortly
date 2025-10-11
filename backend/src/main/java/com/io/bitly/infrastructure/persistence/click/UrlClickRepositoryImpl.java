package com.io.bitly.infrastructure.persistence.click;

import com.io.bitly.domain.click.UrlClick;
import com.io.bitly.domain.click.UrlClickRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
