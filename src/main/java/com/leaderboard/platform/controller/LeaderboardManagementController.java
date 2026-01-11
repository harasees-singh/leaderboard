package com.leaderboard.platform.controller;

import com.leaderboard.platform.dto.CreateLeaderboardRequest;
import com.leaderboard.platform.model.Leaderboard;
import com.leaderboard.platform.model.LeaderboardStatus;
import com.leaderboard.platform.repository.LeaderboardRepository;
import com.leaderboard.platform.repository.RedisRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Controller for leaderboard management operations.
 * This is a helper controller for creating leaderboards during development/testing.
 */
@RestController
@RequestMapping("/api/v1/leaderboards")
public class LeaderboardManagementController {
    
    private static final Logger logger = LoggerFactory.getLogger(LeaderboardManagementController.class);
    
    private final LeaderboardRepository leaderboardRepository;
    private final RedisRepository redisRepository;
    
    @Autowired
    public LeaderboardManagementController(
            LeaderboardRepository leaderboardRepository,
            RedisRepository redisRepository) {
        this.leaderboardRepository = leaderboardRepository;
        this.redisRepository = redisRepository;
    }
    
    /**
     * Create a new leaderboard.
     * POST /api/v1/leaderboards/create
     */
    @PostMapping("/create")
    public ResponseEntity<Leaderboard> createLeaderboard(@RequestBody CreateLeaderboardRequest request) {
        logger.info("Received POST request to create leaderboard - UUID: {}, podId: {}, name: {}", 
            request.getUuid(), request.getPodId(), request.getName());
        
        try {
            validateCreateRequest(request);
            checkLeaderboardExists(request.getUuid());
            Leaderboard leaderboard = buildAndSaveLeaderboard(request);
            initializeRedisLeaderboard(leaderboard);
            
            logger.info("Successfully created leaderboard - UUID: {}, leaderboardId: {}, podId: {}", 
                leaderboard.getUuid(), leaderboard.getLeaderboardId(), leaderboard.getPodId());
            
            return ResponseEntity.ok(leaderboard);
        } catch (Exception e) {
            logger.error("Error creating leaderboard - UUID: {}, podId: {}, error: {}", 
                request.getUuid(), request.getPodId(), e.getMessage(), e);
            throw e;
        }
    }
    
    private void validateCreateRequest(CreateLeaderboardRequest request) {
        if (request.getUuid() == null || request.getUuid().trim().isEmpty()) {
            throw new IllegalArgumentException("UUID cannot be null or empty");
        }
        if (request.getPodId() == null || request.getPodId().trim().isEmpty()) {
            throw new IllegalArgumentException("PodId cannot be null or empty");
        }
    }
    
    private void checkLeaderboardExists(String uuid) {
        if (leaderboardRepository.existsByUuid(uuid)) {
            logger.warn("Attempted to create leaderboard with existing UUID: {}", uuid);
            throw new IllegalArgumentException("Leaderboard with UUID " + uuid + " already exists");
        }
    }
    
    private Leaderboard buildAndSaveLeaderboard(CreateLeaderboardRequest request) {
        Leaderboard leaderboard = Leaderboard.builder()
            .leaderboardId(UUID.randomUUID().toString())
            .uuid(request.getUuid())
            .podId(request.getPodId())
            .name(request.getName() != null ? request.getName() : "Leaderboard " + request.getUuid())
            .status(LeaderboardStatus.ACTIVE)
            .createdAt(Instant.now())
            .createdBy(request.getCreatedBy() != null ? request.getCreatedBy() : "system")
            .startTime(request.getStartTime() != null ? request.getStartTime() : Instant.now())
            .endTime(request.getEndTime())
            .metadata(request.getMetadata())
            .build();
        
        return leaderboardRepository.save(leaderboard);
    }
    
    private void initializeRedisLeaderboard(Leaderboard leaderboard) {
        if (!redisRepository.isAvailable()) {
            logger.warn("Redis is not available, skipping leaderboard initialization - leaderboardId: {}", 
                leaderboard.getLeaderboardId());
            return;
        }
        
        try {
            redisRepository.initializeLeaderboard(leaderboard.getLeaderboardId());
            logger.info("Successfully initialized leaderboard in Redis - leaderboardId: {}", 
                leaderboard.getLeaderboardId());
        } catch (Exception e) {
            logger.warn("Failed to initialize leaderboard in Redis - leaderboardId: {}, error: {}", 
                leaderboard.getLeaderboardId(), e.getMessage());
        }
    }
}

