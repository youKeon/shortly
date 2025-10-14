package com.io.shortly.domain.click;

import java.util.List;

public interface UrlClickRepository {
    List<UrlClick> saveAll(List<UrlClick> urlClicks);
}


