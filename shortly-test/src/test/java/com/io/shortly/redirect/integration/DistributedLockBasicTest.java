package com.io.shortly.redirect.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.io.shortly.redirect.RedirectServiceApplication;
import com.io.shortly.redirect.application.RedirectService;
import com.io.shortly.redirect.domain.RedirectCache;
import com.io.shortly.redirect.infrastructure.restclient.UrlLookupResponse;
import org.springframework.web.client.RestClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

/**
 * 분산 락 기본 동작 테스트
 */
@SpringBootTest(
    classes = RedirectServiceApplication.class,
    properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration"
    }
)
@ActiveProfiles("local")
@DisplayName("분산 락 기본 동작 테스트")
class DistributedLockBasicTest {

    @Autowired
    private RedirectService redirectService;

    @Autowired
    private RedirectCache redirectCache;

    @Autowired
    private RedissonClient redissonClient;

    @MockBean
    private RestClient urlServiceRestClient;

    @BeforeEach
    void setUp() {
        // 캐시 클리어는 TTL로 자동 만료됨
        // evict() 메서드는 URL이 immutable하므로 제거됨
    }

    @Test
    @DisplayName("분산 락이 정상적으로 동작한다")
    void distributedLock_Works() {
        // given
        String shortCode = "lock" + System.nanoTime();  // 유니크한 shortCode
        String originalUrl = "https://example.com/test";

        // RestClient Mock 설정
        mockRestClient(shortCode, originalUrl);

        // when
        var result = redirectService.getOriginalUrl(shortCode);

        // then
        assertThat(result).isNotNull();
        assertThat(result.originalUrl()).isEqualTo(originalUrl);

        // RestClient는 1번만 호출되어야 함 (캐시 미스 → URL Service API 호출)
        verify(urlServiceRestClient, times(1)).get();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void mockRestClient(String shortCode, String originalUrl) {
        RestClient.RequestHeadersUriSpec mockUriSpec = mock(RestClient.RequestHeadersUriSpec.class);
        RestClient.ResponseSpec mockResponseSpec = mock(RestClient.ResponseSpec.class);

        when(urlServiceRestClient.get()).thenReturn(mockUriSpec);
        when(mockUriSpec.uri(any(String.class), any(Object.class))).thenReturn(mockUriSpec);
        when(mockUriSpec.retrieve()).thenReturn(mockResponseSpec);
        when(mockResponseSpec.onStatus(any(), any())).thenReturn(mockResponseSpec);
        when(mockResponseSpec.body(UrlLookupResponse.class))
            .thenReturn(new UrlLookupResponse(shortCode, originalUrl));
    }

    @Test
    @DisplayName("Redis 연결이 정상적이다")
    void redis_Connection_Works() {
        // given
        String key = "test:key";
        var bucket = redissonClient.getBucket(key);

        // when
        bucket.set("test value");
        Object value = bucket.get();

        // then
        assertThat(value).isEqualTo("test value");

        // cleanup
        bucket.delete();
    }

    @Test
    @DisplayName("Redisson 락이 정상적으로 획득/해제된다")
    void redisson_Lock_AcquireAndRelease() {
        // given
        String lockKey = "test:lock:key";
        var lock = redissonClient.getLock(lockKey);

        // when
        boolean acquired = lock.tryLock();

        // then
        assertThat(acquired).isTrue();
        assertThat(lock.isLocked()).isTrue();
        assertThat(lock.isHeldByCurrentThread()).isTrue();

        // cleanup
        lock.unlock();
        assertThat(lock.isLocked()).isFalse();
    }
}
