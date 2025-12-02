package com.io.shortly.redirect.unit;

import static org.assertj.core.api.Assertions.assertThat;

import com.io.shortly.redirect.infrastructure.cache.CacheKeyGenerator;
import com.io.shortly.redirect.infrastructure.cache.CacheLayer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("CacheKeyGenerator 단위 테스트")
class CacheKeyGeneratorTest {

    @Nested
    @DisplayName("캐시 키 생성")
    class GenerateCacheKeyTest {

        @Test
        @DisplayName("L1 캐시 키를 올바른 형식으로 생성한다")
        void generateCacheKey_L1_ReturnsCorrectFormat() {
            // given
            String shortCode = "abc123";

            // when
            String key = CacheKeyGenerator.generateCacheKey(CacheLayer.L1, shortCode);

            // then
            assertThat(key).isEqualTo("redirect:l1:abc123");
        }

        @Test
        @DisplayName("L2 캐시 키를 올바른 형식으로 생성한다")
        void generateCacheKey_L2_ReturnsCorrectFormat() {
            // given
            String shortCode = "xyz789";

            // when
            String key = CacheKeyGenerator.generateCacheKey(CacheLayer.L2, shortCode);

            // then
            assertThat(key).isEqualTo("redirect:l2:xyz789");
        }

        @Test
        @DisplayName("다른 shortCode는 다른 캐시 키를 생성한다")
        void generateCacheKey_DifferentShortCodes_ProducesDifferentKeys() {
            // when
            String key1 = CacheKeyGenerator.generateCacheKey(CacheLayer.L1, "code1");
            String key2 = CacheKeyGenerator.generateCacheKey(CacheLayer.L1, "code2");

            // then
            assertThat(key1).isNotEqualTo(key2);
            assertThat(key1).isEqualTo("redirect:l1:code1");
            assertThat(key2).isEqualTo("redirect:l1:code2");
        }

        @Test
        @DisplayName("같은 레이어와 shortCode는 항상 같은 키를 생성한다 (멱등성)")
        void generateCacheKey_SameInputs_ProducesSameKey() {
            // given
            String shortCode = "same123";

            // when
            String key1 = CacheKeyGenerator.generateCacheKey(CacheLayer.L1, shortCode);
            String key2 = CacheKeyGenerator.generateCacheKey(CacheLayer.L1, shortCode);

            // then
            assertThat(key1).isEqualTo(key2);
        }

        @Test
        @DisplayName("같은 shortCode라도 다른 레이어는 다른 키를 생성한다")
        void generateCacheKey_DifferentLayers_ProducesDifferentKeys() {
            // given
            String shortCode = "multi123";

            // when
            String l1Key = CacheKeyGenerator.generateCacheKey(CacheLayer.L1, shortCode);
            String l2Key = CacheKeyGenerator.generateCacheKey(CacheLayer.L2, shortCode);

            // then
            assertThat(l1Key).isNotEqualTo(l2Key);
            assertThat(l1Key).isEqualTo("redirect:l1:multi123");
            assertThat(l2Key).isEqualTo("redirect:l2:multi123");
        }

        @Test
        @DisplayName("특수 문자가 포함된 shortCode도 처리한다")
        void generateCacheKey_SpecialCharacters_HandlesCorrectly() {
            // given
            String shortCode = "test-123_abc";

            // when
            String key = CacheKeyGenerator.generateCacheKey(CacheLayer.L1, shortCode);

            // then
            assertThat(key).isEqualTo("redirect:l1:test-123_abc");
        }
    }
}
