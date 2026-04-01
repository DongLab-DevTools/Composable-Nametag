# Composable-Nametag

Compose 화면에 표시되는 모든 `@Composable` 함수의 이름을 라벨로 오버레이하는 디버그 도구입니다.

**기존 코드를 수정하지 않고**, Kotlin Compiler Plugin(KCP)이 컴파일 시점에 자동으로 주입합니다.

## 구조

```
Composable-Nametag/
├── compiler/       ← Kotlin Compiler Plugin (IR Transformer)
├── runtime/        ← Android Library (라벨 렌더링 + 토글)
├── gradle-plugin/  ← Gradle Plugin (사용자 진입점)
└── app/            ← 샘플 앱
```

## 사용법

### 1. 플러그인 적용

Compose를 사용하는 모듈에 적용합니다. **compose 플러그인보다 먼저 선언해야 합니다.**

#### 방법 A. `plugins {}` 블록으로 직접 적용

```kotlin
// Compose를 사용하는 모듈의 build.gradle.kts
plugins {
    id("com.donglab.compose.debug.overlay") version "1.0.0" // compose 플러그인보다 먼저
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.21"
    // ...
}
```

#### 방법 B. Convention Plugin을 통한 적용

build-logic 등 Convention Plugin 구조를 사용하는 프로젝트에서는 플러그인 아티팩트를 의존성으로 추가한 뒤 Convention Plugin 내부에서 적용합니다.

```kotlin
// build-logic/build.gradle.kts
dependencies {
    implementation("com.donglab.compose.debug:compose-debug-overlay-gradle:1.0.0")
}
```

```kotlin
// Convention Plugin 내부 (예: AndroidComposeConventionPlugin.kt)
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

### 2. 활성화

```kotlin
// Application 또는 원하는 시점에서
ComposeDebugConfig.enabled = true
```

끝입니다. 모든 `@Composable` 함수에 이름 라벨이 표시됩니다.

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

## 필터링 규칙

| 조건 | 처리 |
|------|------|
| 대문자로 시작하는 @Composable | ✅ 라벨 표시 |
| 소문자로 시작 (remember, modifier 등) | ❌ 스킵 |
| 람다 / anonymous | ❌ 스킵 |
| Property accessor | ❌ 스킵 |
| `__` 접두사 | ❌ 스킵 |

## Kotlin 버전 호환성

컴파일러 플러그인은 Kotlin IR 내부 API를 사용하므로 버전 종속적입니다.

- **지원 버전**: 2.1.21
- **미지원 버전**: 경고 1회 출력 후 컴파일러 플러그인만 비활성화. 빌드는 정상 진행.

```
⚠️  compose-debug-overlay: Kotlin X.Y.Z is not supported.
    → Your build and app are NOT affected.
```

## 기술 스택

- Kotlin 2.1.21
- AGP 8.6.1
- Compose BOM 2025.05.01
- Gradle 8.7

## 라이선스

Apache License 2.0
