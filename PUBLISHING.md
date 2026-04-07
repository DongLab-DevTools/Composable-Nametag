# Publishing Guide

Composable-Nametag 라이브러리를 배포하는 방법을 설명합니다.

## 배포 대상 아티팩트 (3개)

| 아티팩트 | 모듈 | 타입 | 설명 |
|---------|------|------|------|
| `compose-debug-overlay-compiler` | `compiler/` | JAR | Kotlin IR Transformer |
| `compose-debug-overlay-runtime` | `runtime/` | AAR | 라벨 UI + 토글 |
| `compose-debug-overlay-gradle` | `gradle-plugin/` | JAR | 사용자 진입점 플러그인 |

---

## 1. mavenLocal 배포 (로컬 개발/테스트용)

GPG 서명이 불필요합니다.

```bash
# compiler + runtime
./gradlew :compiler:publishToMavenLocal :runtime:publishToMavenLocal

# gradle-plugin (included build이므로 별도 실행)
cd gradle-plugin && ../gradlew publishToMavenLocal && cd ..
```

배포 위치: `~/.m2/repository/com/donglab/compose/debug/`

### 소비자 프로젝트에서 사용

```kotlin
// settings.gradle.kts
pluginManagement {
    repositories {
        mavenLocal()
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}
dependencyResolutionManagement {
    repositories {
        mavenLocal()
        google()
        mavenCentral()
    }
}

// app/build.gradle.kts
plugins {
    id("com.donglab.compose.debug.overlay") version "1.0.0"
}
```

---

## 2. Maven Central 배포 (공개 배포)

### 사전 준비 (최초 1회)

#### Step 1: Sonatype 계정 생성
1. https://s01.oss.sonatype.org 접속
2. 회원 가입
3. New Project Ticket 생성하여 `com.donglab.compose.debug` groupId 승인 요청
   - groupId 소유 증명: GitHub repo에 Sonatype이 제공하는 코드 추가

> **참고**: 2024년 이후 Sonatype Central Portal(https://central.sonatype.com)로 이관 중.
> Central Portal을 사용하면 JIRA 티켓 없이 즉시 namespace 등록 가능.

#### Step 2: GPG 키 생성

```bash
# GPG 키 생성
gpg --full-generate-key
# → RSA, 4096 bit, 이름/이메일 입력

# 키 ID 확인 (마지막 8자리)
gpg --list-secret-keys --keyid-format short
# sec   rsa4096/ABCD1234 2024-01-01 [SC]
#                ^^^^^^^^ 이 부분이 keyId

# 공개 키를 키서버에 업로드 (Maven Central 검증용)
gpg --keyserver keyserver.ubuntu.com --send-keys ABCD1234
gpg --keyserver keys.openpgp.org --send-keys ABCD1234

# secret keyring 내보내기 (Gradle signing 플러그인용)
gpg --export-secret-keys ABCD1234 > ~/.gnupg/secring.gpg
```

#### Step 3: ~/.gradle/gradle.properties 설정

```properties
# Sonatype OSSRH 자격증명
mavenCentralUsername=<Sonatype 사용자명 또는 토큰>
mavenCentralPassword=<Sonatype 비밀번호 또는 토큰>

# GPG 서명
signing.keyId=ABCD1234
signing.password=<GPG passphrase>
signing.secretKeyRingFile=/Users/<username>/.gnupg/secring.gpg
```

> ⚠️ 이 파일은 **프로젝트가 아닌 홈 디렉토리**의 `~/.gradle/gradle.properties`에 설정합니다.
> Git에 올라가지 않습니다.

### 배포 실행

```bash
# compiler + runtime
./gradlew :compiler:publishAllPublicationsToMavenCentralRepository
./gradlew :runtime:publishAllPublicationsToMavenCentralRepository

# gradle-plugin
cd gradle-plugin && ../gradlew publishAllPublicationsToMavenCentralRepository && cd ..
```

### Staging → Release

1. https://s01.oss.sonatype.org 로그인
2. **Staging Repositories** 탭에서 업로드된 staging repo 확인
3. **Close** → 검증 통과 확인
4. **Release** → Maven Central에 공개 (보통 10~30분 후 반영)

### 소비자 프로젝트에서 사용

```kotlin
// settings.gradle.kts — mavenLocal 불필요
pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}

// app/build.gradle.kts
plugins {
    id("com.donglab.compose.debug.overlay") version "1.0.0"
}
```

---

## 버전 올리기

1. `gradle.properties`의 `VERSION` 수정
2. `gradle-plugin/gradle.properties`의 `VERSION` 동기화
3. `ComposeDebugOverlayPlugin.kt`의 `VERSION` 상수 동기화
4. 배포 실행

---

## CI/CD 자동화 (GitHub Actions)

`.github/workflows/publish.yml`로 태그 push 시 자동 배포 가능:

```yaml
name: Publish to Maven Central
on:
  push:
    tags: ['v*']

jobs:
  publish:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: 21
      - name: Publish
        env:
          ORG_GRADLE_PROJECT_mavenCentralUsername: ${{ secrets.MAVEN_USERNAME }}
          ORG_GRADLE_PROJECT_mavenCentralPassword: ${{ secrets.MAVEN_PASSWORD }}
          ORG_GRADLE_PROJECT_signingKeyId: ${{ secrets.GPG_KEY_ID }}
          ORG_GRADLE_PROJECT_signingPassword: ${{ secrets.GPG_PASSPHRASE }}
          ORG_GRADLE_PROJECT_signingKey: ${{ secrets.GPG_PRIVATE_KEY }}
        run: |
          ./gradlew :compiler:publishAllPublicationsToMavenCentralRepository
          ./gradlew :runtime:publishAllPublicationsToMavenCentralRepository
          cd gradle-plugin && ../gradlew publishAllPublicationsToMavenCentralRepository
```
