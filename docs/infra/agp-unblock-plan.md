# AGP 8.4.1 Unblock Plan - Session 14Z
**Дата создания**: 24.11.2025  
**Версия**: 1.0  
**Статус**: BLOCKED - Требуется вмешательство владельца проекта

## Содержание
- [Анализ блокировки](#анализ-блокировки)
- [Доступные стратегии](#доступные-стратегии)
- [Стратегия A: Корпоративный прокси](#стратегия-a-корпоративный-прокси)
- [Стратегия B: Локальное зеркало Maven](#стратегия-b-локальное-зеркало-maven)
- [Стратегия C: Downgrade AGP](#стратегия-c-downgrade-agp)
- [Стратегия D: GitHub Actions с Google Maven](#стратегия-d-github-actions-с-google-maven)
- [Рекомендации](#рекомендации)

---

## Анализ блокировки

### Текущая ситуация
**Android Gradle Plugin (AGP) версия**: 8.4.1  
**Ошибка сборки**:
```
Plugin [id: 'com.android.application', version: '8.4.1', apply: false] was not found in any of the following sources
```

### Диагностика сетевого доступа (24.11.2025 04:41 UTC)

| Репозиторий          | URL                                        | Статус       | Код ошибки             |
| -------------------- | ------------------------------------------ | ------------ | ---------------------- |
| Google Maven         | https://dl.google.com                      | ❌ БЛОКИРОВАН | Could not resolve host |
| Aliyun Google Mirror | https://maven.aliyun.com/repository/google | ❌ БЛОКИРОВАН | Could not resolve host |
| Aliyun Public Mirror | https://maven.aliyun.com/repository/public | ❌ БЛОКИРОВАН | Could not resolve host |
| Maven Central        | https://repo1.maven.org/maven2/            | ✅ ДОСТУПЕН   | 200 OK                 |
| Gradle Plugin Portal | https://plugins.gradle.org/m2/             | ✅ ДОСТУПЕН   | 200 OK                 |

### Текущая конфигурация репозиториев
**Файл**: `android/settings.gradle.kts`

```kotlin
pluginManagement {
    repositories {
        maven { url = uri("https://maven.aliyun.com/repository/google") }  // НЕДОСТУПЕН
        maven { url = uri("https://maven.aliyun.com/repository/public") }   // НЕДОСТУПЕН
        gradlePluginPortal()  // ДОСТУПЕН, но AGP там нет
        google()              // НЕДОСТУПЕН (dl.google.com)
        mavenCentral()        // ДОСТУПЕН, но AGP там только старые версии (≤2.3.0)
    }
}
```

### Причины блокировки
1. **Сетевая изоляция**: Среда выполнения (GitHub Actions runner) не имеет доступа к:
   - `dl.google.com` (официальный Google Maven)
   - `maven.aliyun.com` (китайские зеркала)
   
2. **Недоступность AGP в публичных репозиториях**:
   - Maven Central содержит только AGP ≤ 2.3.0
   - Gradle Plugin Portal НЕ содержит AGP (это не Gradle Core Plugin)
   - AGP публикуется ТОЛЬКО в Google Maven Repository

3. **Отсутствие кэша**:
   - Локальный Gradle cache (`~/.gradle/caches/`) пустой
   - Нет pre-downloaded артефактов

### Попытки решения (Session 10C)
История из предыдущих сессий показывает, что были протестированы версии:
- AGP 8.4.1 ❌
- AGP 8.3.2 ❌
- AGP 8.2.2 ❌
- AGP 7.4.2 ❌

Все версии недоступны из-за сетевой блокировки.

---

## Доступные стратегии

### Сравнительная таблица стратегий

| Стратегия                    | Сложность | Время реализации | Требуется доступ                      | Риски                                           | Рекомендация    |
| ---------------------------- | --------- | ---------------- | ------------------------------------- | ----------------------------------------------- | --------------- |
| A. Корпоративный прокси      | Средняя   | 2-4 часа         | Настройка сети, учётные данные прокси | Зависимость от прокси, секреты                  | ⭐⭐⭐⭐            |
| B. Локальное зеркало         | Высокая   | 4-8 часов        | Сервер, права администратора          | Обслуживание, storage                           | ⭐⭐⭐             |
| C. Downgrade AGP             | Низкая    | 30 мин           | Нет                                   | Несовместимость с compileSdk 35, потеря функций | ⭐ (НЕ работает) |
| D. GitHub Actions с доступом | Низкая    | 1-2 часа         | Изменение политик CI/CD               | Зависимость от GitHub                           | ⭐⭐⭐⭐⭐           |

---

## Стратегия A: Корпоративный прокси

### Описание
Настройка HTTP/HTTPS прокси для доступа к `dl.google.com` через корпоративную сеть.

### Требования
1. **Сетевые права**:
   - Разрешение firewall для доступа к `dl.google.com` через прокси
   - IP-адрес и порт прокси-сервера
   
2. **Учётные данные** (если требуется аутентификация):
   - Proxy username
   - Proxy password
   
3. **Конфигурация SSL** (если используется HTTPS прокси):
   - CA сертификаты прокси
   - Настройка truststore

### Реализация

#### Шаг 1: Настройка Gradle Properties
**Файл**: `~/.gradle/gradle.properties` (НЕ коммитить!)

```properties
# HTTP Proxy
systemProp.http.proxyHost=proxy.company.com
systemProp.http.proxyPort=8080
systemProp.http.proxyUser=username
systemProp.http.proxyPassword=password

# HTTPS Proxy
systemProp.https.proxyHost=proxy.company.com
systemProp.https.proxyPort=8080
systemProp.https.proxyUser=username
systemProp.https.proxyPassword=password

# Non-proxy hosts (если нужны исключения)
systemProp.http.nonProxyHosts=localhost|127.0.0.1
```

**ВАЖНО**: Credentials должны храниться в `~/.gradle/gradle.properties`, а НЕ в репозитории!

#### Шаг 2: Настройка CI/CD (GitHub Actions)
**Файл**: `.github/workflows/android-build.yml`

```yaml
env:
  GRADLE_OPTS: >
    -Dhttp.proxyHost=${{ secrets.PROXY_HOST }}
    -Dhttp.proxyPort=${{ secrets.PROXY_PORT }}
    -Dhttps.proxyHost=${{ secrets.PROXY_HOST }}
    -Dhttps.proxyPort=${{ secrets.PROXY_PORT }}
```

**Секреты** (добавить в GitHub Settings → Secrets):
- `PROXY_HOST`
- `PROXY_PORT`
- `PROXY_USER` (опционально)
- `PROXY_PASSWORD` (опционально)

#### Шаг 3: Проверка доступа

```powershell
# Windows PowerShell (с прокси)
$Env:http_proxy = "http://proxy.company.com:8080"
$Env:https_proxy = "http://proxy.company.com:8080"
curl https://dl.google.com/android/repository/addons_list-5.xml
```

```bash
# Linux/macOS (с прокси)
export http_proxy=http://proxy.company.com:8080
export https_proxy=http://proxy.company.com:8080
curl https://dl.google.com/android/repository/addons_list-5.xml
```

Ожидаемый результат: XML с списком дополнений Android SDK.

#### Шаг 4: Сборка с обновлёнными зависимостями

```bash
cd android
./gradlew --refresh-dependencies clean assembleDebug
```

### Трудозатраты
- Получение доступа к прокси: 1-2 часа (зависит от процессов компании)
- Настройка и тестирование: 1-2 часа
- **Итого**: 2-4 часа

### Риски
1. **Секреты в CI/CD**: Необходимо корректно настроить GitHub Secrets
2. **Зависимость от прокси**: При недоступности прокси сборка не работает
3. **SSL/TLS проблемы**: Некоторые прокси требуют установки корневых сертификатов

### Преимущества
- ✅ Доступ ко всем версиям AGP
- ✅ Полная совместимость с текущей конфигурацией
- ✅ Можно использовать любые Google сервисы

### Недостатки
- ❌ Требуется корпоративная инфраструктура
- ❌ Сложность управления секретами
- ❌ Зависимость от внешнего сервиса

---

## Стратегия B: Локальное зеркало Maven

### Описание
Развёртывание локального Maven repository manager (Nexus или Artifactory) и загрузка AGP вручную.

### Требования
1. **Инфраструктура**:
   - Сервер с Docker или VM (минимум 4GB RAM, 50GB storage)
   - Статический IP или DNS
   
2. **Программное обеспечение**:
   - Nexus Repository OSS 3.x или JFrog Artifactory
   - Docker (опционально)
   
3. **Доступы**:
   - Права администратора на сервере
   - Доступ к серверу из GitHub Actions runner

### Реализация

#### Шаг 1: Развёртывание Nexus Repository

**Docker Compose** (`infra/docker/nexus/docker-compose.yml`):

```yaml
version: '3.8'
services:
  nexus:
    image: sonatype/nexus3:3.68.0
    container_name: nexus-maven
    restart: always
    ports:
      - "8081:8081"
    volumes:
      - nexus-data:/nexus-data
    environment:
      - INSTALL4J_ADD_VM_PARAMS=-Xms2g -Xmx2g

volumes:
  nexus-data:
```

Запуск:
```bash
cd infra/docker/nexus
docker-compose up -d
```

Первоначальный admin password:
```bash
docker exec nexus-maven cat /nexus-data/admin.password
```

#### Шаг 2: Настройка Nexus

1. **Создать proxy repository для Google Maven**:
   - Name: `google-maven-proxy`
   - Remote storage: `https://dl.google.com/dl/android/maven2/`
   - Proxy: настроить если требуется

2. **Создать hosted repository для ручной загрузки**:
   - Name: `android-artifacts-hosted`
   - Version policy: Mixed
   - Layout policy: Permissive

3. **Создать group repository**:
   - Name: `maven-public`
   - Members: `google-maven-proxy`, `android-artifacts-hosted`, `maven-central`

#### Шаг 3: Загрузка AGP 8.4.1 вручную

**Скачивание артефактов** (на машине с доступом к Google Maven):

```bash
# AGP Plugin
curl -O https://dl.google.com/dl/android/maven2/com/android/tools/build/gradle/8.4.1/gradle-8.4.1.jar
curl -O https://dl.google.com/dl/android/maven2/com/android/tools/build/gradle/8.4.1/gradle-8.4.1.pom

# AGP API
curl -O https://dl.google.com/dl/android/maven2/com/android/tools/build/gradle-api/8.4.1/gradle-api-8.4.1.jar
curl -O https://dl.google.com/dl/android/maven2/com/android/tools/build/gradle-api/8.4.1/gradle-api-8.4.1.pom

# И другие зависимости...
```

**Загрузка в Nexus**:
```bash
# Через UI: Nexus → Upload → Component Upload
# Или через Maven Deploy Plugin
```

#### Шаг 4: Настройка Gradle

**Файл**: `android/settings.gradle.kts`

```kotlin
pluginManagement {
    repositories {
        maven {
            url = uri("http://<nexus-server>:8081/repository/maven-public/")
            // Если требуется аутентификация:
            credentials {
                username = System.getenv("NEXUS_USERNAME") ?: ""
                password = System.getenv("NEXUS_PASSWORD") ?: ""
            }
        }
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven {
            url = uri("http://<nexus-server>:8081/repository/maven-public/")
            credentials {
                username = System.getenv("NEXUS_USERNAME") ?: ""
                password = System.getenv("NEXUS_PASSWORD") ?: ""
            }
        }
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}
```

#### Шаг 5: Сборка

```bash
cd android
./gradlew clean --refresh-dependencies assembleDebug
```

### Трудозатраты
- Развёртывание Nexus: 1-2 часа
- Загрузка всех артефактов AGP 8.4.1: 2-4 часа (много зависимостей)
- Настройка и тестирование: 1-2 часа
- **Итого**: 4-8 часов

### Риски
1. **Storage**: AGP и зависимости занимают ~5-10 GB
2. **Обслуживание**: Необходимо обновлять зеркало при новых версиях
3. **Недокументированные зависимости**: Сложно найти полный список артефактов

### Преимущества
- ✅ Полный контроль над артефактами
- ✅ Быстрая загрузка (локальная сеть)
- ✅ Независимость от внешних сервисов

### Недостатки
- ❌ Высокая сложность настройки
- ❌ Требуется постоянное обслуживание
- ❌ Затраты на инфраструктуру

---

## Стратегия C: Downgrade AGP

### Описание
Понижение версии AGP до версии, доступной в Maven Central (≤2.3.0).

### Анализ совместимости

| Компонент       | Текущая версия | Требуемая AGP | Доступная AGP               | Совместимость   |
| --------------- | -------------- | ------------- | --------------------------- | --------------- |
| compileSdk      | 35             | ≥8.1.0        | ≤2.3.0                      | ❌ НЕ СОВМЕСТИМО |
| Kotlin          | 1.9.24         | ≥7.3.0        | ≤2.3.0                      | ❌ НЕ СОВМЕСТИМО |
| Gradle          | 8.7            | ≥8.0.0        | ≤2.3.0 (требует Gradle 3.x) | ❌ НЕ СОВМЕСТИМО |
| Jetpack Compose | Да             | ≥7.0.0        | ≤2.3.0                      | ❌ НЕ СОВМЕСТИМО |

### Вывод
**Стратегия C НЕ РАБОТАЕТ**: AGP 2.3.0 несовместим с:
- Android API 35 (требуется AGP ≥8.1.0)
- Kotlin 1.9.24 (требуется AGP ≥7.3.0)
- Gradle 8.7 (требуется AGP ≥8.0.0)
- Современными библиотеками Jetpack

**Статус**: ❌ ОТКЛОНЕНО

---

## Стратегия D: GitHub Actions с Google Maven

### Описание
Использование стандартных GitHub Actions runners, которые имеют доступ к Google Maven.

### Анализ
GitHub-hosted runners обычно имеют доступ ко всем публичным репозиториям, включая `dl.google.com`.

**Проблема**: В текущей среде доступ заблокирован. Возможные причины:
1. Используется self-hosted runner с ограничениями
2. Применена организационная firewall политика
3. Сетевая изоляция для безопасности

### Реализация

#### Вариант 1: Использовать GitHub-hosted runner

**Файл**: `.github/workflows/android-build.yml`

```yaml
name: Android Build

on:
  push:
    branches: [ main, develop ]
  pull_request:
    branches: [ main, develop ]

jobs:
  build:
    runs-on: ubuntu-latest  # GitHub-hosted, имеет доступ к dl.google.com
    
    steps:
    - uses: actions/checkout@v4
    
    - name: Set up JDK 17
      uses: actions/setup-java@v4
      with:
        distribution: 'temurin'
        java-version: '17'
        
    - name: Grant execute permission for gradlew
      run: chmod +x android/gradlew
      
    - name: Build with Gradle
      working-directory: android
      run: ./gradlew clean assembleDebug
      
    - name: Upload APK
      uses: actions/upload-artifact@v4
      with:
        name: app-debug
        path: android/app/build/outputs/apk/debug/app-debug.apk
```

#### Вариант 2: Запрос снятия блокировки

Обратиться к администраторам организации с запросом на:
1. Whitelist для `dl.google.com`
2. Whitelist для `*.maven.google.com`
3. Использование GitHub-hosted runners

### Трудозатраты
- Изменение CI/CD конфигурации: 30 минут
- Согласование с администраторами: 1-2 дня
- **Итого**: 1-2 часа (+ время согласования)

### Преимущества
- ✅ Минимальные изменения кода
- ✅ Стандартный подход
- ✅ Нет дополнительной инфраструктуры

### Недостатки
- ❌ Зависимость от политик организации
- ❌ Может быть отклонено по соображениям безопасности

#### Статус реализации на 24.11.2025
**Session 18G** — Создание базового workflow:
- Создан workflow `.github/workflows/android-build.yml`, выполняющий `lint`, `test` и `assembleDebug` на GitHub-hosted runner `ubuntu-latest` и выгружающий `app-debug.apk`.
- Workflow публикует артефакт `gradle-caches`, что позволяет импортировать скачанные с Google Maven артефакты в изолированную среду (`~/.gradle/caches`, `~/.gradle/wrapper`).
- Запуск доступен через `workflow_dispatch`, push/pull_request в ветках `main`, `develop`, `release/**` при изменениях в `android/**`.

#### Практический сценарий: сборка на GitHub-hosted runner + локальный offline build
Чтобы выполнить успешную сборку в среде без доступа к Google Maven:

1. **Запустить workflow** `Android Build (AGP Unblock)` из вкладки Actions и выбрать режим `github-hosted`. GitHub runner скачает AGP и соберёт `assembleDebug`.
2. **Скачать артефакты** `app-debug-apk` (для проверки) и `gradle-caches.zip` (кэш Gradle).
3. **Импортировать кэш локально** с помощью скрипта `android/scripts/powershell/import-gradle-cache.ps1`:
   ```
   pwsh ./android/scripts/powershell/import-gradle-cache.ps1 -ArchivePath .\gradle-caches.zip
   ```
   Скрипт восстановит каталоги `.gradle/caches` и `.gradle/wrapper`, что позволяет запускать Gradle в `--offline` режиме.
4. **Запустить локальную сборку**:
   ```
   cd android
   ./gradlew --offline clean assembleDebug
   ```
   После переноса кэша Gradle использует локальные артефакты и сборка проходит без обращения к `dl.google.com`.

**Session 20R** — Расширение инфраструктуры сборки:
- Создан workflow `.github/workflows/android-build-bootstrap.yml` для bootstrap-процесса (подготовка AGP артефактов).
- Расширен workflow `.github/workflows/ci-maven-check.yml`:
  - Добавлен шаг `gradle/wrapper-validation-action` для верификации Gradle Wrapper.
  - При ошибке Google Maven workflow помечается как `neutral` (не блокирует CI).
  - Добавлена загрузка артефактов (health check logs).
- Дополнен скрипт `android/scripts/shell/check-maven-access.sh`:
  - Добавлена проверка mirror (Google Mirror).
  - Разделены коды статуса для Google Maven vs остальные репозитории.
- Обновлён `ci-maven-check.yml`: автоматическая еженедельная проверка доступности Maven с отчётностью.
- Создан Gradle task `prepareReleaseBuild` (dependsOn: lint, test, assembleDebug).
- Добавлен параметр `-PskipDeviceTasks=true` для отключения hardware/lock tasks.

---

## Рекомендации

### Приоритетный план действий

#### Немедленные действия (Владелец проекта)
1. **Выбрать стратегию** из A, B или D в зависимости от:
   - Доступной инфраструктуры
   - Политик безопасности
   - Бюджета времени

2. **Если выбрана Стратегия A (Прокси)**:
   - Получить параметры прокси у сетевых администраторов
   - Добавить секреты в GitHub Actions
   - Настроить локальные `~/.gradle/gradle.properties`

3. **Если выбрана Стратегия B (Nexus)**:
   - Выделить сервер для Nexus
   - Получить доступ к машине с доступом к Google Maven для загрузки артефактов
   - Развернуть Nexus и загрузить AGP

4. **Если выбрана Стратегия D (GitHub-hosted)**:
   - Подать запрос на использование GitHub-hosted runners
   - Или запросить whitelist для dl.google.com

#### Временные меры (пока блокер не снят)
1. ✅ Продолжать разработку TypeScript компонентов (не требуют AGP)
2. ✅ Статический анализ и код-ревью Kotlin кода
3. ✅ Написание документации
4. ✅ Подготовка тестов
5. ❌ НЕ коммитить Android APK сборки

#### Долгосрочные меры
1. **Документировать процесс**:
   - Создать runbook для работы с AGP
   - Регламент обновления версий
   
2. **Мониторинг доступности**:
   - Настроить healthcheck для Maven репозиториев
   - Алерты при недоступности

3. **Резервные копии**:
   - Кэшировать критичные артефакты локально
   - Использовать Gradle offline mode в экстренных случаях

### Критерии успеха
После реализации выбранной стратегии:
- ✅ `./gradlew assembleDebug` завершается успешно
- ✅ APK размером ≥60 MB генерируется
- ✅ Все тесты проходят
- ✅ Lint без критичных ошибок

---

## История изменений

| Дата       | Версия | Изменения                            | Автор                |
| ---------- | ------ | ------------------------------------ | -------------------- |
| 24.11.2025 | 1.0    | Начальная версия, анализ 4 стратегий | GitHub Copilot Agent |

---

**Следующие шаги**: Ожидание решения владельца проекта по выбору стратегии.

## Дополнения Session 19G

### Автоматизированные проверки доступности

**Maven Repository Health Check** (`.github/workflows/ci-maven-check.yml`):
- Автоматическая еженедельная проверка доступности Maven репозиториев
- Подтверждает статус блокировки Google Maven
- Валидирует доступность альтернативных источников (Central, Gradle Portal)
- Результаты сохраняются как artifacts для анализа

**Связь со стратегиями разблокировки**:
- **Стратегия A (Proxy)**: health check может валидировать работу прокси после настройки
- **Стратегия B (Nexus)**: health check может проверять доступность локального Nexus
- **Стратегия C (Downgrade)**: ❌ Отклонена, health check не применим
- **Стратегия D (GitHub-hosted runner)**: ✅ health check подтверждает, что публичный runner имеет доступ

**Рекомендация**: использовать результаты ci-maven-check.yml для мониторинга статуса блокировки и оценки эффективности выбранной стратегии разблокировки.

### Дополнительные CI workflows

**hardware-arduino.yml**:
- Еженедельная компиляция Arduino прошивок для валидации корректности кода
- Dry-run режим по умолчанию (безопасно для локального окружения)
- Не зависит от AGP blocker (использует arduino-cli)

**packages-ci.yml**:
- Матричное тестирование всех TypeScript *-kit пакетов
- Валидирует npm install/test/lint для device-obd-kit, device-thickness-kit, report-kit, payment-mock-kit
- Не зависит от AGP blocker (использует Node.js + npm)

**agent-ci.yml** (обновлён):
- Добавлены `if: always()` для всех artifact uploads
- Улучшена отказоустойчивость при падении lint/test
- Результаты всегда сохраняются для анализа

**Статус**: все workflows работают независимо от AGP blocker. Android-специфичные задачи остаются заблокированными до выбора стратегии разблокировки.

---

**Обновление**: 24.11.2025 (Session 19G)  
**Следующие шаги**: выбор и реализация одной из стратегий разблокировки (D рекомендуется).

