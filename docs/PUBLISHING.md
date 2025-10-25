# Publishing Guide

This guide explains how to publish the Scala Multipart Client library to Maven Central or GitHub Packages.

## Table of Contents

1. [Prerequisites](#prerequisites)
2. [Option A: Publishing to Maven Central (Sonatype)](#option-a-publishing-to-maven-central-sonatype)
3. [Option B: Publishing to GitHub Packages](#option-b-publishing-to-github-packages)
4. [Version Management](#version-management)
5. [Release Checklist](#release-checklist)

---

## Prerequisites

### Required Tools

- **SBT 1.9.8+** installed
- **GPG** for signing artifacts (Maven Central only)
- **Sonatype Account** (for Maven Central) OR **GitHub Account** (for GitHub Packages)

### Install GPG (for Maven Central)

**macOS:**
```bash
brew install gnupg
```

**Linux:**
```bash
sudo apt-get install gnupg
# or
sudo yum install gnupg
```

**Windows:**
Download from https://www.gnupg.org/download/

---

## Option A: Publishing to Maven Central (Sonatype)

### Step 1: Create Sonatype Account

1. Go to https://issues.sonatype.org/secure/Signup!default.jspa
2. Create an account
3. Create a JIRA ticket to claim your `io.github.ahoubouby` group ID
   - Project: Community Support - Open Source Project Repository Hosting (OSSRH)
   - Issue Type: New Project
   - Group Id: `io.github.ahoubouby`
   - Project URL: `https://github.com/ahoubouby/scala-multipart-client`
   - SCM URL: `https://github.com/ahoubouby/scala-multipart-client.git`
4. Wait for approval (usually within 2 business days)

### Step 2: Setup GPG Key

```bash
# Generate a GPG key
gpg --gen-key

# List your keys
gpg --list-keys

# Publish your public key (replace KEY_ID with your key ID)
gpg --keyserver hkps://keys.openpgp.org --send-keys KEY_ID
```

### Step 3: Configure SBT Credentials

Create or edit `~/.sbt/1.0/sonatype.sbt`:

```scala
credentials += Credentials(
  "Sonatype Nexus Repository Manager",
  "s01.oss.sonatype.org",
  "your-sonatype-username",
  "your-sonatype-password"
)
```

Create or edit `~/.sbt/1.0/plugins/gpg.sbt`:

```scala
addSbtPlugin("com.github.sbt" % "sbt-pgp" % "2.2.1")
```

### Step 4: Configure GPG in SBT

Add to `project/plugins.sbt` in your project:

```scala
addSbtPlugin("com.github.sbt" % "sbt-pgp" % "2.2.1")
```

### Step 5: Publish

```bash
# Test local publishing first
sbt publishLocal

# Publish to Sonatype staging
sbt publishSigned

# Release to Maven Central
sbt sonatypeBundleRelease

# Or use the combined command
sbt clean compile test publishSigned sonatypeBundleRelease
```

### Step 6: Verify Publication

After 2-4 hours, your library will be available on Maven Central:
https://repo1.maven.org/maven2/io/github/ahoubouby/scala-multipart-client_2.13/

---

## Option B: Publishing to GitHub Packages

### Step 1: Generate GitHub Token

1. Go to GitHub Settings → Developer settings → Personal access tokens → Tokens (classic)
2. Generate new token with `write:packages` and `read:packages` permissions
3. Save the token securely

### Step 2: Configure Build

Update `build.sbt` (uncomment the GitHub Packages section):

```scala
publishTo := Some(
  "GitHub Package Registry" at "https://maven.pkg.github.com/ahoubouby/scala-multipart-client"
)

publishMavenStyle := true

credentials += Credentials(
  "GitHub Package Registry",
  "maven.pkg.github.com",
  "ahoubouby",
  sys.env.getOrElse("GITHUB_TOKEN", "")
)
```

### Step 3: Set Environment Variable

```bash
export GITHUB_TOKEN=your_github_token
```

Or add to `~/.sbt/1.0/github.sbt`:

```scala
credentials += Credentials(
  "GitHub Package Registry",
  "maven.pkg.github.com",
  "ahoubouby",
  "YOUR_GITHUB_TOKEN"
)
```

### Step 4: Publish

```bash
sbt publish
```

### Step 5: Verify

Check your GitHub repository → Packages section:
https://github.com/ahoubouby/scala-multipart-client/packages

---

## Version Management

### Semantic Versioning

Follow [Semantic Versioning](https://semver.org/):
- **MAJOR**: Incompatible API changes
- **MINOR**: Add functionality in a backward-compatible manner
- **PATCH**: Backward-compatible bug fixes

### Version Workflow

1. **Development**: Use `-SNAPSHOT` suffix (e.g., `0.2.0-SNAPSHOT`)
2. **Release**: Remove `-SNAPSHOT` (e.g., `0.2.0`)
3. **Post-Release**: Bump to next `-SNAPSHOT` version

Example in `build.sbt`:

```scala
// Development
version := "0.2.0-SNAPSHOT"

// Release
version := "0.2.0"

// Next development cycle
version := "0.3.0-SNAPSHOT"
```

---

## Release Checklist

### Pre-Release

- [ ] All tests pass: `sbt test`
- [ ] Code is formatted: `sbt scalafmt`
- [ ] Documentation is up-to-date
- [ ] CHANGELOG.md is updated
- [ ] Version number is updated in `build.sbt` (no `-SNAPSHOT`)
- [ ] Commit all changes: `git commit -am "Release v0.1.0"`
- [ ] Tag the release: `git tag -a v0.1.0 -m "Release version 0.1.0"`

### Release

- [ ] Run release check: `sbt releaseCheck`
- [ ] Publish artifacts: `sbt publishSigned` (Maven Central) or `sbt publish` (GitHub Packages)
- [ ] Release to Maven Central: `sbt sonatypeBundleRelease` (Maven Central only)

### Post-Release

- [ ] Push commits: `git push origin main`
- [ ] Push tags: `git push origin v0.1.0`
- [ ] Create GitHub Release with release notes
- [ ] Update version to next SNAPSHOT: `version := "0.2.0-SNAPSHOT"`
- [ ] Announce release (if applicable)

---

## Automated Publishing with GitHub Actions

Create `.github/workflows/publish.yml`:

```yaml
name: Publish

on:
  release:
    types: [created]

jobs:
  publish:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3

      - name: Set up JDK 11
        uses: actions/setup-java@v3
        with:
          java-version: '11'
          distribution: 'temurin'

      - name: Publish to Maven Central
        env:
          PGP_PASSPHRASE: ${{ secrets.PGP_PASSPHRASE }}
          PGP_SECRET: ${{ secrets.PGP_SECRET }}
          SONATYPE_USERNAME: ${{ secrets.SONATYPE_USERNAME }}
          SONATYPE_PASSWORD: ${{ secrets.SONATYPE_PASSWORD }}
        run: |
          echo "$PGP_SECRET" | base64 --decode | gpg --import --no-tty --batch --yes
          sbt publishSigned sonatypeBundleRelease
```

### Required GitHub Secrets

Add these to your repository Settings → Secrets:

- `PGP_PASSPHRASE`: Your GPG key passphrase
- `PGP_SECRET`: Your GPG private key (base64 encoded)
  ```bash
  gpg --armor --export-secret-keys YOUR_KEY_ID | base64
  ```
- `SONATYPE_USERNAME`: Your Sonatype username
- `SONATYPE_PASSWORD`: Your Sonatype password

---

## Troubleshooting

### GPG Signing Issues

```bash
# If gpg agent fails
export GPG_TTY=$(tty)

# Test signing
echo "test" | gpg --clearsign
```

### Sonatype Connection Issues

```bash
# Verify credentials
sbt sonatypeLog

# Check staging repositories
sbt sonatypeList
```

### Publication Verification

```bash
# Check if artifact exists (Maven Central)
curl -I https://repo1.maven.org/maven2/io/github/ahoubouby/scala-multipart-client_2.13/0.1.0/scala-multipart-client_2.13-0.1.0.pom

# Check GitHub Packages
curl -H "Authorization: token YOUR_GITHUB_TOKEN" \
  https://maven.pkg.github.com/ahoubouby/scala-multipart-client/io/github/ahoubouby/scala-multipart-client_2.13/maven-metadata.xml
```

---

## Additional Resources

- [Sonatype OSSRH Guide](https://central.sonatype.org/publish/publish-guide/)
- [SBT Publishing Documentation](https://www.scala-sbt.org/1.x/docs/Publishing.html)
- [GitHub Packages for Scala](https://docs.github.com/en/packages/working-with-a-github-packages-registry/working-with-the-apache-maven-registry)
- [Semantic Versioning](https://semver.org/)
