package com.leaderboard.platform.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateScoreRequest {
    @NotNull(message = "Score cannot be null")
    @Min(value = 0, message = "Score cannot be negative")
    private Double score;
}

