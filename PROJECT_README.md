# Leaderboard Platform

A Java-based leaderboard platform service built with Spring Boot, Maven, Redis, and JSON file storage.

## Features

1. **Update Score API**: Update user scores in a leaderboard
2. **Top N Retrieval API**: Fetch top N users from a leaderboard with proper ranking and tie-breaking

## Architecture

The application follows a clean, layered architecture:

- **API Layer**: REST controllers handling HTTP requests
- **Service Layer**: Business logic for score updates and ranking
- **Repository Layer**: Data access abstractions for persistent storage (JSON files) and Redis

## Technology Stack

- Java 17
- Spring Boot 3.2.0
- Maven
- Redis (via Jedis)
- JSON file storage
- JUnit 5 for testing
- Mockito for mocking

## Prerequisites

- Java 17 or higher
- Maven 3.6+
- Docker (for running Redis)

## Setup and Running

### 1. Start Redis using Docker

```bash
docker-compose up -d
```

This will start a Redis container on port 6379.

### 2. Build the project

```bash
mvn clean install
```

### 3. Run the application

```bash
mvn spring-boot:run
```

The application will start on port 8080.

## API Endpoints

### 1. Update User Score

**Endpoint**: `PUT /api/v1/leaderboards/{uuid}/users/{userId}`

**Request Body**:
```json
{
  "score": 1500.5
}
```

**Response**:
```json
{
  "uuid": "test-uuid-123",
  "userId": "user-789",
  "score": 1500.5,
  "rank": 42,
  "updatedAt": "2024-01-15T10:35:00Z"
}
```

### 2. Get Top N Users

**Endpoint**: `GET /api/v1/leaderboards/{uuid}/top?limit=N`

**Query Parameters**:
- `limit` (optional, default: 10): Number of top users to retrieve (max: 1000)

**Response**:
```json
{
  "uuid": "test-uuid-123",
  "users": [
    {
      "userId": "user123",
      "rank": 1,
      "score": 2500.5,
      "timestamp": "2024-01-15T10:30:00Z"
    },
    {
      "userId": "user456",
      "rank": 2,
      "score": 2300.0,
      "timestamp": "2024-01-15T10:25:00Z"
    }
  ],
  "totalUsers": 1500,
  "retrievedAt": "2024-01-15T10:35:00Z"
}
```

## Ranking Logic

- Users are ranked by score (descending)
- For users with the same score, earlier timestamps rank higher (tie-breaking)
- Rankings are calculated using a composite score stored in Redis sorted sets

## Data Storage

### Persistent Storage (JSON Files)

- Leaderboards are stored in `./data/leaderboards/` directory
- User scores are stored in `./data/user-scores/` directory
- Retry queue is stored in `./data/retry-queue.json`

### Redis

- Redis sorted sets are used for fast ranking operations
- Each leaderboard has a sorted set key: `leaderboard:{leaderboardId}`
- Composite scores combine user score and timestamp for efficient ranking

## Retry Mechanism

If Redis updates fail, the system automatically queues them for retry:
- Failed updates are stored in a retry queue
- A scheduled task processes the retry queue every 30 seconds
- Maximum retry count: 5 attempts

## Running Tests

```bash
mvn test
```

## Configuration

Configuration can be modified in `src/main/resources/application.properties`:

```properties
# Server Configuration
server.port=8080

# Redis Configuration
redis.host=localhost
redis.port=6379
redis.timeout=2000

# Storage Configuration
leaderboard.storage.directory=./data
```

## Project Structure

```
src/
├── main/
│   ├── java/com/leaderboard/platform/
│   │   ├── controller/          # REST API controllers
│   │   ├── service/             # Business logic
│   │   ├── repository/          # Data access interfaces
│   │   │   └── impl/           # Repository implementations
│   │   ├── model/              # Data models
│   │   ├── dto/                # Data transfer objects
│   │   ├── exception/          # Exception handling
│   │   └── config/             # Configuration classes
│   └── resources/
│       └── application.properties
└── test/
    └── java/com/leaderboard/platform/
        ├── controller/          # Controller tests
        ├── service/             # Service tests
        └── repository/          # Repository tests
```

## Error Handling

The application includes comprehensive error handling:
- `LeaderboardNotFoundException`: When a leaderboard is not found
- `InvalidRequestException`: For invalid input parameters
- Global exception handler returns proper HTTP status codes and error messages

## Notes

- The application uses JSON files for persistent storage instead of a traditional database
- Redis is used as a cache for fast read operations
- The system is designed to be resilient to Redis failures (writes to persistent storage always succeed)
- All writes to persistent storage are synchronous, while Redis updates are best-effort

