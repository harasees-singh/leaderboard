package com.leaderboard.platform.repository;

import com.leaderboard.platform.model.Leaderboard;

import java.util.Optional;

public interface LeaderboardRepository {
    Leaderboard save(Leaderboard leaderboard);
    Optional<Leaderboard> findByUuid(String uuid);
    Optional<Leaderboard> findByLeaderboardId(String leaderboardId);
    boolean existsByUuid(String uuid);
}

