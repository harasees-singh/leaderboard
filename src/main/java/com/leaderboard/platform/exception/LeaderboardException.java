package com.leaderboard.platform.exception;

public class LeaderboardException extends RuntimeException {
    private final String errorCode;
    
    public LeaderboardException(String message) {
        super(message);
        this.errorCode = "LEADERBOARD_ERROR";
    }
    
    public LeaderboardException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
    }
    
    public LeaderboardException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = "LEADERBOARD_ERROR";
    }
    
    public LeaderboardException(String message, String errorCode, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }
    
    public String getErrorCode() {
        return errorCode;
    }
}

