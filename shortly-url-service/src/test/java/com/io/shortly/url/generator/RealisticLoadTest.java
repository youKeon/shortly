package com.io.shortly.url.generator;

import com.io.shortly.url.infrastructure.generator.ShortUrlGeneratorSnowflakeImpl;
import com.io.shortly.url.infrastructure.generator.NodeIdManager;
import org.mockito.Mockito;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * URL 단축기 실전 환경 시뮬레이션 테스트
 *
 * 시나리오: 대규모 마케팅 캠페인 시작 시점
 * - 여러 사용자가 동시에 같은 URL을 단축 요청 (예: 신상품 출시 페이지)
 * - 멀티스레드 환경에서 동일 URL 반복 생성
 */
public class RealisticLoadTest {

    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println("URL 단축기 실전 환경 테스트");
        System.out.println("시나리오: 같은 URL을 여러 사용자가 동시에 단축 요청");
        System.out.println("========================================\n");

        int threadCount = 10; // 동시 사용자 수
        int requestsPerUser = 5_000; // 사용자당 요청 수
        int totalRequests = threadCount * requestsPerUser;
        String popularUrl = "https://newly-launched-product.com"; // 인기 URL

        System.out.println("테스트 조건:");
        System.out.println("  - 동시 사용자: " + threadCount + "명");
        System.out.println("  - 사용자당 요청: " + String.format("%,d", requestsPerUser) + "건");
        System.out.println("  - 총 요청: " + String.format("%,d", totalRequests) + "건");
        System.out.println("  - 인기 URL: " + popularUrl);
        System.out.println("  (같은 URL을 여러 명이 동시에 단축)\n");

        // Snowflake Algorithm 테스트
        System.out.println("【테스트】 Snowflake Algorithm");
        System.out.println("----------------------------------------");
        testSnowflake(threadCount, requestsPerUser, popularUrl);
        System.out.println();

        System.out.println("========================================");
        System.out.println("테스트 완료");
        System.out.println("========================================");
    }

    private static void testSnowflake(int threadCount, int requestsPerUser, String url) {
        NodeIdManager nodeIdManager = Mockito.mock(NodeIdManager.class);
        Mockito.when(nodeIdManager.getWorkerId()).thenReturn(0L);
        Mockito.when(nodeIdManager.getDatacenterId()).thenReturn(0L);

        ShortUrlGeneratorSnowflakeImpl generator = new ShortUrlGeneratorSnowflakeImpl(nodeIdManager);
        Set<String> uniqueCodes = new HashSet<>();
        AtomicInteger duplicateCount = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(threadCount);
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);

        long startTime = System.currentTimeMillis();

        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    for (int j = 0; j < requestsPerUser; j++) {
                        String code = generator.generate(url).shortCode();
                        synchronized (uniqueCodes) {
                            if (!uniqueCodes.add(code)) {
                                duplicateCount.incrementAndGet();
                            }
                        }
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        try {
            latch.await(60, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        executorService.shutdown();
        long endTime = System.currentTimeMillis();

        int totalRequests = threadCount * requestsPerUser;
        int uniqueCount = uniqueCodes.size();
        int duplicates = duplicateCount.get();

        System.out.println("📊 결과:");
        System.out.println("   총 요청: " + String.format("%,d", totalRequests) + "건");
        System.out.println("   고유 코드: " + String.format("%,d", uniqueCount) + "건");
        System.out.println("   중복 발생: " + String.format("%,d", duplicates) + "건");
        System.out.println("   중복률: " + String.format("%.2f%%", (duplicates * 100.0 / totalRequests)));
        System.out.println("   처리 시간: " + (endTime - startTime) + "ms");
        System.out.println("   처리량: " + String.format("%,d", totalRequests * 1000L / (endTime - startTime)) + " 건/초");

        if (duplicates == 0) {
            System.out.println("\n   ✅ 완벽한 고유성 보장");
            System.out.println("   → 모든 요청이 고유한 short code 생성");
            System.out.println("   → 프로덕션 환경에서 안전");
        }
    }
}
