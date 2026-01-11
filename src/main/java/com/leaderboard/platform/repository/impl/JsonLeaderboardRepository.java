package com.leaderboard.platform.repository.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.leaderboard.platform.model.Leaderboard;
import com.leaderboard.platform.repository.LeaderboardRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

@Repository
public class JsonLeaderboardRepository implements LeaderboardRepository {
    
    private final String dataDirectory;
    private final ObjectMapper objectMapper;
    private final Map<String, Leaderboard> cache = new ConcurrentHashMap<>();
    // Per-leaderboard locks to prevent race conditions on file writes
    private final Map<String, ReentrantLock> leaderboardLocks = new ConcurrentHashMap<>();
    
    public JsonLeaderboardRepository(@Value("${leaderboard.storage.leaderboards:./data/leaderboards}") String dataDirectory) {
        this.dataDirectory = dataDirectory;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        initializeDirectory();
        loadAllLeaderboards();
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
    
    private void loadAllLeaderboards() {
        try {
            Path path = Paths.get(dataDirectory);
            if (Files.exists(path)) {
                Files.list(path)
                    .filter(p -> p.toString().endsWith(".json"))
                    .forEach(this::loadLeaderboard);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to load leaderboards from directory", e);
        }
    }
    
    private void loadLeaderboard(Path filePath) {
        try {
            // Skip retry-queue.json and other non-leaderboard files
            String fileName = filePath.getFileName().toString();
            if (fileName.equals("retry-queue.json")) {
                return;
            }
            
            Leaderboard leaderboard = objectMapper.readValue(filePath.toFile(), Leaderboard.class);
            // Validate it's actually a leaderboard by checking required fields
            if (leaderboard.getUuid() == null || leaderboard.getUuid().trim().isEmpty()) {
                return; // Skip invalid leaderboard files
            }
            
            cache.put(leaderboard.getUuid(), leaderboard);
            if (leaderboard.getLeaderboardId() != null) {
                cache.put(leaderboard.getLeaderboardId(), leaderboard);
            }
        } catch (Exception e) {
            // Silently skip files that don't match Leaderboard format
            // This allows other JSON files (like retry-queue.json) to coexist
        }
    }
    
    @Override
    public Leaderboard save(Leaderboard leaderboard) {
        if (leaderboard == null) {
            throw new IllegalArgumentException("Leaderboard cannot be null");
        }
        if (leaderboard.getUuid() == null || leaderboard.getUuid().trim().isEmpty()) {
            throw new IllegalArgumentException("Leaderboard UUID cannot be null or empty");
        }
        
        String uuid = leaderboard.getUuid();
        // Get or create a lock for this specific leaderboard to prevent concurrent file writes
        ReentrantLock lock = leaderboardLocks.computeIfAbsent(uuid, k -> new ReentrantLock());
        
        lock.lock();
        try {
            String filename = uuid + ".json";
            File file = new File(dataDirectory, filename);
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(file, leaderboard);
            
            // Update cache atomically (within the lock)
            cache.put(uuid, leaderboard);
            if (leaderboard.getLeaderboardId() != null) {
                cache.put(leaderboard.getLeaderboardId(), leaderboard);
            }
            
            return leaderboard;
        } catch (IOException e) {
            throw new RuntimeException("Failed to save leaderboard to file", e);
        } finally {
            lock.unlock();
        }
    }
    
    @Override
    public Optional<Leaderboard> findByUuid(String uuid) {
        if (uuid == null || uuid.trim().isEmpty()) {
            return Optional.empty();
        }
        
        Leaderboard leaderboard = cache.get(uuid);
        if (leaderboard != null) {
            return Optional.of(leaderboard);
        }
        
        // Try to load from file if not in cache
        String filename = uuid + ".json";
        File file = new File(dataDirectory, filename);
        if (file.exists()) {
            try {
                leaderboard = objectMapper.readValue(file, Leaderboard.class);
                cache.put(uuid, leaderboard);
                if (leaderboard.getLeaderboardId() != null) {
                    cache.put(leaderboard.getLeaderboardId(), leaderboard);
                }
                return Optional.of(leaderboard);
            } catch (IOException e) {
                System.err.println("Failed to load leaderboard from file: " + file + ", error: " + e.getMessage());
            }
        }
        
        return Optional.empty();
    }
    
    @Override
    public Optional<Leaderboard> findByLeaderboardId(String leaderboardId) {
        if (leaderboardId == null || leaderboardId.trim().isEmpty()) {
            return Optional.empty();
        }
        
        Leaderboard leaderboard = cache.get(leaderboardId);
        if (leaderboard != null) {
            return Optional.of(leaderboard);
        }
        
        // Search through all files if not in cache
        try {
            Path path = Paths.get(dataDirectory);
            if (Files.exists(path)) {
                Optional<Leaderboard> found = Files.list(path)
                    .filter(p -> p.toString().endsWith(".json"))
                    .map(this::loadLeaderboardSafely)
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .filter(l -> leaderboardId.equals(l.getLeaderboardId()))
                    .findFirst();
                
                if (found.isPresent()) {
                    Leaderboard lb = found.get();
                    cache.put(lb.getUuid(), lb);
                    cache.put(lb.getLeaderboardId(), lb);
                    return found;
                }
            }
        } catch (IOException e) {
            System.err.println("Failed to search for leaderboard by ID: " + e.getMessage());
        }
        
        return Optional.empty();
    }
    
    private Optional<Leaderboard> loadLeaderboardSafely(Path filePath) {
        try {
            return Optional.of(objectMapper.readValue(filePath.toFile(), Leaderboard.class));
        } catch (IOException e) {
            return Optional.empty();
        }
    }
    
    @Override
    public boolean existsByUuid(String uuid) {
        return findByUuid(uuid).isPresent();
    }
}

