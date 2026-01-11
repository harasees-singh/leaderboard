# Azure Hosting Plan for Leaderboard Platform

## Executive Summary

This document outlines a comprehensive plan for hosting the Leaderboard Platform on Microsoft Azure. The plan leverages fully managed Azure services to minimize operational overhead while ensuring scalability, reliability, and performance.

---

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────────┐
│                         Azure Services                          │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌──────────────────┐      ┌──────────────────┐               │
│  │ Azure Container │      │  Application     │               │
│  │      Apps        │◄─────│  Gateway / ALB   │               │
│  │                  │      │  (Optional)      │               │
│  └────────┬─────────┘      └──────────────────┘               │
│           │                                                    │
│           │ REST API                                           │
│           │                                                    │
│  ┌────────▼──────────────────────────────────────┐            │
│  │      Spring Boot Application                   │            │
│  │  - REST Controllers                            │            │
│  │  - Service Layer                               │            │
│  │  - Repository Layer                            │            │
│  └────────┬───────────────────────────┬──────────┘            │
│           │                           │                        │
│           │                           │                        │
│  ┌────────▼──────────┐    ┌──────────▼──────────┐            │
│  │ Azure Cache for   │    │ Azure Database for  │            │
│  │      Redis        │    │   PostgreSQL        │            │
│  │ (Sorted Sets)     │    │ (Persistent Store)  │            │
│  └───────────────────┘    └──────────┬──────────┘            │
│                                       │                        │
│  ┌───────────────────────────────────▼──────────┐            │
│  │         Azure Event Hubs                      │            │
│  │      (Kafka-compatible)                       │            │
│  │  - pod-{podId}-score-updates topics           │            │
│  │  - Schema Registry (Azure Schema Registry)    │            │
│  └──────────────────────────────────────────────┘            │
│                                                                 │
│  ┌─────────────────────────────────────────────────────┐      │
│  │        Supporting Services                          │      │
│  │  - Azure Key Vault (Secrets)                        │      │
│  │  - Application Insights (Monitoring)                │      │
│  │  - Azure Storage (Backups/Artifacts)                │      │
│  │  - Azure Container Registry (Required)              │      │
│  └─────────────────────────────────────────────────────┘      │
└─────────────────────────────────────────────────────────────────┘
```

---

## Core Services & Feasibility Analysis

### 1. Azure Cache for Redis ✅ **RECOMMENDED**

**Purpose**: Store Redis sorted sets for fast ranking operations and leaderboard queries

**Service Details**:
- **Service**: Azure Cache for Redis
- **Tier Options**:
  - **Basic/Standard**: Single node, suitable for development/testing
  - **Premium**: Multi-node with persistence, replication, clustering
  - **Enterprise/Enterprise Flash**: Highest performance with geo-replication

**Recommendation**: **Premium tier** (at minimum) for production
- Supports Redis persistence (AOF/RDB)
- High availability with replication
- Redis Cluster support for horizontal scaling
- VNet integration for security

**Configuration Requirements**:
- Redis version: 7.0+ (matches current docker-compose setup)
- Enable persistence (AOF recommended)
- Configure authentication (access keys)
- Network security: VNet integration or private endpoints

**Connection Configuration**:
```yaml
# application.yml update needed
spring:
  data:
    redis:
      host: <redis-cache-name>.redis.cache.windows.net
      port: 6380  # SSL port
      ssl: true
      password: ${REDIS_PASSWORD}  # From Key Vault
      timeout: 2000ms
```

**Feasibility**: ✅ **Excellent**
- Fully managed service
- Direct compatibility with Jedis client (minor config changes)
- Supports all Redis features used (sorted sets, transactions)
- No code changes required (just configuration)

**Estimated Cost**: 
- Premium P1 (6GB): ~$165/month
- Premium P2 (13GB): ~$330/month
- Premium P3 (26GB): ~$660/month

**Migration Steps**:
1. Create Azure Cache for Redis instance
2. Enable SSL and configure firewall rules
3. Update application configuration
4. Migrate data (if needed) using Redis DUMP/RESTORE or custom script

---

### 2. Azure Container Apps ✅ **RECOMMENDED**

**Purpose**: Host and manage the Spring Boot application as a containerized workload (Java 17, Spring Boot 3.2.0)

**Service Details**:
- **Service**: Azure Container Apps
- **Deployment Model**: Container-based (Docker)
- **Registry**: Azure Container Registry (ACR) for image storage

**Recommendation**: **Consumption Plan** (serverless) or **Dedicated Plan** (for production)
- Fully managed container orchestration
- Auto-scaling from 0 to N instances based on metrics
- Built-in load balancing and ingress
- Integrated with Azure services (Redis, databases, Event Hubs)
- Zero-downtime deployments with revision management
- Pay-per-use pricing model (Consumption plan)

**Configuration Requirements**:
- Container Image: Docker image stored in Azure Container Registry
- Runtime: Java 17 (via JRE base image)
- Resource allocation: Start with 0.5-1.0 CPU, 1-2GB RAM per instance
- Scaling: Auto-scale based on HTTP requests, CPU, or memory metrics
- Health checks: Configure HTTP health probe endpoint
- Ingress: Enable external ingress on port 8080

**Container Configuration**:
```yaml
# Container Apps Environment Variables
SPRING_DATASOURCE_URL: jdbc:postgresql://<postgres-host>:5432/leaderboard_db
SPRING_DATASOURCE_USERNAME: ${POSTGRES_USERNAME}
SPRING_DATASOURCE_PASSWORD: ${POSTGRES_PASSWORD}
REDIS_HOST: <redis-cache-name>.redis.cache.windows.net
REDIS_PORT: 6380
REDIS_SSL: true
REDIS_PASSWORD: ${REDIS_PASSWORD}
SERVER_PORT: 8080
```

**GitHub Actions CI/CD Pipeline**:
- Automated build and push to ACR on main branch updates
- Workflow: `.github/workflows/azure-container-registry.yml`
- Builds Docker image from Dockerfile
- Tags images with commit SHA and `latest`
- Pushes to Azure Container Registry

**Feasibility**: ✅ **Excellent**
- Container-native approach (Docker)
- No code changes required (standard Spring Boot app)
- Automatic integration with Azure services via environment variables
- Built-in monitoring and logging via Application Insights
- Supports any containerized application
- More flexible than platform-specific services

**Estimated Cost** (Consumption Plan):
- CPU: $0.000012/vCPU-second (~$0.043/vCPU-hour)
- Memory: $0.0000015/GB-second (~$0.0054/GB-hour)
- Example: 1 vCPU, 2GB RAM, 24/7 = ~$62/month
- Scales to zero when not in use (cost savings)

**Estimated Cost** (Dedicated Plan):
- Fixed pricing per workload profile
- Better for predictable workloads
- Starts at ~$0.000012/vCPU-second + $0.0000015/GB-second

**Deployment Steps**:
1. Create Azure Container Registry (ACR)
2. Create Container Apps Environment
3. Configure GitHub Actions workflow secrets:
   - `AZURE_CONTAINER_REGISTRY`: ACR login server URL
   - `AZURE_CONTAINER_REGISTRY_USERNAME`: ACR admin username
   - `AZURE_CONTAINER_REGISTRY_PASSWORD`: ACR admin password
4. Push to main branch triggers automatic build and push to ACR
5. Create Container App and configure:
   - Image: `{acr-name}.azurecr.io/leaderboard-platform:latest`
   - Environment variables from Key Vault or direct configuration
   - Scaling rules (min: 1, max: 10 instances)
   - Ingress: External, port 8080
6. Configure revision management for zero-downtime deployments

---

### 3. Apache Kafka Solution: Azure Event Hubs ✅ **RECOMMENDED**

**Purpose**: Handle asynchronous event processing from pods (Kafka-compatible event streaming)

**Service Details**:
- **Primary Option**: **Azure Event Hubs** (Kafka-compatible endpoint)
- **Alternative**: Confluent Cloud on Azure (if full Kafka ecosystem needed)

**Recommendation**: **Azure Event Hubs with Kafka API**
- Fully managed service
- Native Kafka protocol support (no code changes needed)
- High throughput: Millions of events per second
- Built-in retention and replay capabilities
- Integrated with Azure ecosystem
- Lower operational overhead than self-managed Kafka

**Configuration Requirements**:
- Namespace: Create Event Hubs namespace
- Event Hub (Topic): Create topics per pod pattern: `pod-{podId}-score-updates`
- Throughput units: Start with 1-2 TU (20-40 MB/s ingress)
- Retention: Configure retention period (1-7 days default, up to 90 days)
- Consumer groups: Configure for each service instance

**Event Hubs vs Self-Managed Kafka**:

| Feature | Azure Event Hubs | Self-Managed Kafka |
|---------|-----------------|-------------------|
| Management | Fully managed | Self-managed |
| Setup Time | Minutes | Days/Weeks |
| Scaling | Automatic | Manual |
| High Availability | Built-in | Manual setup |
| Cost | Pay-per-use | Infrastructure + ops |
| Kafka Protocol | ✅ Native support | ✅ Native |
| Schema Registry | Azure Schema Registry | Confluent/Others |

**Feasibility**: ✅ **Excellent**
- Kafka-compatible API (existing Kafka clients work)
- Minimal code changes (just connection strings)
- Better than self-managed for most use cases
- Lower total cost of ownership

**Alternative: Confluent Cloud on Azure** (if needed):
- Full Confluent ecosystem (Schema Registry, KSQL, etc.)
- Higher cost but more Kafka-native features
- Good if you need advanced Kafka features

**Estimated Cost**:
- Event Hubs Basic: $0.028/hour per throughput unit (~$20/month)
- Event Hubs Standard: $0.040/hour per throughput unit (~$29/month)
- Event Hubs Premium: $0.067/hour per processing unit (~$48/month)
- Start with 1-2 throughput units for standard tier

**Migration Steps**:
1. Create Event Hubs namespace
2. Create Event Hub topics (one per pod)
3. Update Kafka bootstrap servers configuration
4. Test connectivity with existing Kafka clients
5. Migrate consumer groups

---

## Additional Recommended Services

### 4. Azure Database for PostgreSQL ⚠️ **REQUIRED FOR PRODUCTION**

**Purpose**: Replace JSON file storage with proper SQL database (as per architecture documentation)

**Current State**: Application uses JSON file storage (`./data/` directory)
**Target State**: Migrate to PostgreSQL for persistent storage

**Service Details**:
- **Service**: Azure Database for PostgreSQL - Flexible Server
- **Tier**: General Purpose or Memory Optimized

**Recommendation**: **Flexible Server (General Purpose)**
- Better suited for Spring Boot applications
- More control over maintenance windows
- Cost-effective scaling
- Zone-redundant high availability

**Schema Design** (from architecture docs):
```sql
-- leaderboards table
CREATE TABLE leaderboards (
    leaderboard_id UUID PRIMARY KEY,
    uuid VARCHAR(255) UNIQUE NOT NULL,
    pod_id VARCHAR(255) NOT NULL,
    name VARCHAR(255),
    created_at TIMESTAMP NOT NULL,
    created_by VARCHAR(255),
    start_time TIMESTAMP,
    end_time TIMESTAMP,
    status VARCHAR(50) NOT NULL,
    metadata JSONB
);

-- user_scores table
CREATE TABLE user_scores (
    id UUID PRIMARY KEY,
    user_id VARCHAR(255) NOT NULL,
    leaderboard_id UUID NOT NULL,
    score DECIMAL(20,4) NOT NULL,
    timestamp TIMESTAMP NOT NULL,
    FOREIGN KEY (leaderboard_id) REFERENCES leaderboards(leaderboard_id),
    INDEX idx_leaderboard_user (leaderboard_id, user_id),
    INDEX idx_timestamp (timestamp)
);
```

**Feasibility**: ⚠️ **Requires Code Changes**
- Current implementation uses JSON file repositories
- Need to implement JPA/Spring Data repositories
- Migration script needed for existing data
- Estimated effort: 2-3 weeks

**Estimated Cost**:
- Basic (1 vCore, 2GB RAM): ~$25/month
- General Purpose (2 vCore, 5GB RAM): ~$100/month
- General Purpose (4 vCore, 10GB RAM): ~$200/month

---

### 5. Azure Key Vault ✅ **RECOMMENDED**

**Purpose**: Securely store secrets, connection strings, and certificates

**Use Cases**:
- Redis access keys
- Database connection strings
- API keys
- Certificates

**Feasibility**: ✅ **Easy Integration**
- Azure Container Apps has built-in Key Vault integration via managed identity
- Access via Azure SDK or Spring Cloud Azure
- No code changes required (configuration only)

**Estimated Cost**: ~$0.03/10,000 operations (~$5-10/month for typical usage)

---

### 6. Application Insights ✅ **RECOMMENDED**

**Purpose**: Application performance monitoring, logging, and diagnostics

**Features**:
- Application performance monitoring (APM)
- Request tracing
- Exception tracking
- Custom metrics
- Log aggregation

**Feasibility**: ✅ **Easy Integration**
- Built-in support in Azure Container Apps via Application Insights integration
- Just add dependency and configuration
- Automatic instrumentation

**Estimated Cost**: 
- Free tier: 5GB data ingestion/month
- Pay-as-you-go: $2.30/GB after free tier
- Typical small-medium app: $20-50/month

---

### 7. Azure Storage Account ✅ **OPTIONAL**

**Purpose**: Store backups, deployment artifacts, or migrate from JSON files temporarily

**Use Cases**:
- Database backups
- Application logs (if not using Application Insights)
- Static assets
- Migration staging area

**Feasibility**: ✅ **Easy to Use**
- Standard storage account
- Blob storage for files
- Low cost

**Estimated Cost**: ~$0.0184/GB/month for hot storage

---

### 8. Azure Container Registry (ACR) ✅ **REQUIRED**

**Purpose**: Store Docker images for Container Apps deployment

**Service Details**:
- **Service**: Azure Container Registry
- **Tier**: Basic (for dev/test) or Standard (for production)
- **Features**: Private Docker registry, geo-replication, security scanning

**Feasibility**: ✅ **Required for Container Apps**
- Container Apps requires images from a container registry
- ACR provides secure, private image storage
- Integrated with GitHub Actions for CI/CD
- Automatic image builds on push to main branch

**Estimated Cost**:
- Basic: ~$5/month (10GB storage, 1GB network egress/day)
- Standard: ~$20/month (100GB storage, 10GB network egress/day)

**Configuration**:
- Enable admin user for GitHub Actions authentication
- Configure network access (public or private endpoints)
- Set up retention policies for old images

---

## Network Architecture & Security

### Virtual Network (VNet) Setup

**Recommendation**: Deploy resources in the same VNet for security

```
┌─────────────────────────────────────────┐
│         Azure Virtual Network           │
│                                         │
│  ┌──────────────────────────────────┐  │
│  │   Subnet: apps-subnet            │  │
│  │   - Azure Container Apps         │  │
│  └──────────────────────────────────┘  │
│                                         │
│  ┌──────────────────────────────────┐  │
│  │   Subnet: data-subnet            │  │
│  │   - Azure Cache for Redis        │  │
│  │   - Azure Database for PostgreSQL│  │
│  │   - Azure Event Hubs (Private)   │  │
│  └──────────────────────────────────┘  │
│                                         │
│  ┌──────────────────────────────────┐  │
│  │   Private Endpoints              │  │
│  │   - Redis Private Endpoint       │  │
│  │   - PostgreSQL Private Endpoint  │  │
│  │   - Event Hubs Private Endpoint  │  │
│  └──────────────────────────────────┘  │
└─────────────────────────────────────────┘
```

### Security Recommendations

1. **Private Endpoints**: Use private endpoints for Redis, PostgreSQL, and Event Hubs
2. **Network Security Groups (NSG)**: Restrict traffic between subnets
3. **Azure Firewall**: Optional, for outbound traffic control
4. **Managed Identity**: Use managed identities instead of connection strings
5. **TLS/SSL**: Enable SSL for all connections (Redis, PostgreSQL, Event Hubs)

---

## Visual Studio Subscription Considerations

### Overview

Since you have a Visual Studio subscription, you receive monthly Azure credits (typically $50-$150/month depending on subscription level). However, there are important limitations and considerations:

**Key Points**:
- ✅ Monthly Azure credits included (amount varies by subscription tier)
- ⚠️ Spending limit enabled by default (services stop when credits are exhausted)
- ⚠️ Some services may not be eligible for credit usage
- ⚠️ Services are intended for **development and testing**, not production workloads
- ⚠️ Cannot use pay-as-you-go billing while spending limit is active

### Service Eligibility for Visual Studio Subscription Credits

Most Azure services are eligible for credit usage, but with limitations:

| Service | Eligible for Credits? | Notes |
|---------|----------------------|-------|
| Azure Cache for Redis | ✅ Yes (Basic/Standard tiers) | Premium tier may exceed credit limits |
| Azure Container Apps | ✅ Yes | Consumption/Dedicated plans eligible |
| Azure Event Hubs | ✅ Yes | Standard tier eligible |
| Azure Database for PostgreSQL | ✅ Yes | Basic/General Purpose tiers eligible |
| Azure Key Vault | ✅ Yes | Standard tier eligible |
| Application Insights | ✅ Yes | Free tier (5GB/month) + credits for additional usage |
| Azure Storage | ✅ Yes | Standard storage eligible |

### Recommendations for Visual Studio Subscription

#### Option 1: Development/Testing Setup (Recommended for VS Subscription)

**Use smaller, credit-friendly tiers**:

| Service | Recommended Tier | Monthly Cost | Notes |
|---------|-----------------|--------------|-------|
| Azure Cache for Redis | **Standard C1 (1GB)** or **Standard C2 (2.5GB)** | ~$15-55/month | Start small, scale if needed |
| Azure Container Apps | **Consumption Plan (0.5-1 vCPU, 1-2GB)** | ~$30-60/month | Sufficient for dev/test, scales to zero |
| Azure Event Hubs | **Basic tier (1 TU)** | ~$10/month | Sufficient for development |
| Azure Database for PostgreSQL | **Basic (1 vCore, 2GB)** or **General Purpose (2 vCore, 5GB)** | ~$25-100/month | Start with Basic for dev |
| Azure Key Vault | **Standard** | ~$5/month | Minimal cost |
| Application Insights | **Free tier** | $0-20/month | 5GB free, minimal usage after |
| Azure Storage | **Standard LRS (10GB)** | ~$0.20/month | Minimal for dev |

**Total Estimated Cost**: ~$103-$297/month (well within most VS subscription credit limits)

**Advantages**:
- Fits within Visual Studio subscription credits
- Good for development and testing
- No need to remove spending limit or provide credit card

**Limitations**:
- Not suitable for production workloads
- Lower performance/resource limits
- May need to upgrade for production

#### Option 2: Alternative Architecture (If Services Exceed Credits)

If you need more resources or want to stay within credits, consider:

1. **Azure Container Apps (Recommended)**:
   - Cost-effective container hosting
   - Consumption plan scales to zero when not in use
   - Pay only for actual usage
   - Fully managed container orchestration

2. **Azure Container Instances (ACI) + Azure Container Apps**:
   - Very cost-effective for containerized apps
   - Pay only for usage
   - Good for development/testing

3. **Azure Functions (Serverless)**:
   - Pay-per-execution model
   - Very cost-effective for low-medium traffic
   - ⚠️ Requires significant code refactoring

4. **Self-hosted Redis on Azure VM**:
   - Lower cost (~$30-50/month for small VM)
   - ⚠️ You manage it (not recommended)

### Important Considerations

1. **Spending Limit**:
   - Visual Studio subscriptions have a spending limit enabled
   - Services will **stop automatically** when credits are exhausted
   - To use pay-as-you-go billing, you must:
     - Remove the spending limit (requires credit card)
     - This converts subscription to pay-as-you-go model

2. **Production Workloads**:
   - Visual Studio subscription credits are **intended for dev/test only**
   - For production, consider:
     - Enterprise Agreement (if available)
     - Pay-as-you-go subscription
     - Remove spending limit and use credits as discount

3. **Credit Usage Monitoring**:
   - Monitor credit usage in Azure Portal
   - Set up alerts for credit usage thresholds
   - Budget alerts to track spending

### Recommended Approach

**For Development/Testing (Current Situation)**:
1. Use the smaller tiers listed in Option 1
2. Start with Basic/Standard tiers for all services
3. Monitor credit usage monthly
4. Scale up only if needed and credits allow

**For Production (Future)**:
1. Consider upgrading to Pay-As-You-Go subscription
2. Or use Enterprise Agreement if available
3. Use Reserved Instances for cost savings (30-40% discount)
4. Implement auto-scaling to optimize costs

### Cost Optimization Tips

1. **Use Azure Free Tier Services**:
   - Application Insights: 5GB free per month
   - Azure Storage: 5GB free per month (first 12 months)
   - Azure Functions: 1M free requests per month

2. **Right-size Resources**:
   - Start small and scale as needed
   - Monitor usage and adjust
   - Use Azure Cost Management to identify waste

3. **Development vs Production**:
   - Use smaller tiers for development
   - Shut down dev environments when not in use
   - Use auto-shutdown schedules

4. **Consider Azure Dev/Test Pricing**:
   - If you have Enterprise Agreement, use Dev/Test pricing
   - Significant discounts (up to 50% off)
   - Requires Visual Studio subscription per user

---

## Cost Estimation

### Monthly Cost Breakdown with Visual Studio Subscription Credits (Development/Testing)

**Recommended Setup for VS Subscription**:

| Service | Tier/Size | Estimated Monthly Cost |
|---------|-----------|----------------------|
| Azure Cache for Redis | Standard C1 (1GB) | $15 |
| Azure Container Apps | Consumption Plan (0.5 vCPU, 1GB) | $30 |
| Azure Container Registry | Basic (10GB) | $5 |
| Azure Event Hubs | Basic (1 TU) | $10 |
| Azure Database for PostgreSQL | Basic (1 vCore, 2GB) | $25 |
| Azure Key Vault | Standard | $5 |
| Application Insights | Free tier (5GB) | $0-10 |
| Azure Storage | Standard LRS (10GB) | $0.20 |
| **Total (VS Subscription Friendly)** | | **~$103-113/month** |

✅ **Fits within Visual Studio subscription credits** ($50-$150/month typical)

### Monthly Cost Breakdown (Development/Staging - Pay-As-You-Go)

**Note**: For Visual Studio subscription users, see the "Visual Studio Subscription Considerations" section above for credit-friendly options.

| Service | Tier/Size | Estimated Monthly Cost |
|---------|-----------|----------------------|
| Azure Container Apps | Consumption Plan (1 vCPU, 2GB) | $60 |
| Azure Container Registry | Standard (100GB) | $20 |
| Azure Cache for Redis | Premium P1 (6GB) | $165 |
| Azure Event Hubs | Standard (2 TU) | $58 |
| Azure Database for PostgreSQL | General Purpose (2 vCore, 5GB) | $100 |
| Azure Key Vault | Standard | $5 |
| Application Insights | Pay-as-you-go | $30 |
| Azure Storage | Standard LRS (50GB) | $1 |
| **Total (Development)** | | **~$456/month** |

### Monthly Cost Breakdown (Production)

| Service | Tier/Size | Estimated Monthly Cost |
|---------|-----------|----------------------|
| Azure Container Apps | Dedicated Plan (2 vCPU, 4GB) × 2 instances | $300 |
| Azure Container Registry | Standard (100GB) | $20 |
| Azure Cache for Redis | Premium P2 (13GB) | $330 |
| Azure Event Hubs | Standard (4 TU) | $116 |
| Azure Database for PostgreSQL | General Purpose (4 vCore, 10GB) | $200 |
| Azure Key Vault | Standard | $10 |
| Application Insights | Pay-as-you-go | $50 |
| Azure Storage | Standard LRS (100GB) | $2 |
| **Total (Production)** | | **~$1,094/month** |

**Note**: Costs are estimates and can vary based on:
- Actual usage patterns
- Data transfer volumes
- Regional pricing differences
- Reserved capacity discounts (can save 30-40%)

---

## Migration Roadmap

### Phase 1: Foundation Setup (Week 1-2)
1. ✅ Create Azure subscription and resource group
2. ✅ Set up Virtual Network and subnets (optional for dev/test)
3. ✅ Deploy Azure Cache for Redis (Standard C1/C2 for VS Subscription, Premium for Pay-As-You-Go)
4. ✅ Deploy Azure Key Vault
5. ✅ Configure secrets in Key Vault

### Phase 2: Application Deployment (Week 2-3)
1. ✅ Create Azure Container Registry (ACR)
2. ✅ Create Azure Container Apps Environment
3. ✅ Configure GitHub Actions workflow for CI/CD
4. ✅ Build and push Docker image to ACR
5. ✅ Create Container App and deploy application (with Redis connection)
6. ✅ Test basic functionality
7. ✅ Set up Application Insights

### Phase 3: Event Streaming (Week 3-4)
1. ✅ Create Azure Event Hubs namespace
2. ✅ Create Event Hub topics (per pod pattern)
3. ✅ Update application configuration for Event Hubs
4. ✅ Implement Kafka consumer (if not already done)
5. ✅ Test event processing

### Phase 4: Database Migration (Week 4-6) ⚠️ **REQUIRES CODE CHANGES**
1. ⚠️ Deploy Azure Database for PostgreSQL
2. ⚠️ Design and create database schema
3. ⚠️ Implement JPA repositories (replace JSON repositories)
4. ⚠️ Create data migration scripts
5. ⚠️ Migrate existing data from JSON files
6. ⚠️ Update application to use PostgreSQL
7. ⚠️ Test and validate data integrity

### Phase 5: Production Hardening (Week 6-8)
1. ✅ Configure private endpoints
2. ✅ Set up network security groups
3. ✅ Enable high availability (Redis, PostgreSQL)
4. ✅ Configure auto-scaling
5. ✅ Set up monitoring and alerts
6. ✅ Performance testing and optimization
7. ✅ Disaster recovery planning
8. ✅ Documentation and runbooks

---

## Configuration Changes Required

### 1. Application Configuration Updates

**File**: `src/main/resources/application.yml`

```yaml
server:
  port: 8080

spring:
  application:
    name: leaderboard-platform
  profiles:
    active: azure
  
  # Redis Configuration (Azure Cache for Redis)
  data:
    redis:
      host: ${REDIS_HOST}
      port: 6380
      ssl: true
      password: ${REDIS_PASSWORD}
      timeout: 2000ms
      jedis:
        pool:
          max-active: 50
          max-idle: 20
          min-idle: 5

  # PostgreSQL Configuration (when migrated)
  datasource:
    url: jdbc:postgresql://${DB_HOST}:5432/${DB_NAME}?sslmode=require
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
    hikari:
      maximum-pool-size: 10
      minimum-idle: 5
      
  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: false
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect

  # Kafka Configuration (Azure Event Hubs)
  kafka:
    bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS}
    consumer:
      group-id: leaderboard-platform-consumer
      auto-offset-reset: earliest
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.apache.kafka.common.serialization.StringDeserializer
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.apache.kafka.common.serialization.StringSerializer

# Azure Container Apps Configuration
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  endpoint:
    health:
      show-details: always
      probes:
        enabled: true
  metrics:
    export:
      azure-monitor:
        enabled: true
server:
  port: 8080

logging:
  level:
    com.leaderboard.platform: INFO
    org.springframework: WARN
```

### 2. Maven Dependencies (if migrating to PostgreSQL)

**File**: `pom.xml` (add if not present)

```xml
<!-- Spring Data JPA -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-jpa</artifactId>
</dependency>

<!-- PostgreSQL Driver -->
<dependency>
    <groupId>org.postgresql</groupId>
    <artifactId>postgresql</artifactId>
    <scope>runtime</scope>
</dependency>

<!-- Spring Cloud Azure (for Key Vault integration) -->
<dependency>
    <groupId>com.azure.spring</groupId>
    <artifactId>spring-cloud-azure-starter-keyvault-secrets</artifactId>
</dependency>

<!-- Kafka (if not already present) -->
<dependency>
    <groupId>org.springframework.kafka</groupId>
    <artifactId>spring-kafka</artifactId>
</dependency>
```

### 3. Environment Variables in Azure Container Apps

Set these via Azure Portal, CLI, or Container Apps configuration:

```bash
REDIS_HOST=<redis-name>.redis.cache.windows.net
REDIS_PASSWORD=<from-key-vault>
DB_HOST=<postgres-server>.postgres.database.azure.com
DB_NAME=leaderboard_db
DB_USERNAME=<admin-user>
DB_PASSWORD=<from-key-vault>
KAFKA_BOOTSTRAP_SERVERS=<event-hubs-namespace>.servicebus.windows.net:9093
```

---

## High Availability & Disaster Recovery

### High Availability Configuration

1. **Azure Container Apps**:
   - Deploy minimum 2 replicas
   - Configure auto-scaling (min: 2, max: 10 replicas)
   - Use revision management for zero-downtime deployments
   - Configure health probes for automatic recovery

2. **Azure Cache for Redis**:
   - Premium tier with replication
   - Configure geo-replication for cross-region DR
   - Enable persistence (AOF)

3. **Azure Database for PostgreSQL**:
   - Enable zone-redundant high availability
   - Configure automated backups (7-35 days retention)
   - Point-in-time restore capability

4. **Azure Event Hubs**:
   - Automatic replication within region
   - Configure geo-disaster recovery (optional)
   - Consumer group failover

### Disaster Recovery Plan

1. **Backup Strategy**:
   - PostgreSQL: Automated daily backups + point-in-time restore
   - Redis: AOF persistence + optional RDB snapshots
   - Configuration: Stored in Infrastructure as Code (ARM/Bicep/Terraform)

2. **Recovery Objectives**:
   - RTO (Recovery Time Objective): < 4 hours
   - RPO (Recovery Point Objective): < 1 hour (depends on backup frequency)

3. **Failover Procedures**:
   - Documented runbooks for each service
   - Tested failover procedures
   - Monitoring and alerting for DR scenarios

---

## Monitoring & Observability

### Application Insights Dashboard

Key metrics to monitor:
- Request rates and latencies (p50, p95, p99)
- Error rates
- Redis cache hit/miss rates
- Database query performance
- Kafka consumer lag
- Application health status

### Alerting

Configure alerts for:
- Application downtime
- High error rates (>1%)
- High latency (p95 > 200ms)
- Redis memory usage (>80%)
- Database connection pool exhaustion
- Kafka consumer lag (>1000 messages)
- Disk space warnings

---

## Scalability Considerations

### Horizontal Scaling

1. **Application Tier**:
   - Azure Container Apps auto-scales based on HTTP requests, CPU, or memory
   - Scale rules: Scale out at 70% CPU or >100 concurrent requests, scale in at 30% CPU
   - Minimum: 1 replica, Maximum: 10 replicas (configurable)
   - Consumption plan scales to zero when not in use

2. **Redis**:
   - Upgrade to larger tier or enable clustering (Premium)
   - Consider Redis Enterprise for multi-region

3. **PostgreSQL**:
   - Vertical scaling: Upgrade to larger SKU
   - Read replicas for read-heavy workloads
   - Consider Azure Database for PostgreSQL Hyperscale for massive scale

4. **Event Hubs**:
   - Increase throughput units based on ingress/egress
   - Scale automatically or manually

### Performance Optimization

1. **Redis**:
   - Connection pooling (already configured)
   - Pipeline operations where possible
   - Monitor and optimize memory usage

2. **Database**:
   - Proper indexing (already defined in schema)
   - Query optimization
   - Connection pooling
   - Read replicas for reporting queries

3. **Application**:
   - Async processing for non-critical paths
   - Caching strategies
   - Connection pooling
   - Batch operations where applicable

---

## Security Checklist

- [ ] Enable private endpoints for all data services
- [ ] Use Azure Key Vault for all secrets
- [ ] Enable SSL/TLS for all connections
- [ ] Configure network security groups
- [ ] Use managed identities where possible
- [ ] Enable Azure Defender for Cloud
- [ ] Configure firewall rules (Redis, PostgreSQL)
- [ ] Enable audit logging
- [ ] Regular security updates
- [ ] Implement API authentication/authorization
- [ ] Encrypt data at rest (enabled by default in Azure)
- [ ] Encrypt data in transit (TLS/SSL)

---

## Comparison: Azure Event Hubs vs Alternatives

### Azure Event Hubs (Recommended) ✅

**Pros**:
- Fully managed, no infrastructure to maintain
- Kafka protocol support (no code changes)
- High throughput (millions of events/sec)
- Integrated with Azure ecosystem
- Lower operational overhead
- Cost-effective for most scenarios

**Cons**:
- Less Kafka-native features than Confluent
- Some advanced Kafka features may not be available

**Best For**: Most use cases, especially if already on Azure

### Confluent Cloud on Azure

**Pros**:
- Full Confluent ecosystem (Schema Registry, KSQL, etc.)
- More Kafka-native features
- Better for complex Kafka use cases

**Cons**:
- Higher cost
- Additional vendor (Confluent)
- More complex setup

**Best For**: Complex Kafka requirements, need for KSQL/Streaming SQL

### Self-Managed Kafka on Azure VMs

**Pros**:
- Full control
- All Kafka features available
- Can be cost-effective at very large scale

**Cons**:
- High operational overhead
- Setup and maintenance complexity
- Need Kafka expertise
- High availability setup required

**Best For**: Very specific requirements, large scale, Kafka expertise available

---

## Recommendations Summary

### ✅ **Must Have (Phase 1-2)**
1. **Azure Cache for Redis** (Premium) - Core functionality
2. **Azure Container Apps** (Consumption/Dedicated) - Application hosting
3. **Azure Container Registry** (Basic/Standard) - Container image storage
3. **Azure Event Hubs** (Standard) - Event streaming

### ⚠️ **Required for Production (Phase 4)**
4. **Azure Database for PostgreSQL** - Replace JSON storage (requires code changes)

### ✅ **Highly Recommended**
5. **Azure Key Vault** - Secret management
6. **Application Insights** - Monitoring
7. **Private Endpoints** - Security

### ✅ **Optional**
8. **Azure Storage** - Backups/artifacts
9. **Azure Container Registry** - Only if containerizing

---

## Next Steps

1. **Review and Approve Plan**: Review this document with stakeholders
2. **Create Azure Resources**: Use provided configurations to create resources
3. **Set Up Development Environment**: Deploy to development/staging first
4. **Plan Database Migration**: Allocate time for PostgreSQL migration (2-3 weeks)
5. **Configure CI/CD**: Set up deployment pipeline
6. **Performance Testing**: Test at expected load
7. **Security Review**: Complete security checklist
8. **Documentation**: Create operational runbooks

---

## References

- [Azure Cache for Redis Documentation](https://docs.microsoft.com/azure/azure-cache-for-redis/)
- [Azure Container Apps Documentation](https://docs.microsoft.com/azure/container-apps/)
- [Azure Container Registry Documentation](https://docs.microsoft.com/azure/container-registry/)
- [Azure Event Hubs Documentation](https://docs.microsoft.com/azure/event-hubs/)
- [Azure Database for PostgreSQL Documentation](https://docs.microsoft.com/azure/postgresql/)
- [Azure Event Hubs for Apache Kafka](https://docs.microsoft.com/azure/event-hubs/azure-event-hubs-kafka-overview)

---

**Document Version**: 1.0  
**Last Updated**: 2024  
**Author**: Architecture Review

