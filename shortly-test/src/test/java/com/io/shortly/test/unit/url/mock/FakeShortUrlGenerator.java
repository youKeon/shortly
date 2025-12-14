package com.io.shortly.test.unit.url.mock;

import com.io.shortly.url.domain.GeneratedShortCode;
import com.io.shortly.url.domain.ShortUrlGenerator;

/**
 * ShortUrlGenerator의 테스트용 Fake 구현체
 * 고정된 값을 반환하거나 설정된 값을 반환
 */
public class FakeShortUrlGenerator implements ShortUrlGenerator {

    private GeneratedShortCode resultToReturn;
    private String lastSeed;
    private int generateCallCount = 0;

    public FakeShortUrlGenerator() {
        // 기본값 설정
        this.resultToReturn = GeneratedShortCode.of(123456789L, "abc123");
    }

    public FakeShortUrlGenerator(GeneratedShortCode resultToReturn) {
        this.resultToReturn = resultToReturn;
    }

    @Override
    public GeneratedShortCode generate(String seed) {
        generateCallCount++;
        lastSeed = seed;
        return resultToReturn;
    }

    // 테스트 검증용 메서드
    public void setResultToReturn(GeneratedShortCode result) {
        this.resultToReturn = result;
    }

    public String getLastSeed() {
        return lastSeed;
    }

    public int getGenerateCallCount() {
        return generateCallCount;
    }

    public void clear() {
        lastSeed = null;
        generateCallCount = 0;
    }
}
