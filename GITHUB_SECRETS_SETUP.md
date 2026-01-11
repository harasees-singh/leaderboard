# GitHub Secrets Setup Guide

This guide explains how to set up GitHub secrets for the Azure Container Registry CI/CD pipeline.

## Required Secrets

The workflow requires three secrets:
1. `AZURE_CONTAINER_REGISTRY` - Your ACR login server URL
2. `AZURE_CONTAINER_REGISTRY_USERNAME` - ACR admin username
3. `AZURE_CONTAINER_REGISTRY_PASSWORD` - ACR admin password

---

## Step 1: Create Azure Container Registry

### Option A: Using Azure Portal

1. **Sign in to Azure Portal**: https://portal.azure.com
2. **Create a new resource**:
   - Click "Create a resource"
   - Search for "Container Registry"
   - Click "Create"

3. **Configure the registry**:
   - **Subscription**: Select your subscription
   - **Resource Group**: Create new or select existing
   - **Registry name**: Choose a unique name (e.g., `leaderboardacr` or `yournameacr`)
     - Must be globally unique
     - 5-50 alphanumeric characters
     - Lowercase only
   - **Location**: Select a region close to you
   - **SKU**: 
     - **Basic** for dev/test (~$5/month)
     - **Standard** for production (~$20/month)
   - **Admin user**: **Enable** (required for GitHub Actions)
   - Click "Review + create", then "Create"

4. **Wait for deployment** (takes 1-2 minutes)

### Option B: Using Azure CLI

```bash
# Login to Azure
az login

# Create resource group (if needed)
az group create --name leaderboard-rg --location eastus

# Create Container Registry
az acr create \
  --resource-group leaderboard-rg \
  --name <your-registry-name> \
  --sku Basic \
  --admin-enabled true
```

Replace `<your-registry-name>` with your desired registry name (must be globally unique).

---

## Step 2: Get the Secret Values

### Get AZURE_CONTAINER_REGISTRY

This is your ACR login server URL in the format: `<registry-name>.azurecr.io`

**From Azure Portal**:
1. Go to your Container Registry resource
2. In the **Overview** section, you'll see:
   - **Login server**: `yourregistryname.azurecr.io`
   - Copy this value

**From Azure CLI**:
```bash
az acr show --name <your-registry-name> --query loginServer --output tsv
```

**Example**: `leaderboardacr.azurecr.io`

---

### Get AZURE_CONTAINER_REGISTRY_USERNAME

This is the admin username for your ACR.

**From Azure Portal**:
1. Go to your Container Registry
2. Click **Access keys** in the left menu
3. Under **Admin user**, you'll see:
   - **Username**: This is your username (usually the same as your registry name)
   - Copy this value

**From Azure CLI**:
```bash
az acr credential show --name <your-registry-name> --query username --output tsv
```

**Example**: `leaderboardacr`

---

### Get AZURE_CONTAINER_REGISTRY_PASSWORD

This is the admin password for your ACR.

**From Azure Portal**:
1. Go to your Container Registry
2. Click **Access keys** in the left menu
3. Under **Admin user**, you'll see:
   - **Password**: Click "Show" to reveal the password
   - Copy this value (you can use either password1 or password2)

**From Azure CLI**:
```bash
# Get password1
az acr credential show --name <your-registry-name> --query passwords[0].value --output tsv

# Or get password2
az acr credential show --name <your-registry-name> --query passwords[1].value --output tsv
```

**Note**: You can use either password. If you regenerate passwords, make sure to update the GitHub secret.

---

## Step 3: Add Secrets to GitHub

### Method 1: Using GitHub Web Interface (Recommended)

1. **Navigate to your repository** on GitHub
2. **Go to Settings**:
   - Click the **Settings** tab (top menu)
3. **Access Secrets**:
   - In the left sidebar, click **Secrets and variables** → **Actions**
4. **Add each secret**:
   - Click **New repository secret**
   - For each secret:
     - **Name**: Enter the secret name (e.g., `AZURE_CONTAINER_REGISTRY`)
     - **Secret**: Paste the value
     - Click **Add secret**
   - Repeat for all three secrets

### Method 2: Using GitHub CLI

```bash
# Install GitHub CLI if not already installed
# macOS: brew install gh
# Windows: winget install GitHub.cli

# Login to GitHub
gh auth login

# Add secrets
gh secret set AZURE_CONTAINER_REGISTRY --body "yourregistryname.azurecr.io"
gh secret set AZURE_CONTAINER_REGISTRY_USERNAME --body "yourregistryname"
gh secret set AZURE_CONTAINER_REGISTRY_PASSWORD --body "your-password-here"
```

---

## Step 4: Verify Secrets Are Set

1. Go to your repository on GitHub
2. Navigate to **Settings** → **Secrets and variables** → **Actions**
3. You should see all three secrets listed:
   - ✅ `AZURE_CONTAINER_REGISTRY`
   - ✅ `AZURE_CONTAINER_REGISTRY_USERNAME`
   - ✅ `AZURE_CONTAINER_REGISTRY_PASSWORD`

**Note**: GitHub will show masked values (only the last 4 characters visible) for security.

---

## Step 5: Test the Workflow

1. **Push to main branch** or **manually trigger the workflow**:
   - Go to **Actions** tab in your repository
   - Select **Build and Push to Azure Container Registry** workflow
   - Click **Run workflow** → **Run workflow**

2. **Monitor the workflow**:
   - The workflow should:
     - Checkout code
     - Set up JDK 17
     - Log in to ACR
     - Build Docker image
     - Push image to ACR

3. **Verify in Azure Portal**:
   - Go to your Container Registry
   - Click **Repositories** → **leaderboard-platform**
   - You should see images tagged with commit SHA and `latest`

---

## Troubleshooting

### Issue: "unauthorized: authentication required"

**Solution**: 
- Verify the username and password are correct
- Make sure admin user is enabled on your ACR
- Regenerate passwords if needed:
  ```bash
  az acr credential renew --name <your-registry-name> --password-name password1
  ```

### Issue: "repository name must be lowercase"

**Solution**: 
- Ensure your registry name is all lowercase
- Registry names cannot contain hyphens or special characters

### Issue: "name already exists"

**Solution**: 
- ACR names must be globally unique
- Try a different name (e.g., add your initials or numbers)

### Issue: Workflow fails with "permission denied"

**Solution**:
- Ensure secrets are set correctly
- Check that the secret names match exactly (case-sensitive)
- Verify you have write access to the repository

---

## Security Best Practices

1. **Never commit secrets to code**: Always use GitHub Secrets
2. **Rotate passwords regularly**: Regenerate ACR passwords periodically
3. **Use managed identity** (advanced): For production, consider using Azure Managed Identity instead of admin credentials
4. **Limit access**: Only grant access to necessary team members
5. **Monitor usage**: Review ACR access logs regularly

---

## Next Steps

After setting up secrets and successfully pushing images:

1. **Create Azure Container Apps Environment**
2. **Create Container App** using the image from ACR
3. **Configure environment variables** for your application
4. **Set up scaling rules** and health probes

See `AZURE_HOSTING_PLAN.md` for detailed deployment instructions.
