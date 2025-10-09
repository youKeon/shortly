package com.io.bitly.infrastructure.persistence.shorturl;

import com.io.bitly.domain.shorturl.ShortUrl;
import com.io.bitly.domain.shorturl.ShortUrlRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class ShortUrlRepositoryImpl implements ShortUrlRepository {

    private final ShortUrlJpaRepository jpaRepository;

    public ShortUrlRepositoryImpl(ShortUrlJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public ShortUrl save(ShortUrl shortUrl) {
        ShortUrlJpaEntity entity = ShortUrlJpaEntity.fromDomain(shortUrl);
        ShortUrlJpaEntity savedEntity = jpaRepository.save(entity);
        return savedEntity.toDomain();
    }

    @Override
    public Optional<ShortUrl> findByShortUrl(String shortCode) {
        return jpaRepository.findByShortUrl(shortCode)
                .map(ShortUrlJpaEntity::toDomain);
    }

    @Override
    public boolean existsByShortUrl(String shortCode) {
        return jpaRepository.existsByShortUrl(shortCode);
    }
}
