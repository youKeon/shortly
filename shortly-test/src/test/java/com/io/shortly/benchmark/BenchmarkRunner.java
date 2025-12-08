package com.io.shortly.benchmark;

import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

/**
 * JMH 벤치마크 실행 클래스
 *
 * 실행 방법:
 * 1. Gradle로 실행:
 *    ./gradlew :shortly-test:test --tests BenchmarkRunner
 *
 * 2. IDE에서 직접 실행:
 *    main 메서드 실행
 *
 * 3. 특정 벤치마크만 실행:
 *    - EventIdGenerationBenchmark만: -Djmh.benchmarks=EventIdGeneration
 *    - EventIdMemoryBenchmark만: -Djmh.benchmarks=EventIdMemory
 */
public class BenchmarkRunner {

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
            // 모든 벤치마크 클래스 포함
            .include(EventIdGenerationBenchmark.class.getSimpleName())
            .include(EventIdMemoryBenchmark.class.getSimpleName())

            // 결과 파일 출력
            .result("benchmark-results.json")
            .resultFormat(org.openjdk.jmh.results.format.ResultFormatType.JSON)

            // GC 프로파일러 활성화 (메모리 측정)
            .addProfiler("gc")

            .build();

        new Runner(opt).run();
    }
}
