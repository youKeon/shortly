package com.io.shortly.redirect.infrastructure.persistence.jpa;

import com.io.shortly.redirect.domain.Redirect;
import com.io.shortly.redirect.domain.RedirectRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

@Slf4j
@Repository
@RequiredArgsConstructor
public class RedirectRepositoryJpaImpl implements RedirectRepository {

    private final RedirectJpaRepository jpaRepository;

    @Override
    public Optional<Redirect> findByShortCode(String shortCode) {
        log.debug("[DB] 리디렉션 조회 중: shortCode={}", shortCode);
        return jpaRepository.findByShortCode(shortCode)
            .map(entity -> {
                log.debug("[DB] 조회 완료: shortCode={}", shortCode);
                return entity.toDomain();
            });
    }

    @Override
    public Redirect save(Redirect redirect) {
        log.info("[DB] 리디렉션 저장 중: shortCode={}", redirect.getShortCode());
        RedirectJpaEntity entity = RedirectJpaEntity.fromDomain(redirect);
        RedirectJpaEntity saved = jpaRepository.save(entity);
        log.info("[DB] 저장 완료: shortCode={}", saved.getShortCode());
        return saved.toDomain();
    }

    @Override
    public boolean existsByShortCode(String shortCode) {
        boolean exists = jpaRepository.existsByShortCode(shortCode);
        log.debug("[DB] 존재 여부 확인: shortCode={}, exists={}", shortCode, exists);
        return exists;
    }
}
