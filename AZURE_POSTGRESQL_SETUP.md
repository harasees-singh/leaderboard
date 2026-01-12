# Azure PostgreSQL Database Setup Guide

This guide walks you through creating and configuring Azure Database for PostgreSQL for the Leaderboard Platform.

## Overview

**What you need to create:**
1. ✅ **PostgreSQL Server** (Azure managed service)
2. ✅ **Database** (Hibernate cannot create the database, only tables)
3. ⚠️ **Tables** (Optional - Hibernate can create them automatically, or you can create manually)

**What Hibernate does:**
- With `ddl-auto: update`, Hibernate will automatically create/update tables when the application starts
- However, Hibernate **cannot create the database** - you must create it manually

---

## Step 1: Create Azure Database for PostgreSQL Server

### Option A: Using Azure Portal

1. **Sign in to Azure Portal**: https://portal.azure.com

2. **Create a new resource**:
   - Click **"Create a resource"**
   - Search for **"Azure Database for PostgreSQL"**
   - Select **"Azure Database for PostgreSQL - Flexible Server"** (recommended)
   - Click **"Create"**

3. **Configure the server**:
   
   **Basics Tab:**
   - **Subscription**: Select your subscription
   - **Resource Group**: Create new or select existing (e.g., `leaderboard-rg`)
   - **Server name**: Choose a unique name (e.g., `leaderboard-postgres`)
     - Must be globally unique
     - Lowercase letters, numbers, and hyphens only
   - **Region**: Select a region close to you
   - **PostgreSQL version**: Select **15** (recommended) or **14**
   - **Workload type**: **Development** (for dev/test) or **Production** (for production)
   - **Compute + storage**: 
     - **Development**: Burstable B1ms (1 vCore, 2GB RAM) - ~$25/month
     - **Production**: General Purpose D2s_v3 (2 vCore, 8GB RAM) - ~$100/month
   
   **Networking Tab:**
   - **Connectivity method**: 
     - **Public access** (for development/testing)
     - **Private access** (for production - requires VNet)
   - **Firewall rules**: 
     - Click **"Add current client IP address"** to allow your IP
     - For Container Apps, you'll need to add Azure services or specific IP ranges
     - **Allow Azure services and resources**: Enable this for Container Apps
   
   **Security Tab:**
   - **Admin username**: Choose a username (e.g., `adminuser`)
     - Cannot be `azure_superuser`, `azure_pg_admin`, `admin`, `administrator`, `root`, `guest`, or `public`
   - **Password**: Create a strong password
     - Must be 8-128 characters
     - Contains uppercase, lowercase, numbers, and special characters
   - **Save the password** - you'll need it for connection strings!
   
   **Review + Create**:
   - Review all settings
   - Click **"Create"**
   - Wait for deployment (takes 5-10 minutes)

### Option B: Using Azure CLI

```bash
# Login to Azure
az login

# Create resource group (if needed)
az group create --name leaderboard-rg --location eastus

# Create PostgreSQL Flexible Server
az postgres flexible-server create \
  --resource-group leaderboard-rg \
  --name leaderboard-postgres \
  --location eastus \
  --admin-user adminuser \
  --admin-password <your-strong-password> \
  --sku-name Standard_B1ms \
  --tier Burstable \
  --version 15 \
  --storage-size 32 \
  --public-access 0.0.0.0-255.255.255.255
```

---

## Step 2: Create the Database

Hibernate **cannot create the database** - you must create it manually.

### Option A: Using Azure Portal

1. **Go to your PostgreSQL server** in Azure Portal
2. Click **"Databases"** in the left menu
3. Click **"+ Add"**
4. Enter database name: `leaderboard_db`
5. Click **"Save"**

### Option B: Using Azure CLI

```bash
az postgres flexible-server db create \
  --resource-group leaderboard-rg \
  --server-name leaderboard-postgres \
  --database-name leaderboard_db
```

### Option C: Using psql (Command Line)

```bash
# Connect to your PostgreSQL server
psql -h leaderboard-postgres.postgres.database.azure.com \
     -U adminuser@leaderboard-postgres \
     -d postgres

# Create the database
CREATE DATABASE leaderboard_db;

# Exit
\q
```

### Option D: Using Azure Cloud Shell

1. Go to Azure Portal
2. Click the **Cloud Shell** icon (top menu)
3. Run:
```bash
psql -h leaderboard-postgres.postgres.database.azure.com \
     -U adminuser@leaderboard-postgres \
     -d postgres \
     -c "CREATE DATABASE leaderboard_db;"
```

---

## Step 3: Configure Firewall Rules for Container Apps

Your Container App needs to connect to PostgreSQL. Configure firewall rules:

### Option A: Allow Azure Services (Easiest)

1. Go to your PostgreSQL server in Azure Portal
2. Click **"Networking"** in the left menu
3. Under **"Public access"**:
   - Enable **"Allow Azure services and resources to access this server"**
   - Click **"Save"**

### Option B: Allow Specific IP Ranges

1. Go to **"Networking"** in your PostgreSQL server
2. Click **"+ Add current client IP address"** (for your development machine)
3. For Container Apps, you may need to:
   - Use **Private endpoints** (recommended for production)
   - Or add the Container Apps environment's outbound IP addresses

---

## Step 4: Tables - Automatic vs Manual Creation

### Option A: Let Hibernate Create Tables (Recommended for Development)

**This is the easiest approach** - Hibernate will automatically create tables when the application starts.

**Configuration:**
- Set environment variable: `SPRING_JPA_HIBERNATE_DDL_AUTO=update`
- Or use default (already configured in `application.yml`)

**What happens:**
- When the application starts, Hibernate checks if tables exist
- If tables don't exist, it creates them
- If tables exist but schema changed, it updates them
- **Safe for development** - preserves existing data

**First startup:**
```
Hibernate: create table leaderboards (...)
Hibernate: create table user_scores (...)
```

### Option B: Create Tables Manually (Recommended for Production)

For production, you may want to create tables manually for better control.

**SQL Script:**

```sql
-- Connect to your database first
\c leaderboard_db

-- Create leaderboards table
CREATE TABLE IF NOT EXISTS leaderboards (
    leaderboard_id VARCHAR(255) PRIMARY KEY,
    uuid VARCHAR(255) UNIQUE NOT NULL,
    pod_id VARCHAR(255),
    name VARCHAR(255),
    created_at TIMESTAMP NOT NULL,
    created_by VARCHAR(255),
    start_time TIMESTAMP,
    end_time TIMESTAMP,
    status VARCHAR(50) NOT NULL,
    metadata JSONB
);

-- Create indexes for leaderboards
CREATE UNIQUE INDEX IF NOT EXISTS idx_leaderboard_uuid ON leaderboards(uuid);
CREATE UNIQUE INDEX IF NOT EXISTS idx_leaderboard_leaderboard_id ON leaderboards(leaderboard_id);
CREATE INDEX IF NOT EXISTS idx_leaderboard_pod_id ON leaderboards(pod_id);

-- Create user_scores table
CREATE TABLE IF NOT EXISTS user_scores (
    user_id VARCHAR(255) NOT NULL,
    leaderboard_id VARCHAR(255) NOT NULL,
    score DOUBLE PRECISION NOT NULL,
    timestamp TIMESTAMP NOT NULL,
    PRIMARY KEY (user_id, leaderboard_id)
);

-- Create indexes for user_scores
CREATE INDEX IF NOT EXISTS idx_user_score_leaderboard_user ON user_scores(leaderboard_id, user_id);
CREATE INDEX IF NOT EXISTS idx_user_score_timestamp ON user_scores(timestamp);
CREATE INDEX IF NOT EXISTS idx_user_score_leaderboard_score ON user_scores(leaderboard_id, score DESC, timestamp ASC);
```

**How to run the script:**

1. **Using psql:**
```bash
psql -h leaderboard-postgres.postgres.database.azure.com \
     -U adminuser@leaderboard-postgres \
     -d leaderboard_db \
     -f create_tables.sql
```

2. **Using Azure Portal:**
   - Go to your PostgreSQL server
   - Click **"Query editor"** (preview)
   - Paste the SQL script
   - Click **"Run"**

3. **Using Azure Cloud Shell:**
   - Save script to a file
   - Upload to Cloud Shell
   - Run with psql

**Then set:**
- `SPRING_JPA_HIBERNATE_DDL_AUTO=validate` (validates schema matches entities, doesn't modify)

---

## Step 5: Get Connection Information

You'll need these values for your Container App environment variables:

### From Azure Portal:

1. Go to your PostgreSQL server
2. In **Overview**, note:
   - **Server name**: `leaderboard-postgres.postgres.database.azure.com`
   - **Admin username**: `adminuser` (what you set during creation)

3. **Connection string format:**
```
jdbc:postgresql://leaderboard-postgres.postgres.database.azure.com:5432/leaderboard_db?sslmode=require
```

### Environment Variables for Container App:

```bash
SPRING_DATASOURCE_URL=jdbc:postgresql://leaderboard-postgres.postgres.database.azure.com:5432/leaderboard_db?sslmode=require
SPRING_DATASOURCE_USERNAME=adminuser@leaderboard-postgres
SPRING_DATASOURCE_PASSWORD=<your-password>
SPRING_JPA_HIBERNATE_DDL_AUTO=update
```

**Important Notes:**
- Username format: `<admin-username>@<server-name>` (without `.postgres.database.azure.com`)
- Connection string must include `?sslmode=require` for Azure PostgreSQL
- Database name: `leaderboard_db` (what you created in Step 2)

---

## Step 6: Test the Connection

### Test from Local Machine:

```bash
psql -h leaderboard-postgres.postgres.database.azure.com \
     -U adminuser@leaderboard-postgres \
     -d leaderboard_db
```

If connection succeeds, you'll see:
```
leaderboard_db=>
```

### Test from Application:

1. Deploy your Container App with the environment variables
2. Check application logs for:
   - `Hibernate: create table leaderboards` (if using `ddl-auto: update`)
   - Or connection success messages
3. If you see errors, check:
   - Firewall rules allow your Container App
   - Username format is correct (`username@server-name`)
   - Password is correct
   - Database exists

---

## Summary Checklist

- [ ] Created PostgreSQL Flexible Server in Azure
- [ ] Set admin username and password (saved password securely)
- [ ] Created database: `leaderboard_db`
- [ ] Configured firewall rules (allow Azure services or specific IPs)
- [ ] Decided on table creation method:
  - [ ] Option A: Let Hibernate create tables (`ddl-auto: update`)
  - [ ] Option B: Create tables manually (`ddl-auto: validate`)
- [ ] Got connection information:
  - [ ] Server name
  - [ ] Admin username
  - [ ] Database name
- [ ] Set environment variables in Container App
- [ ] Tested connection

---

## Troubleshooting

### Error: "FATAL: password authentication failed"

**Solution:**
- Verify username format: `<username>@<server-name>` (not just `<username>`)
- Check password is correct
- Reset password if needed: Azure Portal → PostgreSQL server → **Reset password**

### Error: "Connection refused" or "Connection timeout"

**Solution:**
- Check firewall rules allow your IP or Azure services
- Verify server name is correct
- Ensure server is running (check in Azure Portal)

### Error: "database does not exist"

**Solution:**
- Create the database manually (Hibernate cannot create it)
- Verify database name in connection string matches created database

### Error: "SSL required"

**Solution:**
- Ensure connection string includes `?sslmode=require`
- Azure PostgreSQL requires SSL connections

### Tables not created automatically

**Solution:**
- Check `SPRING_JPA_HIBERNATE_DDL_AUTO` is set to `update` or `create`
- Check application logs for Hibernate errors
- Verify database connection is working
- Check user has CREATE TABLE permissions (admin user should have this)

---

## Production Recommendations

1. **Use Private Endpoints**: For better security, use private endpoints instead of public access
2. **Manual Table Creation**: Create tables manually and use `ddl-auto: validate` for production
3. **Backup Strategy**: Enable automated backups (7-35 days retention)
4. **High Availability**: Enable zone-redundant high availability
5. **Monitoring**: Set up alerts for connection failures, high CPU, etc.
6. **Connection Pooling**: Already configured via HikariCP (max 10, min 5 connections)

---

## Next Steps

After setting up PostgreSQL:

1. Configure environment variables in your Container App
2. Deploy the application
3. Monitor logs to verify connection and table creation
4. Test API endpoints to verify database operations

For more details, see `AZURE_CONFIGURATION.md`.
