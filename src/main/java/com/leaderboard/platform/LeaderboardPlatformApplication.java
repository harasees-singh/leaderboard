package com.leaderboard.platform;

import com.leaderboard.platform.repository.impl.JsonLeaderboardRepository;
import com.leaderboard.platform.repository.impl.JsonUserScoreRepository;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

@SpringBootApplication
@ComponentScan(
    excludeFilters = @ComponentScan.Filter(
        type = FilterType.ASSIGNABLE_TYPE,
        classes = {JsonLeaderboardRepository.class, JsonUserScoreRepository.class}
    )
)
public class LeaderboardPlatformApplication {

    public static void main(String[] args) {
        SpringApplication.run(LeaderboardPlatformApplication.class, args);
    }
}

