# Troubleshooting Guide

## Common Connection Issues

### Warning: `HHH000342: Could not obtain connection to query metadata`

**Symptom:**
```
WARN o.h.e.j.e.i.JdbcEnvironmentInitiator : HHH000342: Could not obtain connection to query metadata
```

**What this means:**
- Hibernate is trying to connect to PostgreSQL during startup
- It needs to query database metadata (version, existing tables, etc.)
- The connection is failing

**Common causes:**
1. Database doesn't exist
2. Wrong connection string (hostname, port, database name)
3. Wrong credentials (username/password)
4. Firewall blocking the connection
5. Server is not running or unreachable

**Diagnostic Steps:**

#### 1. Check if Database Exists

**Azure Portal:**
- Go to your PostgreSQL server
- Click **Databases** in left menu
- Verify `leaderboard_db` exists
- If not, create it (see `AZURE_POSTGRESQL_SETUP.md`)

**Azure CLI:**
```bash
az postgres flexible-server db list \
  --resource-group <your-resource-group> \
  --server-name <your-server-name>
```

#### 2. Verify Connection String

Check your `SPRING_DATASOURCE_URL` environment variable:

**Correct format:**
```
jdbc:postgresql://<server-name>.postgres.database.azure.com:5432/leaderboard_db?sslmode=require
```

**Check for:**
- ✅ Correct hostname (no typos)
- ✅ Port is `5432`
- ✅ Database name matches created database
- ✅ Includes `?sslmode=require`

#### 3. Verify Credentials

**Username format:**
```
<admin-username>@<server-name>
```

**Example:**
- Server: `leaderboard-postgresql`
- Admin: `adminuser`
- **Correct**: `adminuser@leaderboard-postgresql`
- ❌ **Wrong**: `adminuser` (missing @server-name)

**Password:**
- Verify password is correct
- Reset if needed: Azure Portal → PostgreSQL server → **Reset password**

#### 4. Test Connection Manually

**From Azure Cloud Shell:**
```bash
psql -h <server-name>.postgres.database.azure.com \
     -U <username>@<server-name> \
     -d leaderboard_db
```

If this fails, the issue is with:
- Database existence
- Credentials
- Firewall rules

#### 5. Check Firewall Rules

**Azure Portal:**
1. Go to PostgreSQL server
2. Click **Networking**
3. Under **Public access**:
   - ✅ Enable **"Allow Azure services and resources to access this server"**
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
```

#### 6. Enable Detailed Logging

Temporarily enable SQL logging to see the exact error:

**Add to Container App environment variables:**
```bash
SPRING_JPA_SHOW_SQL=true
SPRING_JPA_PROPERTIES_HIBERNATE_FORMAT_SQL=true
```

Or check Container App logs for the full stack trace.

#### 7. Verify Server Status

**Azure Portal:**
- Go to PostgreSQL server → **Overview**
- Check server status is **Running**

**Azure CLI:**
```bash
az postgres flexible-server show \
  --resource-group <your-resource-group> \
  --name <your-server-name> \
  --query state
```

**Quick Fix Checklist:**
- [ ] Database `leaderboard_db` exists
- [ ] Connection string is correct (no typos)
- [ ] Username format: `<username>@<server-name>`
- [ ] Password is correct
- [ ] Firewall allows Azure services
- [ ] Server is running
- [ ] Connection string includes `?sslmode=require`

---

### Error: `Unable to determine Dialect without JDBC metadata`

**Symptom:**
```
Caused by: org.hibernate.HibernateException: Unable to determine Dialect without JDBC metadata 
(please set 'javax.persistence.jdbc.url', 'hibernate.connection.url', or 'hibernate.dialect')
```

**What this means:**
- Hibernate cannot connect to the database to auto-detect the dialect
- This is a **symptom** of a connection failure, not the root cause
- The underlying issue is usually: `UnknownHostException`, connection refused, or authentication failure

**Root causes:**
1. **Database connection is failing** (most common)
2. **Wrong connection string** (hostname, port, database name)
3. **Database doesn't exist**
4. **Firewall blocking connection**
5. **Wrong credentials**

**Solutions:**

#### 1. Fix the Database Connection (Primary Issue)

This error appears because the connection is failing. Check for:
- `UnknownHostException` - server doesn't exist or wrong hostname
- `Connection refused` - firewall blocking or server not running
- `Authentication failed` - wrong username/password

See other troubleshooting sections for connection issues.

#### 2. Verify Connection String is Set

**Check environment variable:**
```bash
SPRING_DATASOURCE_URL=jdbc:postgresql://<server-name>.postgres.database.azure.com:5432/leaderboard_db?sslmode=require
```

**Verify:**
- Variable name is exactly `SPRING_DATASOURCE_URL` (case-sensitive)
- Value is set correctly
- No extra spaces or quotes

#### 3. Add Fallback Dialect (Temporary Workaround)

The application now includes a fallback dialect configuration. However, **this won't fix connection issues** - it just prevents this specific error message.

**The real fix is to:**
1. Verify PostgreSQL server exists and is accessible
2. Fix the connection string
3. Ensure firewall allows connections
4. Verify credentials are correct

#### 4. Check Container App Logs for Root Cause

Look for the **actual connection error** before this Hibernate error:
- `UnknownHostException` → Server doesn't exist or wrong hostname
- `Connection refused` → Firewall or server not running
- `Authentication failed` → Wrong credentials

**Quick Fix:**
1. Find the **root connection error** in logs (usually appears before the Hibernate error)
2. Fix that connection issue
3. The Hibernate dialect error will disappear once connection works

---

### Error: `java.net.UnknownHostException`

**Symptom:**
```
Caused by: java.net.UnknownHostException: leaderboard-postgresql.postgres.database.azure.com
```

**What this means:**
- DNS lookup is failing - the hostname cannot be resolved
- The server might not exist, be deleted, or the name is incorrect

**Causes:**
1. **Server doesn't exist or was deleted**
2. **Typo in hostname**
3. **Wrong server name** (different name than expected)
4. **Server is in different region/resource group**
5. **DNS resolution issues** (rare)

**Solutions:**

#### 1. Verify Server Exists

**Azure Portal:**
1. Go to **All resources** or search for "PostgreSQL"
2. Look for your PostgreSQL Flexible Server
3. Check the exact **Server name** in the Overview
4. Verify it matches what you're using in the connection string

**Azure CLI:**
```bash
# List all PostgreSQL servers in your subscription
az postgres flexible-server list --output table

# Get exact server name and FQDN
az postgres flexible-server show \
  --resource-group <your-resource-group> \
  --name <your-server-name> \
  --query "{name:name, fqdn:fullyQualifiedDomainName}" \
  --output table
```

#### 2. Check for Typos in Hostname

Common typos:
- ❌ `leaderboard-postresql` (missing 'q')
- ❌ `leaderboard-postgres` (missing 'ql')
- ❌ `leaderboard-postgresql.postgresql.database.azure.com` (double 'postgresql')
- ✅ `leaderboard-postgresql.postgres.database.azure.com` (correct)

**How to get the correct server name:**

1. **Azure Portal:**
   - Go to **All resources** → Search for "PostgreSQL"
   - Click on your PostgreSQL Flexible Server
   - In **Overview**, copy the **Server name** exactly
   - Format should be: `<server-name>.postgres.database.azure.com`

2. **Azure CLI:**
   ```bash
   # List all PostgreSQL servers
   az postgres flexible-server list --output table
   
   # Get the exact FQDN
   az postgres flexible-server show \
     --resource-group <your-resource-group> \
     --name <your-server-name> \
     --query fullyQualifiedDomainName \
     --output tsv
   ```

3. **Test DNS Resolution:**
   ```bash
   # From Azure Cloud Shell or your local machine
   nslookup leaderboard-postgresql.postgres.database.azure.com
   
   # If this fails, the server doesn't exist or name is wrong
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
