package com.leaderboard.platform.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserScoreId implements Serializable {
    private String userId;
    private String leaderboardId;
}
