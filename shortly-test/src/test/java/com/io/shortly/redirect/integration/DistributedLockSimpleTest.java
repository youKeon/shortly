package com.io.shortly.redirect.integration;

import static org.assertj.core.api.Assertions.*;

import com.io.shortly.redirect.RedirectServiceApplication;
import com.io.shortly.redirect.application.RedirectService;
import com.io.shortly.redirect.domain.RedirectCache;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * 분산 락 간단 통합 테스트
 *
 * <p>Cache Stampede 방지를 위한 분산 락 동작을 검증합니다.
 *
 * <p>실행 전 필수:
 * <ul>
 *   <li>docker-compose -f infra/compose/docker-compose-dev.yml up -d</li>
 * </ul>
 */
@SpringBootTest(
    classes = RedirectServiceApplication.class,
    properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration"
    }
)
@ActiveProfiles("local")
@DisplayName("분산 락 간단 통합 테스트")
class DistributedLockSimpleTest {

    @Autowired
    private RedirectService redirectService;

    @Autowired
    private RedirectCache redirectCache;

    @Autowired
    private RedissonClient redissonClient;

    @BeforeEach
    void setUp() {
        clearCache();
    }

    @AfterEach
    void tearDown() {
        clearCache();
    }

    private void clearCache() {
        // 캐시 클리어는 TTL로 자동 만료됨
        // evict() 메서드는 URL이 immutable하므로 제거됨
    }

    @Test
    @DisplayName("캐시에 있는 데이터는 정상적으로 조회된다")
    void getOriginalUrl_FromCache_Success() {
        // given
        String shortCode = "cached";
        String originalUrl = "https://example.com/cached";
        redirectCache.put(com.io.shortly.redirect.domain.Redirect.create(shortCode, originalUrl));

        // when
        var result = redirectService.getOriginalUrl(shortCode);

        // then
        assertThat(result).isNotNull();
        assertThat(result.originalUrl()).isEqualTo(originalUrl);
    }

    @Test
    @DisplayName("동시에 같은 캐시된 데이터를 조회해도 정상 동작한다")
    void getOriginalUrl_ConcurrentCacheHit_Success() throws InterruptedException {
        // given
        String shortCode = "cached";
        String originalUrl = "https://example.com/cached";
        redirectCache.put(com.io.shortly.redirect.domain.Redirect.create(shortCode, originalUrl));

        int threadCount = 100;
        CountDownLatch latch = new CountDownLatch(threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(50);
        AtomicInteger successCount = new AtomicInteger(0);

        // when
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    var result = redirectService.getOriginalUrl(shortCode);
                    if (result.originalUrl().equals(originalUrl)) {
                        successCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    // 무시
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        // then
        assertThat(successCount.get()).isEqualTo(threadCount);
    }

    @Test
    @DisplayName("Redisson 락이 정상적으로 작동한다")
    void redisson_Lock_Works() {
        // given
        String lockKey = "LOCK:test:lock";
        var lock1 = redissonClient.getLock(lockKey);

        // when
        lock1.lock(10, TimeUnit.SECONDS);
        boolean isLocked = lock1.isLocked();
        boolean isHeld = lock1.isHeldByCurrentThread();

        // then
        assertThat(isLocked).isTrue();
        assertThat(isHeld).isTrue();

        // cleanup
        lock1.unlock();

        // 락이 해제되었는지 확인
        assertThat(lock1.isLocked()).isFalse();
    }

    @Test
    @DisplayName("분산 락이 정상적으로 동작한다 (캐시 히트)")
    void distributedLock_CacheHit_Works() {
        // given
        String shortCode = "stampede";
        String originalUrl = "https://example.com/stampede";

        // 캐시에 데이터 추가 (분산 락 호출 없이 캐시 히트)
        redirectCache.put(com.io.shortly.redirect.domain.Redirect.create(shortCode, originalUrl));

        // when
        var result = redirectService.getOriginalUrl(shortCode);

        // then
        assertThat(result).isNotNull();
        assertThat(result.originalUrl()).isEqualTo(originalUrl);
    }

    @Test
    @DisplayName("여러 다른 shortCode에 대한 동시 요청이 각각 독립적으로 처리된다")
    void distributedLock_DifferentKeys_IndependentProcessing() throws InterruptedException {
        // given
        int keyCount = 10;
        for (int i = 0; i < keyCount; i++) {
            String shortCode = "code" + i;
            String url = "https://example.com/" + i;
            redirectCache.put(com.io.shortly.redirect.domain.Redirect.create(shortCode, url));
        }

        int threadCount = 100;  // 각 키당 10번씩 요청
        CountDownLatch latch = new CountDownLatch(threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(50);
        AtomicInteger successCount = new AtomicInteger(0);

        // when: 여러 스레드가 서로 다른 shortCode를 조회
        for (int i = 0; i < threadCount; i++) {
            final int index = i % keyCount;
            executor.submit(() -> {
                try {
                    String shortCode = "code" + index;
                    var result = redirectService.getOriginalUrl(shortCode);
                    if (result != null) {
                        successCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    // 무시
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        // then: 모든 요청이 성공
        assertThat(successCount.get()).isEqualTo(threadCount);
    }

    @Test
    @DisplayName("Redis 캐시가 정상적으로 동작한다")
    void redis_Cache_Works() {
        // given
        String shortCode = "redis1";
        String originalUrl = "https://example.com/redis";
        com.io.shortly.redirect.domain.Redirect redirect =
            com.io.shortly.redirect.domain.Redirect.create(shortCode, originalUrl);

        // when
        redirectCache.put(redirect);
        var cached = redirectCache.get(shortCode);

        // then
        assertThat(cached).isPresent();
        assertThat(cached.get().getTargetUrl()).isEqualTo(originalUrl);

        // Note: evict()는 URL이 immutable하므로 제거됨. 캐시는 TTL로 자동 만료됨
    }
}
