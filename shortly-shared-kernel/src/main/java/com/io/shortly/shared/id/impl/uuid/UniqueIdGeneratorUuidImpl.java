package com.io.shortly.shared.id.impl.uuid;

import com.io.shortly.shared.id.UniqueIdGenerator;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class UniqueIdGeneratorUuidImpl implements UniqueIdGenerator {

    @Override
    public long generate() {
        return UUID.randomUUID().getMostSignificantBits();
    }
}
