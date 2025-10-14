package com.io.shortly.application.scheduler;

import com.io.shortly.domain.click.UrlClick;
import com.io.shortly.domain.click.UrlClickRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Profile("!phase5")
@Component
@RequiredArgsConstructor
public class ClickCountScheduler {

    private static final String CLICK_COUNT_PREFIX = "url:click:";
    private static final int BATCH_SIZE = 1000;

    private final RedisTemplate<String, String> redisTemplate;
    private final UrlClickRepository urlClickRepository;

    @Scheduled(fixedDelay = 300000) // 5분
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

