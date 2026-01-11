package com.leaderboard.platform.exception;

public class LeaderboardNotFoundException extends LeaderboardException {
    public LeaderboardNotFoundException(String message) {
        super(message, "LEADERBOARD_NOT_FOUND");
    }
}

