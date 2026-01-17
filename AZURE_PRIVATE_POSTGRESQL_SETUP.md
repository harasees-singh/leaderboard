# Connecting Container Apps to Private PostgreSQL Server

If your PostgreSQL server is configured for **private access only** (VNet), you have several options to connect from Azure Container Apps.

## Understanding the Issue

**Private Access PostgreSQL:**
- More secure (not accessible from internet)
- Requires VNet integration
- Public access option is greyed out (cannot be changed after creation)

**Container Apps:**
- By default, Container Apps don't have VNet integration
- Need special configuration to access private resources

---

## Option 1: Recreate PostgreSQL Server with Public Access (Easiest)

**Best for:** Development, testing, or when security requirements allow public access with firewall rules.

### Steps:

1. **Create a new PostgreSQL server with public access:**
   - Azure Portal → Create **Azure Database for PostgreSQL - Flexible Server**
   - In **Networking** tab:
     - Select **"Public access"** (not "Private access")
     - Enable **"Allow Azure services and resources to access this server"**
     - Add firewall rules as needed

2. **Create the database:**
   - Create `leaderboard_db` in the new server

3. **Update Container App:**
   - Update `SPRING_DATASOURCE_URL` with new server hostname
   - Update `SPRING_DATASOURCE_USERNAME` and `SPRING_DATASOURCE_PASSWORD`

4. **Delete old server** (after migration, if needed)

**Pros:**
- ✅ Easiest solution
- ✅ Works immediately
- ✅ No VNet configuration needed

**Cons:**
- ⚠️ Less secure (though firewall rules provide protection)
- ⚠️ Need to recreate database and migrate data

---

## Option 2: Enable VNet Integration for Container Apps (Recommended for Production)

**Best for:** Production environments requiring maximum security.

### Architecture:
```
Container Apps Environment (VNet integrated)
    ↓ (Private Endpoint)
PostgreSQL Server (Private access)
```

### Steps:

#### Step 1: Create VNet and Subnet

**Azure Portal:**
1. Create **Virtual Network**:
   - Name: `leaderboard-vnet`
   - Address space: `10.0.0.0/16`
   - Create subnet for Container Apps: `container-apps-subnet` (e.g., `10.0.1.0/24`)

**Azure CLI:**
```bash
# Create resource group
az group create --name leaderboard-rg --location eastus

# Create VNet
az network vnet create \
  --resource-group leaderboard-rg \
  --name leaderboard-vnet \
  --address-prefix 10.0.0.0/16 \
  --location eastus

# Create subnet for Container Apps
az network vnet subnet create \
  --resource-group leaderboard-rg \
  --vnet-name leaderboard-vnet \
  --name container-apps-subnet \
  --address-prefix 10.0.1.0/24
```

#### Step 2: Create Private Endpoint for PostgreSQL

**Azure Portal:**
1. Go to your PostgreSQL server
2. Click **Networking** → **Private access**
3. Click **+ Private endpoint**
4. Configure:
   - **Name**: `postgres-private-endpoint`
   - **Virtual network**: Select your VNet
   - **Subnet**: Select a subnet (e.g., `10.0.2.0/24` for data services)
   - **Private DNS integration**: Enable (creates private DNS zone)

**Azure CLI:**
```bash
# Create private endpoint
az network private-endpoint create \
  --resource-group leaderboard-rg \
  --name postgres-private-endpoint \
  --vnet-name leaderboard-vnet \
  --subnet data-subnet \
  --private-connection-resource-id /subscriptions/<subscription-id>/resourceGroups/<rg>/providers/Microsoft.DBforPostgreSQL/flexibleServers/<server-name> \
  --group-id postgresqlServer \
  --connection-name postgres-connection
```

#### Step 3: Enable VNet Integration for Container Apps Environment

**Important:** VNet integration is only available for Container Apps Environments with **Dedicated workload profiles**, not Consumption plan.

**Check your current setup:**
1. Go to your **Container Apps Environment** in Azure Portal
2. Check the **Overview** page
3. Look for **Workload profiles** - if you see "Consumption", you need to upgrade

**To enable VNet integration, you need Dedicated workload profile:**

**Option A: Create new Container Apps Environment with Dedicated profile**

**Azure Portal:**
1. Create a new **Container Apps Environment**
2. In **Basics** tab:
   - Select **"Dedicated"** for workload profile type (not Consumption)
3. In **Networking** tab:
   - Select **"Custom VNet"**
   - Choose your VNet and subnet (`container-apps-subnet`)
4. Create the environment

**Azure CLI:**
```bash
# Create Container Apps Environment with Dedicated workload profile
az containerapp env create \
  --name <new-env-name> \
  --resource-group leaderboard-rg \
  --location eastus \
  --infrastructure-subnet-resource-id /subscriptions/<subscription-id>/resourceGroups/<rg>/providers/Microsoft.Network/virtualNetworks/leaderboard-vnet/subnets/container-apps-subnet \
  --workload-profile-type Dedicated
```

**Option B: If you're on Consumption plan**

**Consumption plan Container Apps cannot use VNet integration.** You have two choices:

1. **Upgrade to Dedicated** (recommended for production with private resources):
   - Create new Container Apps Environment with Dedicated profile
   - Migrate your Container App to the new environment
   - This incurs higher costs (~$0.000012/vCPU-second + $0.0000015/GB-second)

2. **Use public access PostgreSQL** (recommended for development):
   - Recreate PostgreSQL with public access (Option 1)
   - Much simpler and works with Consumption plan
   - Still secure with firewall rules

**Azure CLI:**
```bash
# Update Container Apps Environment with VNet integration
az containerapp env update \
  --name <your-env-name> \
  --resource-group leaderboard-rg \
  --infrastructure-subnet-resource-id /subscriptions/<subscription-id>/resourceGroups/<rg>/providers/Microsoft.Network/virtualNetworks/leaderboard-vnet/subnets/container-apps-subnet
```

#### Step 4: Update Connection String

Use the **private endpoint hostname** instead of public hostname:

**Environment variable:**
```bash
SPRING_DATASOURCE_URL=jdbc:postgresql://<private-endpoint-ip-or-dns>:5432/leaderboard_db?sslmode=require
```

Or use the private DNS name if private DNS integration was enabled.

**Pros:**
- ✅ Maximum security (no public exposure)
- ✅ Production-ready
- ✅ Follows Azure best practices

**Cons:**
- ⚠️ More complex setup
- ⚠️ Requires Dedicated workload profile (higher cost)
- ⚠️ More configuration needed
- ⚠️ **NOT available on Consumption plan** - must upgrade to Dedicated

---

## Option 3: Use Azure Private Link Service (Alternative)

If you can't change the Container Apps Environment to Dedicated mode, you can use Azure Private Link, but this is more complex and may require additional services.

---

## Option 4: Use Azure Database for PostgreSQL - Single Server (If Available)

**Note:** Azure Database for PostgreSQL Single Server is being retired, but if you have an existing one, it might have different networking options.

---

## Recommended Approach by Scenario

### Development/Testing (Consumption Plan):
**Use Option 1** - Recreate with public access
- ✅ Works with Consumption plan (no VNet needed)
- ✅ Fastest to set up
- ✅ Good enough security with firewall rules
- ✅ Easy to troubleshoot
- ✅ Lower cost

### Production (Dedicated Plan):
**Use Option 2** - VNet integration with private endpoints
- ✅ Maximum security
- ✅ Follows Azure best practices
- ⚠️ Requires Dedicated Container Apps Environment
- ⚠️ Higher cost than Consumption plan

### If you're on Consumption plan:
**You must use Option 1** (public access PostgreSQL)
- Consumption plan does NOT support VNet integration
- Cannot connect to private-only resources
- Must recreate PostgreSQL with public access

### Quick Migration:
**Use Option 1** - Recreate with public access
- Migrate data from old server
- Update connection strings
- Delete old server

---

## Step-by-Step: Recreating PostgreSQL with Public Access

### 1. Get Data from Current Server (if needed)

If you have data to migrate:

```bash
# Export data using pg_dump
pg_dump -h <private-endpoint> \
        -U <username>@<server-name> \
        -d leaderboard_db \
        -F c \
        -f backup.dump
```

### 2. Create New PostgreSQL Server

**Azure Portal:**
1. **Create a resource** → **Azure Database for PostgreSQL - Flexible Server**
2. **Basics:**
   - Server name: `leaderboard-postgres-public` (or your choice)
   - Region: Same as your Container App
   - PostgreSQL version: 15
   - Workload type: Development or Production
3. **Networking:**
   - **Select "Public access"** (important!)
   - Enable **"Allow Azure services and resources to access this server"**
   - Click **"Add current client IP address"** (for your access)
4. **Security:**
   - Set admin username and password
5. **Review + Create**

### 3. Create Database

```bash
az postgres flexible-server db create \
  --resource-group leaderboard-rg \
  --server-name leaderboard-postgres-public \
  --database-name leaderboard_db
```

### 4. Restore Data (if needed)

```bash
pg_restore -h leaderboard-postgres-public.postgres.database.azure.com \
           -U <username>@leaderboard-postgres-public \
           -d leaderboard_db \
           backup.dump
```

### 5. Update Container App Environment Variables

```bash
SPRING_DATASOURCE_URL=jdbc:postgresql://leaderboard-postgres-public.postgres.database.azure.com:5432/leaderboard_db?sslmode=require
SPRING_DATASOURCE_USERNAME=<username>@leaderboard-postgres-public
SPRING_DATASOURCE_PASSWORD=<password>
```

### 6. Test Connection

The Container App should now be able to connect!

---

## Security Best Practices for Public Access

Even with public access, you can secure it:

1. **Firewall Rules:**
   - Only allow Azure services
   - Add specific IP ranges if needed
   - Don't allow `0.0.0.0/0` (all IPs)

2. **Strong Passwords:**
   - Use complex passwords
   - Rotate regularly

3. **SSL Required:**
   - Always use `?sslmode=require` in connection string
   - Encrypts data in transit

4. **Monitor Access:**
   - Enable Azure Defender for PostgreSQL
   - Review connection logs

---

## Troubleshooting

### "Public access option is greyed out"
- Server was created with private access
- Cannot be changed after creation
- Must recreate server with public access

### "Cannot connect after VNet integration"
- Verify private endpoint is created
- Check private DNS zone is configured
- Verify Container Apps Environment is in Dedicated mode
- Check subnet routes and NSG rules

### "Connection timeout"
- Check firewall rules allow Azure services
- Verify server is running
- Check network security groups (NSGs) if using VNet

---

## Next Steps

1. **Choose your approach** based on your requirements
2. **For quick setup**: Use Option 1 (recreate with public access)
3. **For production**: Use Option 2 (VNet integration)
4. **Update Container App** with new connection strings
5. **Test the connection** and verify tables are created

For more details on PostgreSQL setup, see `AZURE_POSTGRESQL_SETUP.md`.
