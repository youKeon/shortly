package com.io.bitly.domain.click;

import java.util.List;

public interface UrlClickRepository {
    List<UrlClick> saveAll(List<UrlClick> urlClicks);
}
