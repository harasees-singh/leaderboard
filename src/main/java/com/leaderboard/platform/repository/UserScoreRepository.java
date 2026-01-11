package com.leaderboard.platform.repository;

import com.leaderboard.platform.model.UserScore;

import java.util.List;
import java.util.Optional;

public interface UserScoreRepository {
    UserScore save(UserScore userScore);
    Optional<UserScore> findByLeaderboardIdAndUserId(String leaderboardId, String userId);
    List<UserScore> findByLeaderboardId(String leaderboardId);
}

