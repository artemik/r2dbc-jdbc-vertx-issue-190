package org.example;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
public class Controller {

    @Autowired
    private BenchmarkService benchmarkService;

    /**
     *
     * @return benchmark run duration Mono in milliseconds
     */
    @GetMapping("/benchmark")
    public Mono<Long> runBenchmark() {
        return benchmarkService.runBenchmark();
    }
}
