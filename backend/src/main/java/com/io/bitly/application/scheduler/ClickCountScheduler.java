package com.io.bitly.application.scheduler;

import com.io.bitly.domain.click.UrlClick;
import com.io.bitly.domain.click.UrlClickRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Redis 클릭 카운트를 DB에 저장하는 스케줄러 - 5분마다 실행 - Redis의 클릭 카운트를 읽어 DB에 저장 후 삭제
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ClickCountScheduler {

    private static final String CLICK_COUNT_PREFIX = "url:click:";
    private static final int BATCH_SIZE = 1000;

    private final RedisTemplate<String, String> redisTemplate;
    private final UrlClickRepository urlClickRepository;

    /**
     * 5분마다 Redis 클릭 카운트를 DB에 플러시
     */
    @Scheduled(fixedDelay = 300000)
    @Transactional
    public void saveCount() {

        ScanOptions scanOptions = ScanOptions.scanOptions()
            .match(CLICK_COUNT_PREFIX + "*")
            .count(BATCH_SIZE)
            .build();

        Cursor<byte[]> cursor = redisTemplate.getConnectionFactory()
            .getConnection()
            .scan(scanOptions);

        List<UrlClick> clickBatch = new ArrayList<>();

        while (cursor.hasNext()) {
            String key = new String(cursor.next());
            String countStr = redisTemplate.opsForValue().get(key);

            if (countStr != null) {
                Long urlId = extractUrlId(key);
                int clickCount = Integer.parseInt(countStr);

                // 클릭 수만큼 UrlClick 엔티티 생성
                for (int i = 0; i < clickCount; i++) {
                    clickBatch.add(UrlClick.of(urlId));

                    // 배치 크기에 도달하면 저장
                    if (clickBatch.size() >= BATCH_SIZE) {
                        urlClickRepository.saveAll(clickBatch);
                        clickBatch.clear();
                    }
                }

                // Redis 키 삭제
                redisTemplate.delete(key);
            }
        }

        // 남은 데이터 저장
        if (!clickBatch.isEmpty()) {
            urlClickRepository.saveAll(clickBatch);
        }

        cursor.close();
    }

    /**
     * "url:click:123" → 123
     */
    private Long extractUrlId(String key) {
        String urlIdStr = key.substring(CLICK_COUNT_PREFIX.length());
        return Long.parseLong(urlIdStr);
    }
}
