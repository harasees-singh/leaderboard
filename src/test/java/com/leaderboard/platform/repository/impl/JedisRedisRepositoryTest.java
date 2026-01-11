package com.leaderboard.platform.repository.impl;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class JedisRedisRepositoryTest {
    
    @Test
    void testCalculateCompositeScore() {
        // Test that higher scores result in higher composite scores
        double score1 = 1000.0;
        double score2 = 2000.0;
        Instant timestamp = Instant.now();
        
        double composite1 = JedisRedisRepository.calculateCompositeScore(score1, timestamp);
        double composite2 = JedisRedisRepository.calculateCompositeScore(score2, timestamp);
        
        assertTrue(composite2 > composite1, "Higher score should result in higher composite score");
    }
    
    @Test
    void testCalculateCompositeScore_TieBreaking() {
        // Test that for equal scores, earlier timestamps result in higher composite scores
        double score = 1000.0;
        Instant earlier = Instant.now().minusSeconds(10);
        Instant later = Instant.now();
        
        double compositeEarlier = JedisRedisRepository.calculateCompositeScore(score, earlier);
        double compositeLater = JedisRedisRepository.calculateCompositeScore(score, later);
        
        assertTrue(compositeEarlier > compositeLater, 
            "Earlier timestamp should result in higher composite score for equal scores");
    }
    
    @Test
    void testCalculateCompositeScore_NullTimestamp() {
        // Test that null timestamp is handled (should use current time)
        double score = 1000.0;
        
        double composite1 = JedisRedisRepository.calculateCompositeScore(score, null);
        double composite2 = JedisRedisRepository.calculateCompositeScore(score, Instant.now());
        
        // Both should be valid composite scores
        assertTrue(composite1 > 0);
        assertTrue(composite2 > 0);
    }
}

