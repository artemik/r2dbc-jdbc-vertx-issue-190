package org.example;

public class Application {

    public static void main(String[] args) throws InterruptedException {
        System.out.println("App started.");

        for (int i = 0; i < 50; i++) {
            BenchmarkService.runBenchmark();

            // Just wait a bit between runs.
            // When monitoring DB, it helps to distinguish each run load.
            Thread.sleep(2000);
        }
    }
}
