package com.leaderboard.platform.repository.impl;

import com.leaderboard.platform.model.RankedUser;
import com.leaderboard.platform.repository.RedisRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.resps.Tuple;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Repository
public class JedisRedisRepository implements RedisRepository {
    
    private static final String LEADERBOARD_KEY_PREFIX = "leaderboard:";
    private static final long MAX_TIMESTAMP = 9999999999999L; // Year 2286 in milliseconds
    private static final long SCORE_MULTIPLIER = 10_000_000_000_000_000L; // 10^16 to ensure score takes precedence
    
    private JedisPool jedisPool;
    private volatile boolean available = false;
    
    @Value("${redis.host:localhost}")
    private String redisHost;
    
    @Value("${redis.port:6379}")
    private int redisPort;
    
    @Value("${redis.password:}")
    private String redisPassword;
    
    @Value("${redis.ssl:false}")
    private boolean redisSsl;
    
    @Value("${redis.timeout:2000}")
    private int timeout;
    
    @PostConstruct
    public void init() {
        try {
            JedisPoolConfig poolConfig = new JedisPoolConfig();
            poolConfig.setMaxTotal(128);
            poolConfig.setMaxIdle(32);
            poolConfig.setMinIdle(8);
            poolConfig.setTestOnBorrow(true);
            poolConfig.setTestOnReturn(true);
            
            HostAndPort hostAndPort = new HostAndPort(redisHost, redisPort);
            
            // Configure client with SSL and password if needed
            DefaultJedisClientConfig.Builder clientConfigBuilder = DefaultJedisClientConfig.builder()
                .connectionTimeoutMillis(timeout)
                .socketTimeoutMillis(timeout);
            
            if (redisSsl) {
                clientConfigBuilder.ssl(true);
            }
            
            if (redisPassword != null && !redisPassword.isEmpty()) {
                clientConfigBuilder.password(redisPassword);
            }
            
            DefaultJedisClientConfig clientConfig = clientConfigBuilder.build();
            jedisPool = new JedisPool(poolConfig, hostAndPort, clientConfig);
            
            // Test connection
            try (Jedis jedis = jedisPool.getResource()) {
                jedis.ping();
                available = true;
                System.out.println("Successfully connected to Redis at " + redisHost + ":" + redisPort + 
                    (redisSsl ? " (SSL enabled)" : ""));
            }
        } catch (Exception e) {
            System.err.println("Failed to initialize Redis connection: " + e.getMessage());
            e.printStackTrace();
            available = false;
        }
    }
    
    @PreDestroy
    public void destroy() {
        if (jedisPool != null && !jedisPool.isClosed()) {
            jedisPool.close();
        }
    }
    
    @Override
    public boolean isAvailable() {
        if (!available) {
            return false;
        }
        
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.ping();
            return true;
        } catch (Exception e) {
            available = false;
            return false;
        }
    }
    
    @Override
    public void initializeLeaderboard(String leaderboardId) {
        if (leaderboardId == null || leaderboardId.trim().isEmpty()) {
            throw new IllegalArgumentException("LeaderboardId cannot be null or empty");
        }
        
        if (!isAvailable()) {
            throw new RuntimeException("Redis is not available");
        }
        
        String key = LEADERBOARD_KEY_PREFIX + leaderboardId;
        try (Jedis jedis = jedisPool.getResource()) {
            // Create empty sorted set if it doesn't exist
            jedis.zcard(key);
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize leaderboard in Redis", e);
        }
    }
    
    @Override
    public void updateScore(String leaderboardId, String userId, double compositeScore) {
        if (leaderboardId == null || leaderboardId.trim().isEmpty()) {
            throw new IllegalArgumentException("LeaderboardId cannot be null or empty");
        }
        if (userId == null || userId.trim().isEmpty()) {
            throw new IllegalArgumentException("UserId cannot be null or empty");
        }
        
        if (!isAvailable()) {
            throw new RuntimeException("Redis is not available");
        }
        
        String key = LEADERBOARD_KEY_PREFIX + leaderboardId;
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.zadd(key, compositeScore, userId);
        } catch (Exception e) {
            throw new RuntimeException("Failed to update score in Redis", e);
        }
    }
    
    @Override
    public Optional<RankedUser> getUserRank(String leaderboardId, String userId) {
        if (leaderboardId == null || leaderboardId.trim().isEmpty() || userId == null || userId.trim().isEmpty()) {
            return Optional.empty();
        }
        
        if (!isAvailable()) {
            return Optional.empty();
        }
        
        String key = LEADERBOARD_KEY_PREFIX + leaderboardId;
        try (Jedis jedis = jedisPool.getResource()) {
            Double compositeScore = jedis.zscore(key, userId);
            if (compositeScore == null) {
                return Optional.empty();
            }
            
            Long rank = jedis.zrevrank(key, userId);
            if (rank == null) {
                return Optional.empty();
            }
            
            // Extract original score and timestamp from composite score
            double originalScore = Math.floor(compositeScore / SCORE_MULTIPLIER);
            long timestampValue = extractTimestampFromComposite(compositeScore);
            Instant timestamp = Instant.ofEpochMilli(timestampValue);
            
            return Optional.of(RankedUser.builder()
                .userId(userId)
                .rank(rank.intValue() + 1) // Redis ranks are 0-based
                .score(originalScore)
                .timestamp(timestamp)
                .build());
        } catch (Exception e) {
            System.err.println("Failed to get user rank from Redis: " + e.getMessage());
            return Optional.empty();
        }
    }
    
    @Override
    public List<RankedUser> getTopN(String leaderboardId, int limit) {
        if (leaderboardId == null || leaderboardId.trim().isEmpty()) {
            return new ArrayList<>();
        }
        if (limit <= 0) {
            return new ArrayList<>();
        }
        
        if (!isAvailable()) {
            return new ArrayList<>();
        }
        
        String key = LEADERBOARD_KEY_PREFIX + leaderboardId;
        try (Jedis jedis = jedisPool.getResource()) {
            // Get top N with scores (descending order)
            List<Tuple> tuples = jedis.zrevrangeWithScores(key, 0, limit - 1);
            
            List<RankedUser> rankedUsers = new ArrayList<>();
            int rank = 1;
            for (Tuple tuple : tuples) {
                String userId = tuple.getElement();
                double compositeScore = tuple.getScore();
                
                // Extract original score and timestamp
                double originalScore = Math.floor(compositeScore / SCORE_MULTIPLIER);
                long timestampValue = extractTimestampFromComposite(compositeScore);
                Instant timestamp = Instant.ofEpochMilli(timestampValue);
                
                rankedUsers.add(RankedUser.builder()
                    .userId(userId)
                    .rank(rank++)
                    .score(originalScore)
                    .timestamp(timestamp)
                    .build());
            }
            
            return rankedUsers;
        } catch (Exception e) {
            System.err.println("Failed to get top N from Redis: " + e.getMessage());
            return new ArrayList<>();
        }
    }
    
    @Override
    public Long getUserRankPosition(String leaderboardId, String userId) {
        if (leaderboardId == null || leaderboardId.trim().isEmpty() || userId == null || userId.trim().isEmpty()) {
            return null;
        }
        
        if (!isAvailable()) {
            return null;
        }
        
        String key = LEADERBOARD_KEY_PREFIX + leaderboardId;
        try (Jedis jedis = jedisPool.getResource()) {
            Long rank = jedis.zrevrank(key, userId);
            return rank != null ? rank + 1 : null; // Convert to 1-based ranking
        } catch (Exception e) {
            System.err.println("Failed to get user rank position from Redis: " + e.getMessage());
            return null;
        }
    }
    
    @Override
    public Long getTotalUsers(String leaderboardId) {
        if (leaderboardId == null || leaderboardId.trim().isEmpty()) {
            return 0L;
        }
        
        if (!isAvailable()) {
            return 0L;
        }
        
        String key = LEADERBOARD_KEY_PREFIX + leaderboardId;
        try (Jedis jedis = jedisPool.getResource()) {
            return jedis.zcard(key);
        } catch (Exception e) {
            System.err.println("Failed to get total users from Redis: " + e.getMessage());
            return 0L;
        }
    }
    
    /**
     * Extract timestamp from composite score.
     * Validates and ensures the extracted timestamp is within reasonable bounds.
     */
    private long extractTimestampFromComposite(double compositeScore) {
        long remainder = (long)(compositeScore % SCORE_MULTIPLIER);
        long timestampValue = MAX_TIMESTAMP - remainder;
        
        // Validate timestamp is within reasonable bounds (year 1970 to year 2100)
        long minTimestamp = 0L; // Epoch start
        long maxTimestamp = 4102444800000L; // Year 2100 in milliseconds
        
        if (timestampValue < minTimestamp) {
            // If extracted timestamp is negative or too small, use current time
            return Instant.now().toEpochMilli();
        }
        if (timestampValue > maxTimestamp) {
            // If extracted timestamp is too large, use current time
            return Instant.now().toEpochMilli();
        }
        
        return timestampValue;
    }
    
    /**
     * Calculate composite score from original score and timestamp.
     * Higher scores rank higher, and for equal scores, earlier timestamps rank higher.
     * Formula: composite_score = (score * multiplier) + (max_timestamp - timestamp)
     */
    public static double calculateCompositeScore(double score, Instant timestamp) {
        if (timestamp == null) {
            timestamp = Instant.now();
        }
        long timestampMillis = timestamp.toEpochMilli();
        // Ensure timestamp doesn't exceed MAX_TIMESTAMP
        if (timestampMillis > MAX_TIMESTAMP) {
            timestampMillis = MAX_TIMESTAMP;
        }
        // Ensure timestamp is not negative
        if (timestampMillis < 0) {
            timestampMillis = Instant.now().toEpochMilli();
        }
        return (score * SCORE_MULTIPLIER) + (MAX_TIMESTAMP - timestampMillis);
    }
}

