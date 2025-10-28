package com.io.shortly.url.infrastructure.persistence.jpa.url;

import com.io.shortly.url.domain.url.ShortUrl;
import com.io.shortly.url.domain.url.ShortUrlRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class ShortUrlRepositoryJpaImpl implements ShortUrlRepository {

    private final ShortUrlJpaRepository jpaRepository;

    @Override
    public void save(ShortUrl shortUrl) {
        jpaRepository.save(ShortUrlJpaEntity.fromDomain(shortUrl));
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
