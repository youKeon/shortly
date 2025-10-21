package com.io.shortly.click.api.support;

import com.io.shortly.shared.exception.BaseExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler extends BaseExceptionHandler {
    // Inherits all common exception handlers from BaseMvcExceptionHandler
    // Add Click service-specific exception handlers here if needed
}
