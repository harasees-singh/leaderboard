package com.leaderboard.platform.controller;

import com.leaderboard.platform.dto.TopNResponse;
import com.leaderboard.platform.dto.UpdateScoreRequest;
import com.leaderboard.platform.dto.UpdateScoreResponse;
import com.leaderboard.platform.model.RankedUser;
import com.leaderboard.platform.model.UserScore;
import com.leaderboard.platform.service.LeaderboardService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/v1/leaderboards")
public class LeaderboardController {
    
    private static final Logger logger = LoggerFactory.getLogger(LeaderboardController.class);
    
    private final LeaderboardService leaderboardService;
    
    @Autowired
    public LeaderboardController(LeaderboardService leaderboardService) {
        this.leaderboardService = leaderboardService;
    }
    
    /**
     * Update user score in a leaderboard.
     * PUT /api/v1/leaderboards/{uuid}/users/{userId}
     */
    @PutMapping("/{uuid}/users/{userId}")
    public ResponseEntity<UpdateScoreResponse> updateScore(
            @PathVariable String uuid,
            @PathVariable String userId,
            @Valid @RequestBody UpdateScoreRequest request) {
        
        logger.info("Received PUT request to update score - UUID: {}, userId: {}, score: {}", 
            uuid, userId, request.getScore());
        
        try {
            UserScore userScore = leaderboardService.updateScore(uuid, userId, request.getScore());
            
            UpdateScoreResponse response = UpdateScoreResponse.builder()
                .uuid(uuid)
                .userId(userScore.getUserId())
                .score(userScore.getScore())
                .rank(userScore.getRank())
                .updatedAt(Instant.now())
                .build();
            
            logger.info("Successfully updated score - UUID: {}, userId: {}, score: {}, rank: {}", 
                uuid, userId, userScore.getScore(), userScore.getRank());
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error updating score - UUID: {}, userId: {}, score: {}, error: {}", 
                uuid, userId, request.getScore(), e.getMessage(), e);
            throw e;
        }
    }
    
    /**
     * Get top N users from a leaderboard.
     * GET /api/v1/leaderboards/{uuid}/top?limit=N
     */
    @GetMapping("/{uuid}/top")
    public ResponseEntity<TopNResponse> getTopN(
            @PathVariable String uuid,
            @RequestParam(defaultValue = "10") int limit) {
        
        logger.info("Received GET request for top N users - UUID: {}, limit: {}", uuid, limit);
        
        try {
            List<RankedUser> rankedUsers = leaderboardService.getTopN(uuid, limit);
            
            // Get total users count from service
            long totalUsers = leaderboardService.getTotalUsers(uuid);
            
            TopNResponse response = TopNResponse.builder()
                .uuid(uuid)
                .users(rankedUsers)
                .totalUsers(totalUsers)
                .retrievedAt(Instant.now())
                .build();
            
            logger.info("Successfully retrieved top {} users - UUID: {}, totalUsers: {}, returnedUsers: {}", 
                limit, uuid, totalUsers, rankedUsers.size());
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error retrieving top N users - UUID: {}, limit: {}, error: {}", 
                uuid, limit, e.getMessage(), e);
            throw e;
        }
    }
}

