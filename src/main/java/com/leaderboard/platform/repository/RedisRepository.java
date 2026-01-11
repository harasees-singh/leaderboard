package com.leaderboard.platform.repository;

import com.leaderboard.platform.model.RankedUser;

import java.util.List;
import java.util.Optional;

public interface RedisRepository {
    void updateScore(String leaderboardId, String userId, double compositeScore);
    Optional<RankedUser> getUserRank(String leaderboardId, String userId);
    List<RankedUser> getTopN(String leaderboardId, int limit);
    Long getUserRankPosition(String leaderboardId, String userId);
    Long getTotalUsers(String leaderboardId);
    boolean isAvailable();
    void initializeLeaderboard(String leaderboardId);
}

