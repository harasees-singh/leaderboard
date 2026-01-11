package com.leaderboard.platform.repository.impl;

import com.leaderboard.platform.model.UserScore;
import com.leaderboard.platform.repository.UserScoreRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@Primary
public class JpaUserScoreRepositoryImpl implements UserScoreRepository {
    
    private final JpaUserScoreRepository jpaRepository;
    
    @Autowired
    public JpaUserScoreRepositoryImpl(JpaUserScoreRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }
    
    @Override
    public UserScore save(UserScore userScore) {
        return jpaRepository.save(userScore);
    }
    
    @Override
    public Optional<UserScore> findByLeaderboardIdAndUserId(String leaderboardId, String userId) {
        return jpaRepository.findByLeaderboardIdAndUserId(leaderboardId, userId);
    }
    
    @Override
    public List<UserScore> findByLeaderboardId(String leaderboardId) {
        return jpaRepository.findByLeaderboardIdOrderByScoreDescTimestampAsc(leaderboardId);
    }
}
