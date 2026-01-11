package com.leaderboard.platform.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RetryQueueItem {
    private String leaderboardId;
    private String userId;
    private Double score;
    private Instant timestamp;
    private Instant createdAt;
    private Integer retryCount;
}

