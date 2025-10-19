package com.io.shortly.url.infrastructure.persistence.jpa;

import com.io.shortly.url.domain.ShortUrl;
import com.io.shortly.url.domain.ShortUrlRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class ShortUrlRepositoryJpaImpl implements ShortUrlRepository {

    private final ShortUrlJpaRepository jpaRepository;

    @Override
    public ShortUrl save(ShortUrl shortUrl) {
        ShortUrlJpaEntity entity = jpaRepository.save(
            ShortUrlJpaEntity.fromDomain(shortUrl)
        );
        return entity.toDomain();
    }

    @Override
    public Optional<ShortUrl> findByShortCode(String shortCode) {
        return jpaRepository.findByShortCode(shortCode)
                .map(ShortUrlJpaEntity::toDomain);
    }

    @Override
    public boolean existsByShortCode(String shortCode) {
        return jpaRepository.existsByShortCode(shortCode);
    }
}
