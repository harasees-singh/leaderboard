package com.leaderboard.platform.repository.impl;

import com.leaderboard.platform.model.Leaderboard;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface JpaLeaderboardRepository extends JpaRepository<Leaderboard, String> {
    Optional<Leaderboard> findByUuid(String uuid);
}
