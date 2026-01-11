package com.leaderboard.platform.repository.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.leaderboard.platform.model.RetryQueueItem;
import com.leaderboard.platform.repository.RetryQueueRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.locks.ReentrantLock;

@Repository
public class JsonRetryQueueRepository implements RetryQueueRepository {
    
    private static final String QUEUE_FILE = "retry-queue.json";
    private static final int MAX_RETRY_COUNT = 5;
    
    private final String dataDirectory;
    private final ObjectMapper objectMapper;
    private final Queue<RetryQueueItem> queue = new ConcurrentLinkedQueue<>();
    private final ReentrantLock lock = new ReentrantLock();
    
    public JsonRetryQueueRepository(@Value("${leaderboard.storage.directory:./data}") String dataDirectory) {
        this.dataDirectory = dataDirectory;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        initializeDirectory();
        loadQueue();
    }
    
    private void initializeDirectory() {
        try {
            Path path = Paths.get(dataDirectory);
            if (!Files.exists(path)) {
                Files.createDirectories(path);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to create data directory: " + dataDirectory, e);
        }
    }
    
    private void loadQueue() {
        File queueFile = new File(dataDirectory, QUEUE_FILE);
        if (queueFile.exists()) {
            try {
                List<RetryQueueItem> items = objectMapper.readValue(
                    queueFile,
                    new TypeReference<List<RetryQueueItem>>() {}
                );
                
                if (items == null) {
                    return;
                }
                
                // Filter out items that have exceeded max retry count
                items.stream()
                    .filter(item -> item != null)
                    .filter(item -> item.getRetryCount() == null || item.getRetryCount() < MAX_RETRY_COUNT)
                    .forEach(queue::offer);
            } catch (Exception e) {
                // If file doesn't exist or is invalid, start with empty queue
                System.err.println("Failed to load retry queue from file: " + e.getMessage());
            }
        }
    }
    
    private void persistQueue() {
        lock.lock();
        try {
            File queueFile = new File(dataDirectory, QUEUE_FILE);
            List<RetryQueueItem> items = new ArrayList<>(queue);
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(queueFile, items);
        } catch (IOException e) {
            System.err.println("Failed to persist retry queue to file: " + e.getMessage());
        } finally {
            lock.unlock();
        }
    }
    
    @Override
    public void enqueue(RetryQueueItem item) {
        if (item == null) {
            throw new IllegalArgumentException("RetryQueueItem cannot be null");
        }
        
        if (item.getRetryCount() == null) {
            item.setRetryCount(0);
        }
        if (item.getCreatedAt() == null) {
            item.setCreatedAt(java.time.Instant.now());
        }
        
        queue.offer(item);
        persistQueue();
    }
    
    @Override
    public List<RetryQueueItem> dequeue(int maxItems) {
        if (maxItems <= 0) {
            return Collections.emptyList();
        }
        
        List<RetryQueueItem> items = new ArrayList<>();
        for (int i = 0; i < maxItems && !queue.isEmpty(); i++) {
            RetryQueueItem item = queue.poll();
            if (item != null) {
                // Skip items that have exceeded max retry count
                if (item.getRetryCount() != null && item.getRetryCount() >= MAX_RETRY_COUNT) {
                    continue;
                }
                items.add(item);
            }
        }
        
        if (!items.isEmpty()) {
            persistQueue();
        }
        
        return items;
    }
    
    @Override
    public void remove(RetryQueueItem item) {
        if (item == null) {
            return;
        }
        
        queue.remove(item);
        persistQueue();
    }
    
    public int size() {
        return queue.size();
    }
}

