package com.leaderboard.platform.service;

import com.leaderboard.platform.exception.InvalidRequestException;
import com.leaderboard.platform.exception.LeaderboardNotFoundException;
import com.leaderboard.platform.model.Leaderboard;
import com.leaderboard.platform.model.LeaderboardStatus;
import com.leaderboard.platform.model.RankedUser;
import com.leaderboard.platform.model.UserScore;
import com.leaderboard.platform.repository.LeaderboardRepository;
import com.leaderboard.platform.repository.RedisRepository;
import com.leaderboard.platform.repository.RetryQueueRepository;
import com.leaderboard.platform.repository.UserScoreRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LeaderboardServiceTest {
    
    @Mock
    private LeaderboardRepository leaderboardRepository;
    
    @Mock
    private UserScoreRepository userScoreRepository;
    
    @Mock
    private RedisRepository redisRepository;
    
    @Mock
    private RetryQueueRepository retryQueueRepository;
    
    @InjectMocks
    private LeaderboardService leaderboardService;
    
    private Leaderboard testLeaderboard;
    private String testUuid = "test-uuid-123";
    private String testLeaderboardId = "leaderboard-id-456";
    private String testUserId = "user-789";
    
    @BeforeEach
    void setUp() {
        testLeaderboard = Leaderboard.builder()
            .leaderboardId(testLeaderboardId)
            .uuid(testUuid)
            .podId("pod-1")
            .name("Test Leaderboard")
            .status(LeaderboardStatus.ACTIVE)
            .createdAt(Instant.now())
            .build();
    }

    @Test
    void testGetTopN_Success() {
        // Arrange
        int limit = 10;
        when(leaderboardRepository.findByUuid(testUuid)).thenReturn(Optional.of(testLeaderboard));
        when(redisRepository.isAvailable()).thenReturn(true);
        
        List<RankedUser> rankedUsers = new ArrayList<>();
        for (int i = 0; i < limit; i++) {
            rankedUsers.add(RankedUser.builder()
                .userId("user-" + i)
                .rank(i + 1)
                .score(1000.0 - i * 10)
                .timestamp(Instant.now())
                .build());
        }
        when(redisRepository.getTopN(testLeaderboardId, limit)).thenReturn(rankedUsers);
        
        // Act
        List<RankedUser> result = leaderboardService.getTopN(testUuid, limit);
        
        // Assert
        assertNotNull(result);
        assertEquals(limit, result.size());
        verify(redisRepository).getTopN(testLeaderboardId, limit);
    }
    
    @Test
    void testUpdateScore_CreateNewScore() {
        // Arrange - Test creating a new score for a user who doesn't exist yet
        Double newScore = 1500.5;
        when(leaderboardRepository.findByUuid(testUuid)).thenReturn(Optional.of(testLeaderboard));
        when(redisRepository.isAvailable()).thenReturn(true);
        when(redisRepository.getUserRankPosition(testLeaderboardId, testUserId)).thenReturn(42L);
        
        UserScore savedScore = UserScore.builder()
            .userId(testUserId)
            .leaderboardId(testLeaderboardId)
            .score(newScore)
            .timestamp(Instant.now())
            .build();
        when(userScoreRepository.save(any(UserScore.class))).thenReturn(savedScore);
        
        // Act
        UserScore result = leaderboardService.updateScore(testUuid, testUserId, newScore);
        
        // Assert
        assertNotNull(result);
        assertEquals(testUserId, result.getUserId());
        assertEquals(newScore, result.getScore());
        verify(userScoreRepository).save(any(UserScore.class));
        verify(redisRepository).isAvailable();
        verify(redisRepository).updateScore(eq(testLeaderboardId), eq(testUserId), anyDouble());
        verify(redisRepository).getUserRankPosition(testLeaderboardId, testUserId);
    }
    
    @Test
    void testUpdateScore_UpdateExistingScore() {
        // Arrange - Test updating an existing score with a different value
        Double newScore = 2500.75;
        Instant currentTimestamp = Instant.now();
        
        when(leaderboardRepository.findByUuid(testUuid)).thenReturn(Optional.of(testLeaderboard));
        when(redisRepository.isAvailable()).thenReturn(true);
        when(redisRepository.getUserRankPosition(testLeaderboardId, testUserId)).thenReturn(15L);
        
        UserScore updatedScore = UserScore.builder()
            .userId(testUserId)
            .leaderboardId(testLeaderboardId)
            .score(newScore)
            .timestamp(currentTimestamp) // Timestamp should be current time (not preserved)
            .build();
        when(userScoreRepository.save(any(UserScore.class))).thenAnswer(invocation -> {
            UserScore saved = invocation.getArgument(0);
            return updatedScore;
        });
        
        // Act
        UserScore result = leaderboardService.updateScore(testUuid, testUserId, newScore);
        
        // Assert
        assertNotNull(result);
        assertEquals(testUserId, result.getUserId());
        assertEquals(newScore, result.getScore()); // New score should be set
        verify(userScoreRepository).save(any(UserScore.class));
        verify(redisRepository).isAvailable();
        verify(redisRepository).updateScore(eq(testLeaderboardId), eq(testUserId), anyDouble());
        verify(redisRepository).getUserRankPosition(testLeaderboardId, testUserId);
    }
    
    @Test
    void testUpdateScore_LeaderboardNotFound() {
        // Arrange
        when(leaderboardRepository.findByUuid(testUuid)).thenReturn(Optional.empty());
        
        // Act & Assert
        assertThrows(LeaderboardNotFoundException.class, () -> {
            leaderboardService.updateScore(testUuid, testUserId, 1000.0);
        });
    }
    
    @Test
    void testUpdateScore_InvalidInputs() {
        // Test null UUID
        assertThrows(InvalidRequestException.class, () -> {
            leaderboardService.updateScore(null, testUserId, 1000.0);
        });
        
        // Test null userId
        assertThrows(InvalidRequestException.class, () -> {
            leaderboardService.updateScore(testUuid, null, 1000.0);
        });
        
        // Test null score
        assertThrows(InvalidRequestException.class, () -> {
            leaderboardService.updateScore(testUuid, testUserId, null);
        });
        
        // Test negative score
        assertThrows(InvalidRequestException.class, () -> {
            leaderboardService.updateScore(testUuid, testUserId, -100.0);
        });
    }
    
    @Test
    void testUpdateScore_RedisUnavailable_QueuesForRetry() {
        // Arrange
        Double score = 1500.5;
        when(leaderboardRepository.findByUuid(testUuid)).thenReturn(Optional.of(testLeaderboard));
        when(redisRepository.isAvailable()).thenReturn(false);
        
        UserScore savedScore = UserScore.builder()
            .userId(testUserId)
            .leaderboardId(testLeaderboardId)
            .score(score)
            .timestamp(Instant.now())
            .build();
        when(userScoreRepository.save(any(UserScore.class))).thenReturn(savedScore);
        
        // Act
        UserScore result = leaderboardService.updateScore(testUuid, testUserId, score);
        
        // Assert
        assertNotNull(result);
        verify(userScoreRepository).save(any(UserScore.class));
        verify(retryQueueRepository).enqueue(any());
        verify(redisRepository, never()).updateScore(anyString(), anyString(), anyDouble());
    }

    
    @Test
    void testGetTopN_LeaderboardNotFound() {
        // Arrange
        when(leaderboardRepository.findByUuid(testUuid)).thenReturn(Optional.empty());
        
        // Act & Assert
        assertThrows(LeaderboardNotFoundException.class, () -> {
            leaderboardService.getTopN(testUuid, 10);
        });
    }
    
    @Test
    void testGetTopN_InvalidLimit() {
        // Test zero limit - validation happens before repository call
        assertThrows(InvalidRequestException.class, () -> {
            leaderboardService.getTopN(testUuid, 0);
        });
        
        // Test negative limit - validation happens before repository call
        assertThrows(InvalidRequestException.class, () -> {
            leaderboardService.getTopN(testUuid, -1);
        });
        
        // Test limit exceeding max (10 million) - validation happens before repository call
        assertThrows(InvalidRequestException.class, () -> {
            leaderboardService.getTopN(testUuid, 10_000_001);
        });
    }
    
    @Test
    void testGetTopN_RedisUnavailable_FallbackToStorage() {
        // Arrange
        int limit = 5;
        when(leaderboardRepository.findByUuid(testUuid)).thenReturn(Optional.of(testLeaderboard));
        when(redisRepository.isAvailable()).thenReturn(false);
        
        List<UserScore> userScores = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            userScores.add(UserScore.builder()
                .userId("user-" + i)
                .leaderboardId(testLeaderboardId)
                .score(1000.0 - i * 10)
                .timestamp(Instant.now().minusSeconds(i))
                .build());
        }
        when(userScoreRepository.findByLeaderboardId(testLeaderboardId)).thenReturn(userScores);
        
        // Act
        List<RankedUser> result = leaderboardService.getTopN(testUuid, limit);
        
        // Assert
        assertNotNull(result);
        assertTrue(result.size() <= limit);
        verify(userScoreRepository).findByLeaderboardId(testLeaderboardId);
    }
    
    @Test
    void testGetTopN_TieBreaking() {
        // Arrange
        int limit = 3;
        when(leaderboardRepository.findByUuid(testUuid)).thenReturn(Optional.of(testLeaderboard));
        when(redisRepository.isAvailable()).thenReturn(false);
        
        Instant baseTime = Instant.now();
        List<UserScore> userScores = new ArrayList<>();
        // User A: score 1000, timestamp earlier
        userScores.add(UserScore.builder()
            .userId("user-A")
            .leaderboardId(testLeaderboardId)
            .score(1000.0)
            .timestamp(baseTime.minusSeconds(10))
            .build());
        // User B: score 1000, timestamp later
        userScores.add(UserScore.builder()
            .userId("user-B")
            .leaderboardId(testLeaderboardId)
            .score(1000.0)
            .timestamp(baseTime)
            .build());
        // User C: score 1500, should rank first
        userScores.add(UserScore.builder()
            .userId("user-C")
            .leaderboardId(testLeaderboardId)
            .score(1500.0)
            .timestamp(baseTime)
            .build());
        
        when(userScoreRepository.findByLeaderboardId(testLeaderboardId)).thenReturn(userScores);
        
        // Act
        List<RankedUser> result = leaderboardService.getTopN(testUuid, limit);
        
        // Assert
        assertEquals(3, result.size());
        // User C should rank first (highest score)
        assertEquals("user-C", result.get(0).getUserId());
        // User A should rank second (same score, earlier timestamp)
        assertEquals("user-A", result.get(1).getUserId());
        // User B should rank third (same score, later timestamp)
        assertEquals("user-B", result.get(2).getUserId());
    }
}

