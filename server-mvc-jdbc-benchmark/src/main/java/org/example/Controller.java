package org.example;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class Controller {

    @Autowired
    private BenchmarkService benchmarkService;

    /**
     * @return benchmark run duration milliseconds
     */
    @GetMapping("/benchmark")
    public Long runBenchmark() {
        return benchmarkService.runBenchmark();
    }
}
