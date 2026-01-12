# Troubleshooting Guide

## Common Connection Issues

### Error: `java.net.UnknownHostException`

**Symptom:**
```
Caused by: java.net.UnknownHostException: leaderboard-postresql.postgres.database.azure.com
```

**Causes:**
1. **Typo in hostname** (most common)
2. **Incorrect server name format**
3. **DNS resolution issues**

**Solutions:**

#### 1. Check for Typos in Hostname

The error shows `leaderboard-postresql` - notice it's missing a 'q'. It should be:
- ❌ `leaderboard-postresql.postgres.database.azure.com` (typo)
- ✅ `leaderboard-postgresql.postgres.database.azure.com` (correct)

**How to verify your server name:**

1. **Azure Portal:**
   - Go to your PostgreSQL server
   - In **Overview**, check the **Server name**
   - Copy it exactly as shown

2. **Azure CLI:**
   ```bash
   az postgres flexible-server show \
     --resource-group <your-resource-group> \
     --name <your-server-name> \
     --query fullyQualifiedDomainName \
     --output tsv
   ```

#### 2. Verify Connection String Format

**Correct format:**
```
jdbc:postgresql://<server-name>.postgres.database.azure.com:5432/<database-name>?sslmode=require
```

**Common mistakes:**
- ❌ Missing `.postgres.database.azure.com` suffix
- ❌ Wrong port (should be `5432`)
- ❌ Missing `?sslmode=require` (required for Azure PostgreSQL)
- ❌ Typos in server name

#### 3. Check Environment Variable

In your Azure Container App:

1. Go to **Configuration** → **Environment variables**
2. Find `SPRING_DATASOURCE_URL`
3. Verify the hostname matches your PostgreSQL server name exactly
4. Check for typos

**Example of correct environment variable:**
```bash
SPRING_DATASOURCE_URL=jdbc:postgresql://leaderboard-postgresql.postgres.database.azure.com:5432/leaderboard_db?sslmode=require
```

#### 4. Test DNS Resolution

If you suspect DNS issues, test from Azure Cloud Shell:

```bash
# Test DNS resolution
nslookup leaderboard-postgresql.postgres.database.azure.com

# Test connection
psql -h leaderboard-postgresql.postgres.database.azure.com \
     -U <username>@leaderboard-postgresql \
     -d leaderboard_db
```

---

### Error: `Connection refused` or `Connection timeout`

**Causes:**
1. Firewall rules blocking access
2. Server is stopped or deleted
3. Wrong port number

**Solutions:**

#### 1. Check Firewall Rules

**Azure Portal:**
1. Go to your PostgreSQL server
2. Click **Networking** in left menu
3. Under **Public access**:
   - Ensure **"Allow Azure services and resources to access this server"** is enabled
   - Or add your Container App's outbound IP addresses

**Azure CLI:**
```bash
# Allow Azure services
az postgres flexible-server firewall-rule create \
  --resource-group <your-resource-group> \
  --name <your-server-name> \
  --rule-name AllowAzureServices \
  --start-ip-address 0.0.0.0 \
  --end-ip-address 0.0.0.0

# Or allow specific IP range
az postgres flexible-server firewall-rule create \
  --resource-group <your-resource-group> \
  --name <your-server-name> \
  --rule-name AllowContainerApps \
  --start-ip-address <start-ip> \
  --end-ip-address <end-ip>
```

#### 2. Verify Server Status

**Azure Portal:**
- Go to your PostgreSQL server
- Check **Overview** - server should show **Running** status

**Azure CLI:**
```bash
az postgres flexible-server show \
  --resource-group <your-resource-group> \
  --name <your-server-name> \
  --query state
```

---

### Error: `FATAL: password authentication failed`

**Causes:**
1. Wrong username format
2. Incorrect password
3. Username doesn't include `@server-name`

**Solutions:**

#### 1. Check Username Format

**Correct format:**
```
<admin-username>@<server-name>
```

**Example:**
- Server name: `leaderboard-postgresql`
- Admin username: `adminuser`
- **Correct**: `adminuser@leaderboard-postgresql`
- ❌ **Wrong**: `adminuser` (missing @server-name)

**Environment variable:**
```bash
SPRING_DATASOURCE_USERNAME=adminuser@leaderboard-postgresql
```

#### 2. Verify Password

1. Go to Azure Portal → PostgreSQL server
2. Click **Reset password** if needed
3. Update the password in Container App environment variables

#### 3. Test Credentials

```bash
psql -h leaderboard-postgresql.postgres.database.azure.com \
     -U adminuser@leaderboard-postgresql \
     -d leaderboard_db
```

---

### Error: `database "leaderboard_db" does not exist`

**Cause:**
- Database was not created manually (Hibernate cannot create databases)

**Solution:**

1. **Create the database:**
   ```bash
   psql -h leaderboard-postgresql.postgres.database.azure.com \
        -U adminuser@leaderboard-postgresql \
        -d postgres \
        -c "CREATE DATABASE leaderboard_db;"
   ```

2. **Or via Azure Portal:**
   - PostgreSQL server → **Databases** → **+ Add** → Enter `leaderboard_db`

---

### Error: `SSL required`

**Cause:**
- Connection string missing `?sslmode=require`

**Solution:**

**Correct connection string:**
```
jdbc:postgresql://leaderboard-postgresql.postgres.database.azure.com:5432/leaderboard_db?sslmode=require
```

**Environment variable:**
```bash
SPRING_DATASOURCE_URL=jdbc:postgresql://leaderboard-postgresql.postgres.database.azure.com:5432/leaderboard_db?sslmode=require
```

---

## Redis Connection Issues

### Error: `Connection refused` (Redis)

**Causes:**
1. Wrong host/port
2. Firewall blocking
3. SSL not enabled for Azure Redis

**Solutions:**

#### 1. Verify Redis Configuration

**For Azure Redis Cache:**
```bash
REDIS_HOST=<cache-name>.redis.cache.windows.net
REDIS_PORT=6380
REDIS_SSL=true
REDIS_PASSWORD=<access-key>
```

**Common mistakes:**
- ❌ Using port `6379` (non-SSL, usually disabled)
- ❌ `REDIS_SSL=false` (should be `true` for Azure)
- ❌ Missing password

#### 2. Check Firewall Rules

**Azure Portal:**
- Redis Cache → **Networking**
- Enable **"Allow access from Azure services"**
- Or add Container App IP addresses

---

## Quick Diagnostic Checklist

When troubleshooting connection issues, check:

### PostgreSQL:
- [ ] Server name is correct (no typos)
- [ ] Connection string includes `?sslmode=require`
- [ ] Username format: `<username>@<server-name>`
- [ ] Password is correct
- [ ] Database `leaderboard_db` exists
- [ ] Firewall allows Azure services or Container App IPs
- [ ] Server is running

### Redis:
- [ ] Host format: `<cache-name>.redis.cache.windows.net`
- [ ] Port is `6380` (SSL port)
- [ ] `REDIS_SSL=true`
- [ ] Password is set and correct
- [ ] Firewall allows access

### Container App:
- [ ] Environment variables are set correctly
- [ ] No typos in variable values
- [ ] Variables are saved (not just entered)
- [ ] Container App has been restarted after variable changes

---

## Getting Help

If issues persist:

1. **Check application logs** in Container App:
   - Container App → **Log stream** or **Logs**
   - Look for connection errors and stack traces

2. **Test connection manually:**
   ```bash
   # PostgreSQL
   psql -h <server-name>.postgres.database.azure.com \
        -U <username>@<server-name> \
        -d leaderboard_db
   
   # Redis (using redis-cli)
   redis-cli -h <cache-name>.redis.cache.windows.net \
            -p 6380 \
            --tls \
            -a <password> \
            ping
   ```

3. **Verify in Azure Portal:**
   - Check resource status
   - Review firewall rules
   - Check connection strings in resource overview

4. **Common fixes:**
   - Restart Container App after changing environment variables
   - Double-check for typos (especially in hostnames)
   - Verify all required environment variables are set
