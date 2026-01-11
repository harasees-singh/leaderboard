package com.leaderboard.platform.repository.impl;

import com.leaderboard.platform.model.UserScore;
import com.leaderboard.platform.model.UserScoreId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface JpaUserScoreRepository extends JpaRepository<UserScore, UserScoreId> {
    Optional<UserScore> findByLeaderboardIdAndUserId(String leaderboardId, String userId);
    List<UserScore> findByLeaderboardIdOrderByScoreDescTimestampAsc(String leaderboardId);
}
