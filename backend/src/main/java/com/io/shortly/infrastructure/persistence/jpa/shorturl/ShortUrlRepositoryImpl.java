package com.io.shortly.infrastructure.persistence.jpa.shorturl;

import com.io.shortly.domain.shorturl.ShortUrl;
import com.io.shortly.domain.shorturl.ShortUrlRepository;
import java.util.Optional;
import org.springframework.stereotype.Repository;

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


