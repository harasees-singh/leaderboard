package com.leaderboard.platform.exception;

public class InvalidRequestException extends LeaderboardException {
    public InvalidRequestException(String message) {
        super(message, "INVALID_REQUEST");
    }
}

