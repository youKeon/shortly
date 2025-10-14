package com.io.shortly.infrastructure.persistence.jpa.click;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface UrlClickJpaRepository extends JpaRepository<UrlClickJpaEntity, Long> {

    @Query("SELECT c.urlId as urlId, COUNT(c) as clickCount " +
           "FROM UrlClickJpaEntity c " +
           "GROUP BY c.urlId " +
           "ORDER BY COUNT(c) DESC")
    List<UrlClickStats> countClicksByUrlId();

    long countByUrlId(Long urlId);

    interface UrlClickStats {
        Long getUrlId();
        Long getClickCount();
    }
}

