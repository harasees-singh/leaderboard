package com.leaderboard.platform.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;

@Entity
@Table(name = "leaderboards", indexes = {
    @Index(name = "idx_leaderboard_uuid", columnList = "uuid", unique = true),
    @Index(name = "idx_leaderboard_leaderboard_id", columnList = "leaderboard_id", unique = true),
    @Index(name = "idx_leaderboard_pod_id", columnList = "pod_id")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Leaderboard {
    @Id
    @Column(name = "leaderboard_id")
    private String leaderboardId;
    
    @Column(name = "uuid", unique = true, nullable = false)
    private String uuid;
    
    @Column(name = "pod_id")
    private String podId;
    
    @Column(name = "name")
    private String name;
    
    @Column(name = "created_at", nullable = false)
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC")
    private Instant createdAt;
    
    @Column(name = "created_by")
    private String createdBy;
    
    @Column(name = "start_time")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC")
    private Instant startTime;
    
    @Column(name = "end_time")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC")
    private Instant endTime;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private LeaderboardStatus status;
    
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private Map<String, Object> metadata;
}

