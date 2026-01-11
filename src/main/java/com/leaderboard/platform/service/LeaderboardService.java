package com.leaderboard.platform.service;

import com.leaderboard.platform.model.Leaderboard;
import com.leaderboard.platform.model.RankedUser;
import com.leaderboard.platform.model.RetryQueueItem;
import com.leaderboard.platform.model.UserScore;
import com.leaderboard.platform.exception.InvalidRequestException;
import com.leaderboard.platform.exception.LeaderboardNotFoundException;
import com.leaderboard.platform.repository.LeaderboardRepository;
import com.leaderboard.platform.repository.RedisRepository;
import com.leaderboard.platform.repository.RetryQueueRepository;
import com.leaderboard.platform.repository.UserScoreRepository;
import com.leaderboard.platform.repository.impl.JedisRedisRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
public class LeaderboardService {
    
    private static final Logger logger = LoggerFactory.getLogger(LeaderboardService.class);
    
    private final LeaderboardRepository leaderboardRepository;
    private final UserScoreRepository userScoreRepository;
    private final RedisRepository redisRepository;
    private final RetryQueueRepository retryQueueRepository;
    
    @Autowired
    public LeaderboardService(
            LeaderboardRepository leaderboardRepository,
            UserScoreRepository userScoreRepository,
            RedisRepository redisRepository,
            RetryQueueRepository retryQueueRepository) {
        this.leaderboardRepository = leaderboardRepository;
        this.userScoreRepository = userScoreRepository;
        this.redisRepository = redisRepository;
        this.retryQueueRepository = retryQueueRepository;
    }
    
    /**
     * Update user score in a leaderboard.
     * Writes to persistent storage first, then updates Redis (best effort).
     * If Redis update fails, queues the update for retry.
     */
    public UserScore updateScore(String uuid, String userId, Double score) {
        validateUpdateScoreRequest(uuid, userId, score);
        Leaderboard leaderboard = findActiveLeaderboard(uuid);
        UserScore userScore = persistScoreUpdate(leaderboard.getLeaderboardId(), userId, score);
        updateRedisOrQueueForRetry(leaderboard.getLeaderboardId(), userId, score, userScore);
        return userScore;
    }
    
    private void validateUpdateScoreRequest(String uuid, String userId, Double score) {
        if (uuid == null || uuid.trim().isEmpty()) {
            throw new InvalidRequestException("UUID cannot be null or empty");
        }
        if (userId == null || userId.trim().isEmpty()) {
            throw new InvalidRequestException("UserId cannot be null or empty");
        }
        if (score == null) {
            throw new InvalidRequestException("Score cannot be null");
        }
        if (score < 0) {
            throw new InvalidRequestException("Score cannot be negative");
        }
    }
    
    private Leaderboard findActiveLeaderboard(String uuid) {
        Leaderboard leaderboard = leaderboardRepository.findByUuid(uuid)
            .orElseThrow(() -> new LeaderboardNotFoundException("Leaderboard not found with UUID: " + uuid));
        
        if (leaderboard.getStatus() != com.leaderboard.platform.model.LeaderboardStatus.ACTIVE) {
            throw new InvalidRequestException("Leaderboard is not active");
        }
        
        return leaderboard;
    }
    
    private UserScore persistScoreUpdate(String leaderboardId, String userId, Double score) {
        try {
            UserScore userScore = UserScore.builder()
                .userId(userId)
                .leaderboardId(leaderboardId)
                .score(score)
                .timestamp(Instant.now())
                .build();

            userScore = userScoreRepository.save(userScore);
            logger.info("Successfully persisted score update for user {} in leaderboard {}", userId, leaderboardId);
            return userScore;
        } catch (Exception e) {
            logger.error("Failed to persist score update to storage", e);
            throw new RuntimeException("Failed to update score: " + e.getMessage(), e);
        }
    }
    
    private void updateRedisOrQueueForRetry(String leaderboardId, String userId, Double score, UserScore userScore) {
        if (!redisRepository.isAvailable()) {
            logger.warn("Redis is not available, queueing update for retry");
            calculateAndPersistRankFromStorage(leaderboardId, userId, userScore);
            queueRedisUpdate(leaderboardId, userId, score, userScore.getTimestamp());
            return;
        }
        
        try {
            updateRedisScore(leaderboardId, userId, score, userScore);
            logger.info("Successfully updated Redis for user {} in leaderboard {}", userId, leaderboardId);
        } catch (Exception e) {
            logger.error("Failed to update Redis, queueing for retry", e);
            calculateAndPersistRankFromStorage(leaderboardId, userId, userScore);
            queueRedisUpdate(leaderboardId, userId, score, userScore.getTimestamp());
        }
    }
    
    private void updateRedisScore(String leaderboardId, String userId, Double score, UserScore userScore) {
        double compositeScore = JedisRedisRepository.calculateCompositeScore(score, userScore.getTimestamp());
        redisRepository.updateScore(leaderboardId, userId, compositeScore);
        
        Long rank = redisRepository.getUserRankPosition(leaderboardId, userId);
        if (rank != null) {
            userScore.setRank(rank.intValue());
            persistRankUpdate(leaderboardId, userId, rank.intValue());
        } else {
            // Fallback to calculating from storage if Redis doesn't have it
            calculateAndPersistRankFromStorage(leaderboardId, userId, userScore);
        }
    }
    
    private void calculateAndPersistRankFromStorage(String leaderboardId, String userId, UserScore userScore) {
        try {
            int rank = calculateRankFromStorage(leaderboardId, userId, userScore);
            userScore.setRank(rank);
            persistRankUpdate(leaderboardId, userId, rank);
        } catch (Exception e) {
            logger.warn("Failed to calculate rank from storage for user {} in leaderboard {}", userId, leaderboardId, e);
        }
    }
    
    private int calculateRankFromStorage(String leaderboardId, String userId, UserScore userScore) {
        List<UserScore> allScores = userScoreRepository.findByLeaderboardId(leaderboardId);
        List<UserScore> sortedScores = sortScores(allScores);
        
        for (int i = 0; i < sortedScores.size(); i++) {
            if (sortedScores.get(i).getUserId().equals(userId)) {
                return i + 1;
            }
        }
        
        return sortedScores.size() + 1;
    }
    
    private List<UserScore> sortScores(List<UserScore> userScores) {
        return userScores.stream()
            .sorted(this::compareScoresForRanking)
            .toList();
    }
    
    private void persistRankUpdate(String leaderboardId, String userId, int rank) {
        // Rank is not persisted to storage - it's calculated on-the-fly
        // This method is kept for API response purposes only
        // The rank is set on the UserScore object for the response, but not saved
    }
    
    /**
     * Get top N users from a leaderboard.
     * Reads from Redis if available, otherwise falls back to persistent storage.
     */
    public List<RankedUser> getTopN(String uuid, int limit) {
        validateGetTopNRequest(uuid, limit);
        Leaderboard leaderboard = findLeaderboardByUuid(uuid);
        String leaderboardId = leaderboard.getLeaderboardId();
        
        List<RankedUser> result = tryGetTopNFromRedis(leaderboardId, limit);
        if (result != null) {
            return result;
        }
        
        return getTopNFromStorage(leaderboardId, limit);
    }
    
    private void validateGetTopNRequest(String uuid, int limit) {
        if (uuid == null || uuid.trim().isEmpty()) {
            throw new InvalidRequestException("UUID cannot be null or empty");
        }
        if (limit <= 0) {
            throw new InvalidRequestException("Limit must be greater than 0");
        }
        if (limit > 10_000_000) {
            throw new InvalidRequestException("Limit cannot exceed 10,000,000");
        }
    }
    
    private Leaderboard findLeaderboardByUuid(String uuid) {
        return leaderboardRepository.findByUuid(uuid)
            .orElseThrow(() -> new LeaderboardNotFoundException("Leaderboard not found with UUID: " + uuid));
    }
    
    private List<RankedUser> tryGetTopNFromRedis(String leaderboardId, int limit) {
        if (!redisRepository.isAvailable()) {
            return null;
        }
        
        try {
            List<RankedUser> topN = redisRepository.getTopN(leaderboardId, limit);
            logger.debug("Retrieved top {} users from Redis for leaderboard {} - found {} users", 
                limit, leaderboardId, topN.size());
            return topN;
        } catch (Exception e) {
            logger.warn("Failed to retrieve from Redis, falling back to persistent storage", e);
            return null;
        }
    }
    
    private List<RankedUser> getTopNFromStorage(String leaderboardId, int limit) {
        logger.debug("Retrieving top {} users from persistent storage for leaderboard {}", limit, leaderboardId);
        List<UserScore> userScores = userScoreRepository.findByLeaderboardId(leaderboardId);
        
        if (userScores.isEmpty()) {
            logger.debug("No users found in leaderboard {}", leaderboardId);
            return new java.util.ArrayList<>();
        }
        
        List<UserScore> sortedScores = sortAndLimitScores(userScores, limit);
        List<RankedUser> rankedUsers = convertToRankedUsers(sortedScores);
        
        logger.debug("Retrieved {} users from persistent storage (requested limit: {})", 
            rankedUsers.size(), limit);
        
        return rankedUsers;
    }
    
    private List<UserScore> sortAndLimitScores(List<UserScore> userScores, int limit) {
        return userScores.stream()
            .sorted(this::compareScoresForRanking)
            .limit(limit)
            .toList();
    }
    
    private int compareScoresForRanking(UserScore a, UserScore b) {
        int scoreCompare = Double.compare(b.getScore(), a.getScore());
        if (scoreCompare != 0) {
            return scoreCompare;
        }
        // For equal scores, earlier timestamp ranks higher
        if (a.getTimestamp() != null && b.getTimestamp() != null) {
            return a.getTimestamp().compareTo(b.getTimestamp());
        }
        return 0;
    }
    
    private List<RankedUser> convertToRankedUsers(List<UserScore> sortedScores) {
        List<RankedUser> rankedUsers = new java.util.ArrayList<>();
        for (int i = 0; i < sortedScores.size(); i++) {
            UserScore userScore = sortedScores.get(i);
            rankedUsers.add(RankedUser.builder()
                .userId(userScore.getUserId())
                .rank(i + 1)
                .score(userScore.getScore())
                .timestamp(userScore.getTimestamp())
                .build());
        }
        return rankedUsers;
    }
    
    /**
     * Queue a Redis update for retry.
     */
    private void queueRedisUpdate(String leaderboardId, String userId, Double score, Instant timestamp) {
        try {
            RetryQueueItem item = RetryQueueItem.builder()
                .leaderboardId(leaderboardId)
                .userId(userId)
                .score(score)
                .timestamp(timestamp)
                .createdAt(Instant.now())
                .retryCount(0)
                .build();
            
            retryQueueRepository.enqueue(item);
            logger.info("Queued Redis update for retry: leaderboardId={}, userId={}", leaderboardId, userId);
        } catch (Exception e) {
            logger.error("Failed to queue Redis update for retry", e);
        }
    }
    
    /**
     * Get total number of users in a leaderboard.
     */
    public long getTotalUsers(String uuid) {
        if (uuid == null || uuid.trim().isEmpty()) {
            return 0L;
        }
        
        Leaderboard leaderboard = leaderboardRepository.findByUuid(uuid)
            .orElse(null);
        
        if (leaderboard == null) {
            return 0L;
        }
        
        String leaderboardId = leaderboard.getLeaderboardId();
        
        // Try to get from Redis first
        if (redisRepository.isAvailable()) {
            try {
                Long total = redisRepository.getTotalUsers(leaderboardId);
                if (total != null && total > 0) {
                    return total;
                }
            } catch (Exception e) {
                logger.warn("Failed to get total users from Redis, falling back to storage", e);
            }
        }
        
        // Fallback to persistent storage
        List<UserScore> userScores = userScoreRepository.findByLeaderboardId(leaderboardId);
        return userScores.size();
    }
    
    /**
     * Process retry queue items.
     * This method should be called periodically by a scheduled task.
     */
    public void processRetryQueue() {
        if (!redisRepository.isAvailable()) {
            logger.debug("Redis is not available, skipping retry queue processing");
            return;
        }
        
        List<RetryQueueItem> items = retryQueueRepository.dequeue(100);
        if (items.isEmpty()) {
            return;
        }
        
        logger.info("Processing {} items from retry queue", items.size());
        items.forEach(this::processRetryQueueItem);
    }
    
    private void processRetryQueueItem(RetryQueueItem item) {
        try {
            retryRedisUpdate(item);
            retryQueueRepository.remove(item);
            logger.info("Successfully retried Redis update: leaderboardId={}, userId={}", 
                item.getLeaderboardId(), item.getUserId());
        } catch (Exception e) {
            handleRetryFailure(item, e);
        }
    }
    
    private void retryRedisUpdate(RetryQueueItem item) {
        double compositeScore = JedisRedisRepository.calculateCompositeScore(
            item.getScore(),
            item.getTimestamp()
        );
        redisRepository.updateScore(item.getLeaderboardId(), item.getUserId(), compositeScore);
    }
    
    private void handleRetryFailure(RetryQueueItem item, Exception e) {
        logger.warn("Failed to retry Redis update, will retry later: leaderboardId={}, userId={}", 
            item.getLeaderboardId(), item.getUserId(), e);
        
        item.setRetryCount(item.getRetryCount() + 1);
        if (item.getRetryCount() < 5) {
            retryQueueRepository.enqueue(item);
        } else {
            logger.error("Max retry count exceeded for item: leaderboardId={}, userId={}", 
                item.getLeaderboardId(), item.getUserId());
        }
    }
}

