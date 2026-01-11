package com.leaderboard.platform.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.leaderboard.platform.model.RankedUser;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TopNResponse {
    private String uuid;
    private List<RankedUser> users;
    private Long totalUsers;
    
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC")
    private Instant retrievedAt;
}

