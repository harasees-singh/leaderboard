package com.leaderboard.platform.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class RetryQueueProcessor {
    
    private static final Logger logger = LoggerFactory.getLogger(RetryQueueProcessor.class);
    
    private final LeaderboardService leaderboardService;
    
    @Autowired
    public RetryQueueProcessor(LeaderboardService leaderboardService) {
        this.leaderboardService = leaderboardService;
    }
    
    /**
     * Process retry queue every 30 seconds.
     */
    @Scheduled(fixedRate = 30000)
    public void processRetryQueue() {
        try {
            leaderboardService.processRetryQueue();
        } catch (Exception e) {
            logger.error("Error processing retry queue", e);
        }
    }
}

