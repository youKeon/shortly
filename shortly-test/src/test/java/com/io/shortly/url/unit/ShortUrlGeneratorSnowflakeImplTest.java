package com.io.shortly.url.unit;

import com.io.shortly.url.infrastructure.generator.ShortUrlGeneratorSnowflakeImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;

@DisplayName("ShortUrlGeneratorSnowflakeImpl 단위 테스트")
class ShortUrlGeneratorSnowflakeImplTest {

    @Nested
    @DisplayName("기본 생성 기능")
    class BasicGenerationTest {

        @Test
        @DisplayName("유효한 형식의 단축 코드를 생성한다")
        void generate_ReturnsValidFormat() {
            // given
            ShortUrlGeneratorSnowflakeImpl generator = new ShortUrlGeneratorSnowflakeImpl(0, 0);
            String seed = "https://example.com";

            // when
            String shortCode = generator.generate(seed);

            // then
            assertThat(shortCode).isNotNull();
            assertThat(shortCode).matches("[0-9A-Za-z]+");
            assertThat(shortCode.length()).isGreaterThanOrEqualTo(6);
        }

        @Test
        @DisplayName("같은 seed라도 다른 코드를 생성한다 (timestamp 기반)")
        void generate_ProducesDifferentCodesForSameSeed() throws InterruptedException {
            // given
            ShortUrlGeneratorSnowflakeImpl generator = new ShortUrlGeneratorSnowflakeImpl(0, 0);
            String seed = "https://example.com";

            // when
            String code1 = generator.generate(seed);
            Thread.sleep(1); // 시간 차이 보장
            String code2 = generator.generate(seed);

            // then
            assertThat(code1).isNotEqualTo(code2);
        }
    }

    @Nested
    @DisplayName("동시성 테스트")
    class ConcurrencyTest {

        @Test
        @DisplayName("멀티스레드 환경에서 중복 없이 고유한 코드를 생성한다")
        void generate_ProducesUniqueCodesInMultithreadedEnvironment() throws InterruptedException {
            // given
            ShortUrlGeneratorSnowflakeImpl generator = new ShortUrlGeneratorSnowflakeImpl(0, 0);
            int threadCount = 10;
            int codesPerThread = 100;
            Set<String> generatedCodes = new HashSet<>();
            CountDownLatch latch = new CountDownLatch(threadCount);
            ExecutorService executorService = Executors.newFixedThreadPool(threadCount);

            // when
            for (int i = 0; i < threadCount; i++) {
                final int threadId = i;
                executorService.submit(() -> {
                    try {
                        for (int j = 0; j < codesPerThread; j++) {
                            String code = generator.generate("seed-" + threadId + "-" + j);
                            synchronized (generatedCodes) {
                                generatedCodes.add(code);
                            }
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }

            latch.await(10, TimeUnit.SECONDS);
            executorService.shutdown();

            // then
            int totalExpected = threadCount * codesPerThread;
            assertThat(generatedCodes).hasSize(totalExpected);
        }

        @Test
        @DisplayName("순차적으로 많은 코드를 생성해도 중복이 없다")
        void generate_ProducesUniqueCodesSequentially() {
            // given
            ShortUrlGeneratorSnowflakeImpl generator = new ShortUrlGeneratorSnowflakeImpl(0, 0);
            Set<String> generatedCodes = new HashSet<>();
            int count = 1000;

            // when
            for (int i = 0; i < count; i++) {
                String code = generator.generate("seed-" + i);
                generatedCodes.add(code);
            }

            // then
            assertThat(generatedCodes).hasSize(count);
        }
    }

    @Nested
    @DisplayName("Snowflake ID 검증")
    class SnowflakeValidationTest {

        @Test
        @DisplayName("유효하지 않은 workerId로 생성 시 예외를 발생시킨다")
        void constructor_ThrowsExceptionForInvalidWorkerId() {
            // when & then
            assertThatThrownBy(() -> new ShortUrlGeneratorSnowflakeImpl(-1, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("workerId");

            assertThatThrownBy(() -> new ShortUrlGeneratorSnowflakeImpl(32, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("workerId");
        }

        @Test
        @DisplayName("유효하지 않은 datacenterId로 생성 시 예외를 발생시킨다")
        void constructor_ThrowsExceptionForInvalidDatacenterId() {
            // when & then
            assertThatThrownBy(() -> new ShortUrlGeneratorSnowflakeImpl(0, -1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("datacenterId");

            assertThatThrownBy(() -> new ShortUrlGeneratorSnowflakeImpl(0, 32))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("datacenterId");
        }

        @Test
        @DisplayName("최대 workerId와 datacenterId로 정상 생성한다")
        void constructor_AcceptsMaxWorkerIdAndDatacenterId() {
            // when & then
            assertThatCode(() -> new ShortUrlGeneratorSnowflakeImpl(31, 31))
                .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("서로 다른 workerId는 다른 ID를 생성한다")
        void generate_ProducesDifferentIdsForDifferentWorkerIds() {
            // given
            ShortUrlGeneratorSnowflakeImpl generator1 = new ShortUrlGeneratorSnowflakeImpl(0, 0);
            ShortUrlGeneratorSnowflakeImpl generator2 = new ShortUrlGeneratorSnowflakeImpl(1, 0);
            String seed = "https://example.com";

            // when
            String code1 = generator1.generate(seed);
            String code2 = generator2.generate(seed);

            // then
            assertThat(code1).isNotEqualTo(code2);
        }
    }

    @Nested
    @DisplayName("Base62 인코딩 검증")
    class Base62EncodingTest {

        @Test
        @DisplayName("Base62 문자만 포함한다 (0-9, A-Z, a-z)")
        void generate_ContainsOnlyBase62Characters() {
            // given
            ShortUrlGeneratorSnowflakeImpl generator = new ShortUrlGeneratorSnowflakeImpl(0, 0);
            String seed = "https://example.com";

            // when
            String shortCode = generator.generate(seed);

            // then
            assertThat(shortCode).matches("[0-9A-Za-z]+");
        }

        @Test
        @DisplayName("생성된 코드는 최소 6자 이상이다")
        void generate_HasMinimumLength() {
            // given
            ShortUrlGeneratorSnowflakeImpl generator = new ShortUrlGeneratorSnowflakeImpl(0, 0);

            // when
            String shortCode = generator.generate("https://example.com");

            // then
            assertThat(shortCode.length()).isGreaterThanOrEqualTo(6);
        }

        @Test
        @DisplayName("긴 URL도 일정한 길이의 코드를 생성한다")
        void generate_ProducesConsistentLengthForLongUrls() {
            // given
            ShortUrlGeneratorSnowflakeImpl generator = new ShortUrlGeneratorSnowflakeImpl(0, 0);
            String longUrl = "https://example.com/" + "a".repeat(1000);

            // when
            String shortCode = generator.generate(longUrl);

            // then
            assertThat(shortCode.length()).isLessThan(15); // Snowflake ID는 보통 11~13자
        }

        @Test
        @DisplayName("빈 seed에도 정상적으로 코드를 생성한다")
        void generate_HandlesEmptySeed() {
            // given
            ShortUrlGeneratorSnowflakeImpl generator = new ShortUrlGeneratorSnowflakeImpl(0, 0);

            // when
            String shortCode = generator.generate("");

            // then
            assertThat(shortCode).isNotNull();
            assertThat(shortCode).matches("[0-9A-Za-z]+");
        }
    }
}
