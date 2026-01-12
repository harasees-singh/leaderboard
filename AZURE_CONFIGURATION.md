# Azure Configuration Guide

This guide explains how to configure the application to connect to Azure Redis Cache and Azure Database for PostgreSQL.

## Environment Variables

The application uses environment variables for configuration, making it easy to deploy to Azure Container Apps without code changes.

### Azure Redis Cache Configuration

Set these environment variables in your Azure Container App:

```bash
REDIS_HOST=<your-cache-name>.redis.cache.windows.net
REDIS_PORT=6380
REDIS_SSL=true
REDIS_PASSWORD=<your-redis-access-key>
REDIS_TIMEOUT=2000
```

**How to get these values:**

1. **REDIS_HOST**: 
   - Go to Azure Portal → Your Redis Cache
   - In the **Overview** section, copy the **Host name**
   - Format: `<cache-name>.redis.cache.windows.net`

2. **REDIS_PASSWORD**:
   - Go to Azure Portal → Your Redis Cache
   - Click **Access keys** in the left menu
   - Copy either **Primary key** or **Secondary key**

3. **REDIS_PORT**: 
   - Always use `6380` for Azure Redis Cache (SSL port)
   - Port `6379` is non-SSL and typically disabled

4. **REDIS_SSL**: 
   - Set to `true` for Azure Redis Cache

### Azure Database for PostgreSQL Configuration

Set these environment variables in your Azure Container App:

```bash
SPRING_DATASOURCE_URL=jdbc:postgresql://<server-name>.postgres.database.azure.com:5432/leaderboard_db?sslmode=require
SPRING_DATASOURCE_USERNAME=<admin-username>@<server-name>
SPRING_DATASOURCE_PASSWORD=<admin-password>
SPRING_DATASOURCE_MAX_POOL_SIZE=10
SPRING_DATASOURCE_MIN_IDLE=5
SPRING_JPA_HIBERNATE_DDL_AUTO=update
SPRING_JPA_SHOW_SQL=false
```

**How to get these values:**

1. **SPRING_DATASOURCE_URL**:
   - Go to Azure Portal → Your PostgreSQL server
   - In the **Overview** section, copy the **Server name**
   - Format: `jdbc:postgresql://<server-name>.postgres.database.azure.com:5432/<database-name>?sslmode=require`
   - **Important**: Include `?sslmode=require` for Azure PostgreSQL

2. **SPRING_DATASOURCE_USERNAME**:
   - Format: `<admin-username>@<server-name>`
   - Example: If your admin username is `adminuser` and server is `mypostgres`, use: `adminuser@mypostgres`

3. **SPRING_DATASOURCE_PASSWORD**:
   - The password you set when creating the PostgreSQL server
   - Or reset it in Azure Portal → PostgreSQL server → **Reset password**

4. **Database name**:
   - Create a database in your PostgreSQL server (e.g., `leaderboard_db`)
   - You can do this via Azure Portal → PostgreSQL server → **Databases** → **Add**

### Server Port (Optional)

```bash
SERVER_PORT=8080
```

This is the port your Spring Boot application listens on. Default is `8080`.

---

## Setting Environment Variables in Azure Container Apps

### Method 1: Azure Portal

1. Go to your **Container App** in Azure Portal
2. Click **Configuration** in the left menu
3. Click **Environment variables** tab
4. Click **+ Add** for each environment variable
5. Enter the **Name** and **Value**
6. Click **Save**

### Method 2: Azure CLI

```bash
az containerapp update \
  --name <your-container-app-name> \
  --resource-group <your-resource-group> \
  --set-env-vars \
    REDIS_HOST=<cache-name>.redis.cache.windows.net \
    REDIS_PORT=6380 \
    REDIS_SSL=true \
    REDIS_PASSWORD=<redis-key> \
    SPRING_DATASOURCE_URL="jdbc:postgresql://<server-name>.postgres.database.azure.com:5432/leaderboard_db?sslmode=require" \
    SPRING_DATASOURCE_USERNAME=<admin-username>@<server-name> \
    SPRING_DATASOURCE_PASSWORD=<admin-password>
```

### Method 3: Using Azure Key Vault (Recommended for Production)

For better security, store sensitive values in Azure Key Vault and reference them:

1. **Store secrets in Key Vault**:
   ```bash
   az keyvault secret set --vault-name <your-key-vault> --name "redis-password" --value "<redis-key>"
   az keyvault secret set --vault-name <your-key-vault> --name "postgres-password" --value "<postgres-password>"
   ```

2. **Reference in Container App**:
   - In Container App → Configuration → Environment variables
   - Add variable: `REDIS_PASSWORD`
   - Value: `@Microsoft.KeyVault(SecretUri=https://<your-key-vault>.vault.azure.net/secrets/redis-password/)`
   - Ensure your Container App has **Managed Identity** enabled and access to Key Vault

---

## Local Development Configuration

For local development, you can use the default values or override them:

### Using application.yml (defaults to localhost)

The application will use localhost values if environment variables are not set:
- Redis: `localhost:6379` (no SSL, no password)
- PostgreSQL: `localhost:5432`

### Using Environment Variables

Set environment variables in your IDE or shell:

**macOS/Linux:**
```bash
export REDIS_HOST=localhost
export REDIS_PORT=6379
export REDIS_SSL=false
export REDIS_PASSWORD=
export SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/leaderboard_db
export SPRING_DATASOURCE_USERNAME=leaderboard_user
export SPRING_DATASOURCE_PASSWORD=leaderboard_password
```

**Windows (PowerShell):**
```powershell
$env:REDIS_HOST="localhost"
$env:REDIS_PORT="6379"
$env:REDIS_SSL="false"
$env:REDIS_PASSWORD=""
$env:SPRING_DATASOURCE_URL="jdbc:postgresql://localhost:5432/leaderboard_db"
$env:SPRING_DATASOURCE_USERNAME="leaderboard_user"
$env:SPRING_DATASOURCE_PASSWORD="leaderboard_password"
```

---

## Testing the Connection

### Test Redis Connection

After deploying, check the application logs for:
```
Successfully connected to Redis at <host>:<port> (SSL enabled)
```

If you see connection errors, verify:
- Redis host and port are correct
- SSL is enabled (`REDIS_SSL=true`) for Azure Redis
- Password is correct
- Firewall rules allow your Container App's IP

### Test PostgreSQL Connection

The application will automatically test the connection on startup. Check logs for:
- JPA/Hibernate initialization messages
- Any database connection errors

If you see connection errors, verify:
- Connection string format is correct (especially the `@server-name` in username)
- SSL mode is set to `require`
- Firewall allows connections from your Container App
- Database exists

---

## Troubleshooting

### Redis Connection Issues

**Error: "Connection refused"**
- Check `REDIS_HOST` and `REDIS_PORT` are correct
- Verify firewall rules allow your Container App's outbound IP
- Ensure Redis cache is running

**Error: "Authentication failed"**
- Verify `REDIS_PASSWORD` is correct
- Check if you're using Primary or Secondary key
- Ensure password doesn't have extra spaces

**Error: "SSL handshake failed"**
- Ensure `REDIS_SSL=true` for Azure Redis
- Verify you're using port `6380` (not `6379`)

### PostgreSQL Connection Issues

**Error: "Connection refused"**
- Check connection string format
- Verify server name is correct
- Check firewall rules allow your Container App

**Error: "Authentication failed"**
- Verify username format: `<username>@<server-name>`
- Check password is correct
- Ensure database exists

**Error: "SSL required"**
- Ensure connection string includes `?sslmode=require`
- Azure PostgreSQL requires SSL

---

## Security Best Practices

1. **Never commit secrets to code**: Always use environment variables or Key Vault
2. **Use Key Vault for production**: Store sensitive values in Azure Key Vault
3. **Enable managed identity**: Use managed identity for Key Vault access
4. **Rotate passwords regularly**: Update Redis and PostgreSQL passwords periodically
5. **Use private endpoints**: For production, use private endpoints for Redis and PostgreSQL
6. **Enable firewall rules**: Restrict access to specific IP ranges or VNets

---

## Example: Complete Azure Container App Configuration

Here's a complete example of environment variables for a production deployment:

```bash
# Server
SERVER_PORT=8080

# Azure Redis Cache
REDIS_HOST=mycache.redis.cache.windows.net
REDIS_PORT=6380
REDIS_SSL=true
REDIS_PASSWORD=<from-key-vault>
REDIS_TIMEOUT=2000

# Azure Database for PostgreSQL
SPRING_DATASOURCE_URL=jdbc:postgresql://mypostgres.postgres.database.azure.com:5432/leaderboard_db?sslmode=require
SPRING_DATASOURCE_USERNAME=adminuser@mypostgres
SPRING_DATASOURCE_PASSWORD=<from-key-vault>
SPRING_DATASOURCE_MAX_POOL_SIZE=10
SPRING_DATASOURCE_MIN_IDLE=5

# JPA Configuration
SPRING_JPA_HIBERNATE_DDL_AUTO=validate
SPRING_JPA_SHOW_SQL=false
```

---

## Next Steps

1. Create Azure Redis Cache and PostgreSQL resources
2. Configure firewall rules to allow your Container App
3. Set environment variables in your Container App
4. Deploy and monitor application logs
5. Test the API endpoints to verify connectivity

For more details, see `AZURE_HOSTING_PLAN.md`.
