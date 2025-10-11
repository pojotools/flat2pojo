# Coverage Badge Setup

This project uses a GitHub Gist + shields.io to display the coverage badge.

## One-Time Setup

### 1. Create a GitHub Personal Access Token

1. Go to https://github.com/settings/tokens
2. Click "Generate new token" → "Generate new token (classic)"
3. Name it: `flat2pojo-coverage-badge`
4. Select scope: **gist** (only this one is needed)
5. Click "Generate token"
6. **Copy the token** (you won't see it again!)

### 2. Create a Gist

1. Go to https://gist.github.com/
2. Create a new gist:
   - Filename: `flat2pojo-coverage.json`
   - Content:
     ```json
     {
       "schemaVersion": 1,
       "label": "coverage",
       "message": "0%",
       "color": "red"
     }
     ```
3. Click "Create public gist"
4. **Copy the Gist ID** from the URL (e.g., `https://gist.github.com/kyranrana/abc123def456` → ID is `abc123def456`)

### 3. Add GitHub Secrets

Go to your repository → Settings → Secrets and variables → Actions → "New repository secret"

Add two secrets:
- Name: `GIST_SECRET`, Value: [paste your token from step 1]
- Name: `GIST_ID`, Value: [paste your gist ID from step 2]

### 4. Update README.md

Replace `YOUR_GIST_ID` in README.md with your actual Gist ID:

```markdown
[![Coverage](https://img.shields.io/endpoint?url=https://gist.githubusercontent.com/kyranrana/YOUR_GIST_ID/raw/flat2pojo-coverage.json)](https://github.com/pojotools/flat2pojo/actions/workflows/ci.yml)
```

## How It Works

1. After every successful build on `main` branch:
   - JaCoCo generates coverage reports
   - CI extracts the coverage percentage from `jacoco.csv`
   - CI determines badge color based on coverage:
     - ≥80%: bright green
     - ≥60%: yellow
     - ≥40%: orange
     - <40%: red
   - CI updates your Gist with the new coverage data
   - shields.io reads from the Gist and generates the badge

2. The badge auto-updates on every push to main!

## Troubleshooting

- **Badge shows "invalid"**: Check that the Gist ID in README.md is correct
- **Badge doesn't update**: Check that `GIST_SECRET` and `GIST_ID` secrets are set correctly
- **Build succeeds but badge not updated**: Check the "Extract and upload coverage" step in CI logs
