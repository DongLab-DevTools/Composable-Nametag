# Composable-Nametag

[![Platform](https://img.shields.io/badge/platform-Android-3DDC84?style=flat-square&logo=android)](https://developer.android.com)
[![Min SDK](https://img.shields.io/badge/min%20sdk-24-green?style=flat-square)](https://developer.android.com)

**[English README](./README.md)**

## 개요

<img width="2048" height="1152" alt="image" src="https://github.com/user-attachments/assets/41f2bab7-b145-4060-97f9-1346fd98b07b" />


<br>
<br>

Compose 화면에 표시되는 모든 `@Composable` 함수의 이름을 라벨로 오버레이하는 디버그 도구입니다.

**기존 코드를 수정하지 않고**, Kotlin Compiler Plugin(KCP)이 컴파일 시점에 자동으로 주입합니다.
비활성화 시 런타임 오버헤드는 제로입니다.

<br>

## 스크린샷
<img width="2048" height="1333" alt="image" src="https://github.com/user-attachments/assets/4005f1ff-76e5-40b8-aa29-12748b22c09f" />

<br>

## 주요 기능

- **무수정 자동 주입**: Kotlin Compiler Plugin이 컴파일 시점에 라벨을 삽입 — 기존 코드 수정 불필요
- **비활성화 시 제로 오버헤드**: `ComposeDebugConfig.enabled = false` (기본값)이면 즉시 반환
- **스마트 필터링**: PascalCase Composable 함수만 라벨 표시; 람다, `remember`, 프로퍼티 접근자 등은 스킵
- **Kotlin 버전 안전성**: 미지원 Kotlin 버전에서는 컴파일러 플러그인만 비활성화 — 빌드는 절대 깨지지 않음
- **컬러풀 스태거 라벨**: 함수마다 고유한 색상과 수직 오프셋으로 겹침 방지

<br>

## 설치

### 방법 A. `plugins {}` 블록으로 직접 적용

**Compose를 사용하는 모듈**의 `build.gradle.kts`에 플러그인을 적용합니다.
**반드시 Compose 플러그인보다 먼저 선언해야 합니다.**

```kotlin
// feature/home/build.gradle.kts (Compose 모듈)
plugins {
    id("com.donglab.compose.debug.overlay") version "1.0.0" // compose 플러그인보다 먼저
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.21"
    // ...
}
```

> 별도의 `implementation` 의존성 추가는 필요 없습니다 — 플러그인이 runtime 라이브러리를 자동으로 추가합니다.

### 방법 B. Convention Plugin을 통한 적용

build-logic 등 Convention Plugin 구조를 사용하는 프로젝트에서의 적용 방법입니다.

**Step 1.** `build-logic/build.gradle.kts`에 플러그인 아티팩트를 추가합니다:

```kotlin
// build-logic/build.gradle.kts
dependencies {
    implementation("com.donglab.compose.debug:compose-debug-overlay-gradle:1.0.0")
}
```

**Step 2.** Compose Convention Plugin 내부에서 **Compose 플러그인보다 먼저** 적용합니다:

```kotlin
// 예: AndroidComposeConventionPlugin.kt
class AndroidComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.donglab.compose.debug.overlay") // compose 플러그인보다 먼저
            pluginManager.apply("org.jetbrains.kotlin.plugin.compose")
            // ...
        }
    }
}
```

<br>

### 요구사항

- Android API 24 (Android 7.0) 이상
- Kotlin 2.1.21
- Jetpack Compose (BOM 2025.05.01 또는 호환 버전)

<br>

## 사용법

### 오버레이 활성화

```kotlin
// Application 또는 원하는 시점에서
ComposeDebugConfig.enabled = true
```

끝입니다. 모든 `@Composable` 함수에 이름 라벨이 표시됩니다.

<br>

## 동작 원리

```
[ 컴파일 타임 ]
@Composable
fun HomeScreen() {
    Column { ... }
}

       ↓ KCP가 자동 변환

@Composable
fun HomeScreen() {
    __debugComposableName("HomeScreen")  ← 삽입됨
    Column { ... }
}

[ 런타임 ]
enabled=true  → 라벨 표시
enabled=false → 즉시 반환 (오버헤드 제로)
```

<br>

## 필터링 규칙

| 조건 | 처리 |
|------|------|
| 대문자로 시작하는 @Composable | ✅ 라벨 표시 |
| 소문자로 시작 (remember, modifier 등) | ❌ 스킵 |
| 람다 / anonymous | ❌ 스킵 |
| Property accessor | ❌ 스킵 |
| `__` 접두사 | ❌ 스킵 |

<br>

## Kotlin 버전 호환성

컴파일러 플러그인은 Kotlin IR 내부 API를 사용하므로 버전 종속적입니다.

- **지원 버전**: 2.1.21
- **미지원 버전**: 경고 1회 출력 후 컴파일러 플러그인만 비활성화. 빌드는 정상 진행.

```
⚠️  compose-debug-overlay: Kotlin X.Y.Z is not supported.
    → Your build and app are NOT affected.
```

<br>

## 기술 스택

- Kotlin 2.1.21
- AGP 8.6.1
- Compose BOM 2025.05.01
- Gradle 8.7

## 라이선스

Apache License 2.0
