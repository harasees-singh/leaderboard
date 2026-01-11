package com.leaderboard.platform.repository.impl;

import com.leaderboard.platform.model.Leaderboard;
import com.leaderboard.platform.repository.LeaderboardRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@Primary
public class JpaLeaderboardRepositoryImpl implements LeaderboardRepository {
    
    private final JpaLeaderboardRepository jpaRepository;
    
    @Autowired
    public JpaLeaderboardRepositoryImpl(JpaLeaderboardRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }
    
    @Override
    public Leaderboard save(Leaderboard leaderboard) {
        return jpaRepository.save(leaderboard);
    }
    
    @Override
    public Optional<Leaderboard> findByUuid(String uuid) {
        return jpaRepository.findByUuid(uuid);
    }
    
    @Override
    public Optional<Leaderboard> findByLeaderboardId(String leaderboardId) {
        return jpaRepository.findById(leaderboardId);
    }
    
    @Override
    public boolean existsByUuid(String uuid) {
        return jpaRepository.findByUuid(uuid).isPresent();
    }
}
