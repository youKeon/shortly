package com.io.bitly.infrastructure.persistence.click;

import com.io.bitly.domain.click.UrlClick;
import com.io.bitly.domain.click.UrlClickRepository;
import org.springframework.stereotype.Repository;

@Repository
public class UrlClickRepositoryImpl implements UrlClickRepository {

    private final UrlClickJpaRepository jpaRepository;

    public UrlClickRepositoryImpl(UrlClickJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public UrlClick save(UrlClick urlClick) {
        UrlClickJpaEntity entity = UrlClickJpaEntity.fromDomain(urlClick);
        UrlClickJpaEntity savedEntity = jpaRepository.save(entity);
        return savedEntity.toDomain();
    }
}
