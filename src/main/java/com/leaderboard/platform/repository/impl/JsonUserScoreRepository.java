package com.leaderboard.platform.repository.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.leaderboard.platform.model.UserScore;
import com.leaderboard.platform.repository.UserScoreRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

@Repository
public class JsonUserScoreRepository implements UserScoreRepository {
    
    private final String dataDirectory;
    private final ObjectMapper objectMapper;
    private final Map<String, Map<String, UserScore>> cache = new ConcurrentHashMap<>();
    // Per-leaderboard locks to prevent race conditions on file writes
    private final Map<String, ReentrantLock> leaderboardLocks = new ConcurrentHashMap<>();
    
    public JsonUserScoreRepository(@Value("${leaderboard.storage.userScores:./data/user-scores}") String dataDirectory) {
        this.dataDirectory = dataDirectory;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        initializeDirectory();
        loadAllUserScores();
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
    
    private void loadAllUserScores() {
        try {
            Path path = Paths.get(dataDirectory);
            if (Files.exists(path)) {
                Files.list(path)
                    .filter(p -> p.toString().endsWith(".json"))
                    .forEach(this::loadUserScores);
            }
        } catch (IOException e) {
            System.err.println("Failed to load user scores from directory: " + e.getMessage());
        }
    }
    
    private void loadUserScores(Path filePath) {
        try {
            // Skip retry-queue.json and other non-user-score files
            String fileName = filePath.getFileName().toString();
            if (fileName.equals("retry-queue.json")) {
                return;
            }
            
            String leaderboardId = filePath.getFileName().toString().replace(".json", "");
            List<UserScore> scores = objectMapper.readValue(
                filePath.toFile(),
                new TypeReference<List<UserScore>>() {}
            );
            
            // Validate it's actually a list of user scores
            if (scores == null || scores.isEmpty()) {
                return;
            }
            
            // Check if first item has required UserScore fields (not Leaderboard fields)
            UserScore first = scores.get(0);
            if (first.getUserId() == null || first.getLeaderboardId() == null) {
                return; // Skip files that don't match UserScore format
            }
            
            Map<String, UserScore> scoreMap = scores.stream()
                .collect(Collectors.toMap(
                    UserScore::getUserId,
                    score -> score,
                    (existing, replacement) -> replacement
                ));
            
            cache.put(leaderboardId, scoreMap);
        } catch (Exception e) {
            // Silently skip files that don't match UserScore format
            // This allows other JSON files to coexist
        }
    }
    
    @Override
    public UserScore save(UserScore userScore) {
        validateUserScore(userScore);
        String leaderboardId = userScore.getLeaderboardId();
        ReentrantLock lock = getOrCreateLock(leaderboardId);
        
        lock.lock();
        try {
            UserScore scoreWithTimestamp = ensureTimestamp(userScore);
            updateCacheAndPersist(leaderboardId, scoreWithTimestamp);
            return scoreWithTimestamp;
        } catch (IOException e) {
            throw new RuntimeException("Failed to save user score to file", e);
        } finally {
            lock.unlock();
        }
    }
    
    private void validateUserScore(UserScore userScore) {
        if (userScore == null) {
            throw new IllegalArgumentException("UserScore cannot be null");
        }
        if (userScore.getLeaderboardId() == null || userScore.getLeaderboardId().trim().isEmpty()) {
            throw new IllegalArgumentException("LeaderboardId cannot be null or empty");
        }
        if (userScore.getUserId() == null || userScore.getUserId().trim().isEmpty()) {
            throw new IllegalArgumentException("UserId cannot be null or empty");
        }
        if (userScore.getScore() == null) {
            throw new IllegalArgumentException("Score cannot be null");
        }
    }
    
    private ReentrantLock getOrCreateLock(String leaderboardId) {
        return leaderboardLocks.computeIfAbsent(leaderboardId, k -> new ReentrantLock());
    }
    
    private UserScore ensureTimestamp(UserScore userScore) {
        if (userScore.getTimestamp() != null) {
            // Remove rank before persisting
            return UserScore.builder()
                .userId(userScore.getUserId())
                .leaderboardId(userScore.getLeaderboardId())
                .score(userScore.getScore())
                .timestamp(userScore.getTimestamp())
                .build();
        }
        
        return UserScore.builder()
            .userId(userScore.getUserId())
            .leaderboardId(userScore.getLeaderboardId())
            .score(userScore.getScore())
            .timestamp(Instant.now())
            .build();
    }
    
    private void updateCacheAndPersist(String leaderboardId, UserScore userScore) throws IOException {
        Map<String, UserScore> scoreMap = cache.computeIfAbsent(leaderboardId, k -> new ConcurrentHashMap<>());
        scoreMap.put(userScore.getUserId(), userScore);
        persistToFile(leaderboardId, scoreMap);
    }
    
    private void persistToFile(String leaderboardId, Map<String, UserScore> scoreMap) throws IOException {
        String filename = leaderboardId + ".json";
        File file = new File(dataDirectory, filename);
        List<UserScore> allScores = new ArrayList<>(scoreMap.values());
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(file, allScores);
    }
    
    @Override
    public Optional<UserScore> findByLeaderboardIdAndUserId(String leaderboardId, String userId) {
        if (!isValidId(leaderboardId) || !isValidId(userId)) {
            return Optional.empty();
        }
        
        Optional<UserScore> fromCache = findFromCache(leaderboardId, userId);
        if (fromCache.isPresent()) {
            return fromCache;
        }
        
        return loadFromFileAndFind(leaderboardId, userId);
    }
    
    private boolean isValidId(String id) {
        return id != null && !id.trim().isEmpty();
    }
    
    private Optional<UserScore> findFromCache(String leaderboardId, String userId) {
        Map<String, UserScore> scoreMap = cache.get(leaderboardId);
        if (scoreMap != null) {
            return Optional.ofNullable(scoreMap.get(userId));
        }
        return Optional.empty();
    }
    
    private Optional<UserScore> loadFromFileAndFind(String leaderboardId, String userId) {
        File file = getLeaderboardFile(leaderboardId);
        if (!file.exists()) {
            return Optional.empty();
        }
        
        try {
            Map<String, UserScore> loadedMap = loadScoresFromFile(file);
            cache.put(leaderboardId, loadedMap);
            return Optional.ofNullable(loadedMap.get(userId));
        } catch (IOException e) {
            System.err.println("Failed to load user scores from file: " + file + ", error: " + e.getMessage());
            return Optional.empty();
        }
    }
    
    private File getLeaderboardFile(String leaderboardId) {
        String filename = leaderboardId + ".json";
        return new File(dataDirectory, filename);
    }
    
    private Map<String, UserScore> loadScoresFromFile(File file) throws IOException {
        List<UserScore> scores = objectMapper.readValue(
            file,
            new TypeReference<List<UserScore>>() {}
        );
        
        return scores.stream()
            .collect(Collectors.toMap(
                UserScore::getUserId,
                score -> score,
                (existing, replacement) -> replacement
            ));
    }
    
    @Override
    public List<UserScore> findByLeaderboardId(String leaderboardId) {
        if (leaderboardId == null || leaderboardId.trim().isEmpty()) {
            return Collections.emptyList();
        }
        
        List<UserScore> fromCache = findFromCacheWithLock(leaderboardId);
        if (fromCache != null) {
            return fromCache;
        }
        
        return loadFromFileAndCache(leaderboardId);
    }
    
    private List<UserScore> findFromCacheWithLock(String leaderboardId) {
        ReentrantLock lock = leaderboardLocks.get(leaderboardId);
        if (lock != null) {
            return findFromCacheWithLock(leaderboardId, lock);
        }
        
        return findFromCacheWithoutLock(leaderboardId);
    }
    
    private List<UserScore> findFromCacheWithLock(String leaderboardId, ReentrantLock lock) {
        lock.lock();
        try {
            return createDefensiveCopy(leaderboardId);
        } finally {
            lock.unlock();
        }
    }
    
    private List<UserScore> findFromCacheWithoutLock(String leaderboardId) {
        return createDefensiveCopy(leaderboardId);
    }
    
    private List<UserScore> createDefensiveCopy(String leaderboardId) {
        Map<String, UserScore> scoreMap = cache.get(leaderboardId);
        if (scoreMap == null) {
            return null;
        }
        
        return scoreMap.values().stream()
            .map(this::copyUserScore)
            .toList();
    }
    
    private UserScore copyUserScore(UserScore score) {
        // Don't copy rank - it's not stored in persistent storage
        return UserScore.builder()
            .userId(score.getUserId())
            .leaderboardId(score.getLeaderboardId())
            .score(score.getScore())
            .timestamp(score.getTimestamp())
            .build();
    }
    
    private List<UserScore> loadFromFileAndCache(String leaderboardId) {
        File file = getLeaderboardFile(leaderboardId);
        if (!file.exists()) {
            return Collections.emptyList();
        }
        
        try {
            Map<String, UserScore> loadedMap = loadScoresFromFile(file);
            cacheWithLock(leaderboardId, loadedMap);
            return loadedMap.values().stream()
                .map(this::copyUserScore)
                .toList();
        } catch (IOException e) {
            System.err.println("Failed to load user scores from file: " + file + ", error: " + e.getMessage());
            return Collections.emptyList();
        }
    }
    
    private void cacheWithLock(String leaderboardId, Map<String, UserScore> loadedMap) {
        ReentrantLock writeLock = leaderboardLocks.computeIfAbsent(leaderboardId, k -> new ReentrantLock());
        writeLock.lock();
        try {
            cache.put(leaderboardId, loadedMap);
        } finally {
            writeLock.unlock();
        }
    }
}

