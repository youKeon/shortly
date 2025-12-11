package com.io.shortly.test.benchmark;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.io.shortly.shared.id.impl.snowflake.NodeIdManager;
import com.io.shortly.shared.id.impl.snowflake.UniqueIdGeneratorSnowflakeImpl;
import com.io.shortly.shared.id.impl.uuid.UniqueIdGeneratorUuidImpl;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

@BenchmarkMode({ Mode.Throughput, Mode.AverageTime })
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 2, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(value = 1, jvmArgs = { "-Xms2G", "-Xmx2G" })
public class IdGeneratorBenchmark {

    private UniqueIdGeneratorUuidImpl uuidGenerator;
    private UniqueIdGeneratorSnowflakeImpl snowflakeGenerator;

    @Setup
    public void setup() {
        // UUID
        uuidGenerator = new UniqueIdGeneratorUuidImpl();

        // Snowflake
        NodeIdManager mockNodeIdManager = mock(NodeIdManager.class);
        when(mockNodeIdManager.getWorkerId()).thenReturn(1L);
        when(mockNodeIdManager.getDatacenterId()).thenReturn(1L);

        snowflakeGenerator = new UniqueIdGeneratorSnowflakeImpl(mockNodeIdManager);
    }

    @Benchmark
    public long generateUuid() {
        return uuidGenerator.generate();
    }

    @Benchmark
    public long generateSnowflake() {
        return snowflakeGenerator.generate();
    }
}
