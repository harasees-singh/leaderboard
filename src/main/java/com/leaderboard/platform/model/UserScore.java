package com.leaderboard.platform.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "user_scores", indexes = {
    @Index(name = "idx_user_score_leaderboard_user", columnList = "leaderboard_id,user_id"),
    @Index(name = "idx_user_score_timestamp", columnList = "timestamp"),
    @Index(name = "idx_user_score_leaderboard_score", columnList = "leaderboard_id,score DESC,timestamp ASC")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@IdClass(UserScoreId.class)
public class UserScore {
    @Id
    @Column(name = "user_id", nullable = false)
    private String userId;
    
    @Id
    @Column(name = "leaderboard_id", nullable = false)
    private String leaderboardId;
    
    @Column(name = "score", nullable = false)
    private Double score;
    
    @Column(name = "timestamp", nullable = false)
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC")
    private Instant timestamp;
    
    @Transient
    private Integer rank;
}

