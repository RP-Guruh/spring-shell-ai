package com.console.demo.runners;

import com.console.demo.services.SummaryService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.backfill", havingValue = "true")
public class VectorBackfillRunner implements CommandLineRunner {

    private final SummaryService summaryService;

    public VectorBackfillRunner(SummaryService summaryService) {
        this.summaryService = summaryService;
    }

    @Override
    public void run(String... args) {
        int total = 0;
        int n;
        while ((n = summaryService.backfillVectors(20)) > 0) {
            total += n;
            System.out.println("Embedded " + total + " summary...");
        }
        System.out.println("Backfill selesai: " + total + " summary.");
    }
}