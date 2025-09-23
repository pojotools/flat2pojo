# Publishing flat2pojo to Maven Central

This guide covers how to publish flat2pojo to Maven Central Repository.

## Prerequisites

### 1. **Sonatype Account Setup**
1. Create account at [Sonatype Central](https://central.sonatype.com/)
2. Verify your namespace `io.flat2pojo`
3. Generate deployment token

### 2. **GPG Key Setup**
```bash
# Generate GPG key
gpg --gen-key

# List keys
gpg --list-secret-keys --keyid-format LONG

# Export public key (replace KEY_ID)
gpg --armor --export KEY_ID

# Upload to key server
gpg --keyserver keyserver.ubuntu.com --send-keys KEY_ID
```

### 3. **Maven Settings Configuration**

Add to `~/.m2/settings.xml`:

```xml
<settings>
  <servers>
    <server>
      <id>central</id>
      <username>YOUR_SONATYPE_TOKEN</username>
      <password>YOUR_SONATYPE_PASSWORD</password>
    </server>
    <server>
      <id>ossrh</id>
      <username>YOUR_SONATYPE_TOKEN</username>
      <password>YOUR_SONATYPE_PASSWORD</password>
    </server>
  </servers>

  <profiles>
    <profile>
      <id>ossrh</id>
      <activation>
        <activeByDefault>true</activeByDefault>
      </activation>
      <properties>
        <gpg.executable>gpg</gpg.executable>
        <gpg.passphrase>YOUR_GPG_PASSPHRASE</gpg.passphrase>
      </properties>
    </profile>
  </profiles>
</settings>
```

## Publishing Process

### 1. **Prepare Release**

```bash
# Update version (remove SNAPSHOT)
mvn versions:set -DnewVersion=0.1.0

# Commit version change
git add .
git commit -m "Release version 0.1.0"
git tag v0.1.0
```

### 2. **Deploy to Staging**

```bash
# Clean build and deploy
mvn clean deploy -P release

# Or manually deploy each module
mvn clean deploy -pl flat2pojo-core,flat2pojo-jackson,flat2pojo-spi
```

### 3. **Publish from Staging**

```bash
# Promote to Central (using new Central Publishing)
mvn org.sonatype.central:central-publishing-maven-plugin:publish
```

### 4. **Post-Release**

```bash
# Update to next SNAPSHOT version
mvn versions:set -DnewVersion=0.2.0-SNAPSHOT

# Commit and push
git add .
git commit -m "Prepare for next development iteration"
git push origin main --tags
```

## Release Profile

Add this profile to your `pom.xml` for release builds:

```xml
<profiles>
    <profile>
        <id>release</id>
        <build>
            <plugins>
                <plugin>
                    <groupId>org.apache.maven.plugins</groupId>
                    <artifactId>maven-source-plugin</artifactId>
                    <executions>
                        <execution>
                            <id>attach-sources</id>
                            <goals>
                                <goal>jar-no-fork</goal>
                            </goals>
                        </execution>
                    </executions>
                </plugin>

                <plugin>
                    <groupId>org.apache.maven.plugins</groupId>
                    <artifactId>maven-javadoc-plugin</artifactId>
                    <executions>
                        <execution>
                            <id>attach-javadocs</id>
                            <goals>
                                <goal>jar</goal>
                            </goals>
                        </execution>
                    </executions>
                </plugin>

                <plugin>
                    <groupId>org.apache.maven.plugins</groupId>
                    <artifactId>maven-gpg-plugin</artifactId>
                    <executions>
                        <execution>
                            <id>sign-artifacts</id>
                            <phase>verify</phase>
                            <goals>
                                <goal>sign</goal>
                            </goals>
                        </execution>
                    </executions>
                </plugin>
            </plugins>
        </build>
    </profile>
</profiles>
```

## Automated CI/CD Publishing

### GitHub Actions Workflow

Create `.github/workflows/release.yml`:

```yaml
name: Release to Maven Central

on:
  push:
    tags:
      - 'v*'

jobs:
  publish:
    runs-on: ubuntu-latest

    steps:
    - uses: actions/checkout@v4

    - name: Set up JDK 21
      uses: actions/setup-java@v4
      with:
        java-version: '21'
        distribution: 'corretto'

    - name: Cache Maven packages
      uses: actions/cache@v3
      with:
        path: ~/.m2
        key: ${{ runner.os }}-m2-${{ hashFiles('**/pom.xml') }}

    - name: Import GPG key
      uses: crazy-max/ghaction-import-gpg@v6
      with:
        gpg_private_key: ${{ secrets.GPG_PRIVATE_KEY }}
        passphrase: ${{ secrets.GPG_PASSPHRASE }}

    - name: Deploy to Maven Central
      run: |
        mvn clean deploy -P release \
          -Dgpg.passphrase="${{ secrets.GPG_PASSPHRASE }}" \
          -Dcentral.username="${{ secrets.SONATYPE_USERNAME }}" \
          -Dcentral.password="${{ secrets.SONATYPE_PASSWORD }}"
      env:
        MAVEN_GPG_PASSPHRASE: ${{ secrets.GPG_PASSPHRASE }}
```

## Quick Commands

```bash
# Test deployment (without GPG signing)
mvn clean deploy -Dgpg.skip=true

# Deploy specific modules only
mvn clean deploy -pl flat2pojo-core,flat2pojo-jackson

# Check if artifacts are valid for Central
mvn clean verify -P release

# Release and publish in one command
mvn clean deploy -P release && mvn central-publishing:publish
```

## Validation

After publishing, verify at:
- [Maven Central Search](https://search.maven.org/)
- [Sonatype Central Repository](https://central.sonatype.com/)

Your library will be available as:

```xml
<dependency>
    <groupId>io.flat2pojo</groupId>
    <artifactId>flat2pojo-jackson</artifactId>
    <version>0.1.0</version>
</dependency>
```

## Notes

- First-time namespace verification can take 1-2 business days
- Artifacts appear in Maven Central within 30 minutes of successful publication
- Keep GPG keys and Sonatype credentials secure
- Use environment variables in CI/CD for sensitive data