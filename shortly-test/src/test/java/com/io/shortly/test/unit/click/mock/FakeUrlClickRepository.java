package com.io.shortly.test.unit.click.mock;

import com.io.shortly.click.domain.UrlClick;
import com.io.shortly.click.domain.UrlClickRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * UrlClickRepository의 테스트용 Fake 구현체
 * 메모리 기반 저장소로 동작
 */
public class FakeUrlClickRepository implements UrlClickRepository {

    private final Map<String, List<UrlClick>> storage = new HashMap<>();
    private long idSequence = 1L;

    @Override
    public UrlClick save(UrlClick urlClick) {
        String shortCode = urlClick.getShortCode();

        // ID가 없으면 자동 생성 (restore 메서드 사용)
        UrlClick savedClick = UrlClick.restore(
            idSequence++,
            urlClick.getEventId(),
            urlClick.getShortCode(),
            urlClick.getOriginalUrl(),
            urlClick.getClickedAt()
        );

        storage.computeIfAbsent(shortCode, k -> new ArrayList<>()).add(savedClick);
        return savedClick;
    }

    @Override
    public void saveAll(List<UrlClick> urlClicks) {
        urlClicks.forEach(this::save);
    }

    @Override
    public long countByShortCode(String shortCode) {
        return storage.getOrDefault(shortCode, List.of()).size();
    }

    @Override
    public List<UrlClick> findByShortCodeAndClickedAtBetween(
            String shortCode,
            LocalDateTime start,
            LocalDateTime end
    ) {
        return storage.getOrDefault(shortCode, List.of())
                .stream()
                .filter(click -> {
                    LocalDateTime clickedAt = click.getClickedAt();
                    return !clickedAt.isBefore(start) && !clickedAt.isAfter(end);
                })
                .collect(Collectors.toList());
    }

    // 테스트 검증용 메서드
    public void clear() {
        storage.clear();
        idSequence = 1L;
    }

    public int getTotalCount() {
        return storage.values().stream()
                .mapToInt(List::size)
                .sum();
    }
}
