package com.leaderboard.platform.repository;

import com.leaderboard.platform.model.RetryQueueItem;

import java.util.List;

public interface RetryQueueRepository {
    void enqueue(RetryQueueItem item);
    List<RetryQueueItem> dequeue(int maxItems);
    void remove(RetryQueueItem item);
}

