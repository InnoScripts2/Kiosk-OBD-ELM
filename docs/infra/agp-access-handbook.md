# Android Gradle Plugin - Руководство по доступу и обслуживанию
**Версия**: 1.0  
**Дата создания**: 24.11.2025  
**Последнее обновление**: 24.11.2025

## Содержание
- [Введение](#введение)
- [Описание проблемы](#описание-проблемы)
- [Архитектура решения](#архитектура-решения)
- [Инструкции по обслуживанию](#инструкции-по-обслуживанию)
- [Обновление AGP](#обновление-agp)
- [Мониторинг и диагностика](#мониторинг-и-диагностика)
- [Troubleshooting](#troubleshooting)
- [Справочные материалы](#справочные-материалы)

---

## Введение

### Назначение документа
Этот документ описывает проблему доступа к Android Gradle Plugin (AGP), выбранное решение и процедуры обслуживания для проекта Kiosk-OBD-ELM.

### Аудитория
- DevOps-инженеры
- Android разработчики
- Системные администраторы
- Владельцы проекта

### Предварительные требования
- Базовые знания Gradle
- Понимание Maven repository manager
- Доступ к инфраструктуре проекта

---

## Описание проблемы

### Суть проблемы
**Android Gradle Plugin (AGP)** публикуется Google исключительно в собственном Maven репозитории по адресу:
```
https://dl.google.com/dl/android/maven2/
```

**Проблема**: Этот домен заблокирован в текущей сетевой среде проекта, что делает невозможным:
- Сборку Android приложения (`./gradlew assembleDebug`)
- Обновление зависимостей
- Работу с новыми версиями Android SDK

### Хронология обнаружения
1. **Session 10C (23.11.2025)**: Первое обнаружение блокировки
   - Протестированы версии AGP: 7.4.2, 8.2.2, 8.3.2, 8.4.1
   - Все версии недоступны
   - Статус: BLOCKED

2. **Session 14Z (24.11.2025)**: Комплексный анализ и поиск решений
   - Диагностика сетевого доступа
   - Анализ 4 стратегий разблокировки
   - Создание документации и регламентов

### Технические детали

#### Сетевая диагностика
```bash
# Тест доступа к Google Maven
$ curl -I https://dl.google.com/android/repository/repository2-1.xml
curl: (6) Could not resolve host: dl.google.com

# Тест доступа к китайским зеркалам
$ curl -I https://maven.aliyun.com/repository/google/
curl: (6) Could not resolve host: maven.aliyun.com
```

#### Gradle ошибка
```
FAILURE: Build failed with an exception.

* What went wrong:
Plugin [id: 'com.android.application', version: '8.4.1', apply: false] 
was not found in any of the following sources:
- Gradle Core Plugins (plugin is not in 'org.gradle' namespace)
- Plugin Repositories (could not resolve plugin artifact)
  Searched in the following repositories:
    maven(https://maven.aliyun.com/repository/google)  ❌
    maven(https://maven.aliyun.com/repository/public)   ❌
    Gradle Central Plugin Repository                    ❌ (AGP не публикуется там)
    Google                                              ❌ (dl.google.com заблокирован)
    MavenRepo                                           ❌ (AGP не публикуется там)
```

#### Доступные репозитории
| Репозиторий | Доступность | Содержит AGP? | Версии |
|------------|-------------|---------------|--------|
| Maven Central | ✅ | Частично | ≤2.3.0 |
| Gradle Plugin Portal | ✅ | ❌ | - |
| Google Maven | ❌ | ✅ | Все версии |
| Aliyun mirrors | ❌ | ✅ | Все версии |
| JitPack | ✅ | ❌ | - |

---

## Архитектура решения

### Выбранная стратегия
**ПРИМЕЧАНИЕ**: Конкретная стратегия должна быть выбрана владельцем проекта из 4 вариантов в `agp-unblock-plan.md`.

Этот раздел будет обновлён после выбора и реализации решения.

### Возможные архитектуры

#### Архитектура A: Корпоративный прокси
```
┌─────────────────┐      ┌──────────────┐      ┌─────────────────┐
│ GitHub Actions  │─────▶│ HTTP(S)      │─────▶│ dl.google.com   │
│ Runner          │      │ Proxy        │      │ (Google Maven)  │
└─────────────────┘      └──────────────┘      └─────────────────┘
         │                       ▲
         │                       │
         │               ┌───────┴────────┐
         └──────────────▶│ Gradle Config  │
                         │ (proxy settings)│
                         └────────────────┘
```

**Компоненты**:
- Прокси-сервер с доступом к dl.google.com
- Gradle настроен на использование прокси
- Секреты в GitHub Actions

#### Архитектура B: Локальное зеркало (Nexus)
```
┌─────────────────┐      ┌──────────────┐      ┌─────────────────┐
│ GitHub Actions  │─────▶│ Nexus/       │      │ dl.google.com   │
│ Runner          │      │ Artifactory  │◀─────│ (proxy mode)    │
└─────────────────┘      └──────────────┘      └─────────────────┘
         │                       │
         │                       │ (загружено вручную)
         │               ┌───────▼────────┐
         └──────────────▶│ Gradle Config  │
                         │ (nexus URL)    │
                         └────────────────┘
```

**Компоненты**:
- Nexus Repository Manager
- Proxy repository для Google Maven
- Hosted repository для ручной загрузки
- Group repository для унификации

#### Архитектура D: GitHub-hosted runner
```
┌─────────────────┐      ┌─────────────────┐
│ GitHub-hosted   │─────▶│ dl.google.com   │
│ Runner          │      │ (прямой доступ) │
│ (ubuntu-latest) │      └─────────────────┘
└─────────────────┘
         │
         │
         │               ┌────────────────┐
         └──────────────▶│ Gradle Config  │
                         │ (стандартный)  │
                         └────────────────┘
```

**Компоненты**:
- Стандартный GitHub-hosted runner
- Прямой доступ к Google Maven (без ограничений)

---

## Инструкции по обслуживанию

### Ежедневные задачи
Специфичных ежедневных задач нет.

### Еженедельные задачи

#### Проверка доступности репозиториев
**Периодичность**: Раз в неделю (понедельник, 09:00)

**Скрипт проверки** (`android/scripts/shell/check-maven-access.sh`):
```bash
#!/bin/bash
# Проверка доступности Maven репозиториев

echo "=== Maven Repository Health Check ==="
echo "Дата: $(date)"
echo ""

# Функция проверки доступности
check_repo() {
    local name=$1
    local url=$2
    echo -n "Проверка $name... "
    if curl -s -o /dev/null -w "%{http_code}" --max-time 10 "$url" | grep -q "200\|301\|302"; then
        echo "✅ OK"
        return 0
    else
        echo "❌ НЕДОСТУПЕН"
        return 1
    fi
}

# Проверка основных репозиториев
check_repo "Google Maven" "https://dl.google.com/android/maven2/"
check_repo "Maven Central" "https://repo1.maven.org/maven2/"
check_repo "Gradle Plugin Portal" "https://plugins.gradle.org/m2/"
check_repo "JitPack" "https://jitpack.io/"

# Проверка корпоративного Nexus (если есть)
# check_repo "Nexus" "http://nexus.company.internal/repository/maven-public/"

echo ""
echo "=== Проверка завершена ==="
```

**Запуск проверки**:
- Вручную: `cd android && ./gradlew checkMavenAccess`
- Автоматически: через GitHub Actions workflow `ci-maven-check.yml` (еженедельно)
- CI интеграция: каждый push в main выполняет `checkMavenAccess` в рамках общей проверки

**Логирование результатов**:
Результаты сохраняются в `logs/infrastructure/maven-health-YYYY-MM-DD.log`
}

# Проверка репозиториев
check_repo "Google Maven" "https://dl.google.com/android/repository/repository2-1.xml"
check_repo "Maven Central" "https://repo1.maven.org/maven2/"
check_repo "Gradle Plugin Portal" "https://plugins.gradle.org/m2/"
check_repo "JitPack" "https://jitpack.io/"

# Если используется Nexus
if [ ! -z "$NEXUS_URL" ]; then
    check_repo "Nexus (local)" "$NEXUS_URL/service/rest/v1/status"
fi

echo ""
echo "=== Завершено ==="
```

**Запуск**:
```bash
chmod +x infra/scripts/check-maven-access.sh
./infra/scripts/check-maven-access.sh
```

**Результат сохранять в**: `logs/infrastructure/maven-health-YYYY-MM-DD.log`

#### Проверка размера кэша Gradle
**Периодичность**: Раз в неделю (пятница, 16:00)

```bash
# Локально
du -sh ~/.gradle/caches/

# В CI (GitHub Actions)
du -sh /home/runner/.gradle/caches/
```

**Действия при превышении 10 GB**:
```bash
# Очистка старых артефактов (старше 30 дней)
find ~/.gradle/caches/ -type f -mtime +30 -delete
```

### Ежемесячные задачи

#### Обновление документации
**Периодичность**: Первое число каждого месяца

Проверить и обновить:
- [ ] `docs/infra/agp-access-handbook.md` (этот файл)
- [ ] `docs/infra/agp-unblock-plan.md`
- [ ] `plan-secrets-config.md` (секреты для прокси/Nexus)

#### Аудит секретов
**Периодичность**: Первое число каждого месяца

Проверить актуальность GitHub Secrets:
```bash
# Список секретов (через GitHub CLI)
gh secret list --repo InnoScripts2/Kiosk-OBD-ELM

# Проверить:
# - PROXY_HOST (если используется)
# - PROXY_PORT
# - PROXY_USER
# - PROXY_PASSWORD
# - NEXUS_USERNAME (если используется)
# - NEXUS_PASSWORD
```

---

## Обновление AGP

### Процедура обновления версии AGP

#### Предварительная проверка

**Шаг 1**: Проверить совместимость новой версии
- Посетить: https://developer.android.com/studio/releases/gradle-plugin (если доступен)
- Или посмотреть Release Notes в GitHub: https://developer.android.com/build/releases/gradle-plugin

**Совместимость версий**:
| AGP | Gradle | Kotlin | compileSdk | minSdk |
|-----|--------|--------|------------|--------|
| 8.4.x | 8.6+ | 1.9.20+ | 35 | 21+ |
| 8.3.x | 8.4+ | 1.9.0+ | 34 | 21+ |
| 8.2.x | 8.2+ | 1.9.0+ | 34 | 21+ |

**Шаг 2**: Проверить доступность в репозитории

```bash
# Если используется прокси или Nexus, проверить доступность
cd android
./gradlew dependencies --configuration classpath | grep "com.android.tools.build:gradle"
```

#### Обновление конфигурации

**Шаг 1**: Обновить `gradle/libs.versions.toml`

```toml
[versions]
androidGradlePlugin = "8.5.0"  # Новая версия
kotlin = "1.9.24"              # Проверить совместимость
```

**Шаг 2**: Обновить Gradle Wrapper (если требуется)

```bash
cd android
./gradlew wrapper --gradle-version=8.7
```

**Шаг 3**: Синхронизировать зависимости

```bash
./gradlew --refresh-dependencies
```

#### Тестирование

**Шаг 1**: Чистая сборка

```bash
./gradlew clean assembleDebug
```

**Шаг 2**: Запуск тестов

```bash
./gradlew testDebugUnitTest
./gradlew connectedAndroidTest  # Если есть instrumented тесты
```

**Шаг 3**: Линтинг

```bash
./gradlew lint detekt
```

**Шаг 4**: Проверка размера APK

```bash
ls -lh app/build/outputs/apk/debug/app-debug.apk
# Ожидаемый размер: ≥60 MB
```

#### Откат при проблемах

Если обновление вызвало проблемы:

**Шаг 1**: Откатить версию в `gradle/libs.versions.toml`

```toml
[versions]
androidGradlePlugin = "8.4.1"  # Предыдущая рабочая версия
```

**Шаг 2**: Очистить кэш и пересобрать

```bash
./gradlew clean --refresh-dependencies
./gradlew assembleDebug
```

#### Документирование обновления

После успешного обновления обновить:
- [ ] `CHANGELOG.md` (если есть)
- [ ] `android/session-logs/session-XX.md`
- [ ] `plan-80-session-roadmap.md`

---

## Мониторинг и диагностика

### Ключевые метрики

#### Время сборки
**Цель**: Отслеживать деградацию производительности

**Измерение**:
```bash
time ./gradlew clean assembleDebug
```

**Baseline** (ожидаемые значения):
- Чистая сборка: 3-5 минут
- Инкрементальная: 30-60 секунд

**Действия при превышении**:
1. Проверить доступность репозиториев
2. Очистить Gradle cache
3. Проверить сетевую задержку до репозиториев

#### Размер кэша
**Цель**: Предотвратить переполнение диска

**Измерение**:
```bash
du -sh ~/.gradle/caches/
```

**Пороги**:
- Нормальный: < 5 GB
- Внимание: 5-10 GB
- Критичный: > 10 GB

#### Доступность репозиториев
**Цель**: Раннее обнаружение сетевых проблем

**Мониторинг**: Еженедельная проверка (см. выше)

**Алерты**:
- Google Maven недоступен → CRITICAL
- Maven Central недоступен → HIGH
- Nexus недоступен (если используется) → CRITICAL

### Логирование

#### Gradle Build Logs
**Расположение**: `android/build/outputs/logs/`

**Ротация**: Автоматически при каждой сборке

**Хранение**: Последние 10 сборок

#### Infrastructure Logs
**Расположение**: `logs/infrastructure/`

**Структура**:
```
logs/infrastructure/
├── maven-health-2025-11-24.log
├── gradle-cache-2025-11-24.log
└── nexus-status-2025-11-24.log  (если используется)
```

**Ротация**: Ежедневно, хранить 30 дней

---

## Troubleshooting

### Проблема: "Plugin not found in any of the following sources"

**Симптомы**:
```
Plugin [id: 'com.android.application', version: '8.4.1'] was not found
```

**Причины**:
1. Блокировка доступа к Google Maven
2. Неправильная конфигурация прокси
3. Nexus repository недоступен
4. Сетевые проблемы

**Диагностика**:

**Шаг 1**: Проверить доступность репозиториев
```bash
./infra/scripts/check-maven-access.sh
```

**Шаг 2**: Проверить настройки прокси (если используется)
```bash
cat ~/.gradle/gradle.properties | grep proxy
```

**Шаг 3**: Проверить Nexus (если используется)
```bash
curl -I http://<nexus-url>:8081/service/rest/v1/status
```

**Шаг 4**: Попробовать с явным --debug
```bash
cd android
./gradlew assembleDebug --debug 2>&1 | grep "com.android.application"
```

**Решения**:

**Решение 1**: Проверить и обновить конфигурацию репозиториев
```kotlin
// android/settings.gradle.kts
pluginManagement {
    repositories {
        // Убедиться, что правильный репозиторий первый
        maven { url = uri("http://<nexus-url>:8081/repository/maven-public/") }
        gradlePluginPortal()
    }
}
```

**Решение 2**: Очистить кэш и повторить
```bash
./gradlew clean --refresh-dependencies
rm -rf ~/.gradle/caches/
./gradlew assembleDebug
```

**Решение 3**: Проверить прокси настройки
```properties
# ~/.gradle/gradle.properties
systemProp.http.proxyHost=<proxy-host>
systemProp.http.proxyPort=<proxy-port>
systemProp.https.proxyHost=<proxy-host>
systemProp.https.proxyPort=<proxy-port>
```

### Проблема: Медленная загрузка зависимостей

**Симптомы**:
- Сборка занимает > 10 минут
- "Downloading" зависает на одной зависимости

**Причины**:
1. Медленное сетевое соединение до репозитория
2. Репозиторий перегружен
3. Большое количество зависимостей

**Решения**:

**Решение 1**: Использовать --offline mode (если кэш актуален)
```bash
./gradlew assembleDebug --offline
```

**Решение 2**: Увеличить timeout
```properties
# gradle.properties
org.gradle.internal.http.connectionTimeout=120000
org.gradle.internal.http.socketTimeout=120000
```

**Решение 3**: Использовать локальное зеркало (Nexus)
- См. Стратегию B в `agp-unblock-plan.md`

### Проблема: SSL/TLS ошибки при использовании прокси

**Симптомы**:
```
PKIX path building failed
unable to find valid certification path to requested target
```

**Причины**:
- Прокси использует собственный SSL сертификат
- Java не доверяет корневому CA прокси

**Решения**:

**Решение 1**: Импортировать CA сертификат прокси в Java truststore
```bash
# Получить сертификат
openssl s_client -showcerts -connect proxy.company.com:8080 < /dev/null 2>&1 | \
    sed -n '/BEGIN CERTIFICATE/,/END CERTIFICATE/p' > proxy-ca.crt

# Импортировать в Java truststore
keytool -import -alias proxy-ca -file proxy-ca.crt \
    -keystore $JAVA_HOME/lib/security/cacerts \
    -storepass changeit
```

**Решение 2**: Отключить SSL проверку (НЕ РЕКОМЕНДУЕТСЯ для production)
```properties
# gradle.properties (ТОЛЬКО для dev/test)
systemProp.javax.net.ssl.trustAll=true
```

### Проблема: Nexus storage заполнен

**Симптомы**:
```
HTTP 507 Insufficient Storage
```

**Причины**:
- Nexus storage достиг лимита
- Накопилось много старых версий артефактов

**Решения**:

**Решение 1**: Очистить старые артефакты через Nexus UI
1. Nexus UI → Browse → Components
2. Выбрать старые версии
3. Delete

**Решение 2**: Настроить Cleanup Policy
```
Nexus UI → Administration → Repository → Cleanup Policies
- Name: cleanup-old-artifacts
- Format: maven2
- Criteria: Last Downloaded < 90 days
```

**Решение 3**: Увеличить storage
```bash
# Увеличить volume в docker-compose.yml
# или добавить диск на сервере
```

---

## Справочные материалы

### Официальная документация

**Android Gradle Plugin**:
- Release Notes: https://developer.android.com/build/releases/gradle-plugin
- API Reference: https://developer.android.com/reference/tools/gradle-api
- Migration Guide: https://developer.android.com/studio/releases/gradle-plugin-roadmap

**Gradle**:
- User Manual: https://docs.gradle.org/current/userguide/userguide.html
- Plugin Portal: https://plugins.gradle.org/
- Performance Guide: https://docs.gradle.org/current/userguide/performance.html

**Nexus Repository**:
- Documentation: https://help.sonatype.com/repomanager3
- Docker Image: https://hub.docker.com/r/sonatype/nexus3

### Внутренняя документация

**Текущий проект**:
- `docs/infra/agp-unblock-plan.md` - Стратегии разблокировки AGP
- `plan-secrets-config.md` - Управление секретами
- `plan-connectivity.md` - Сетевые политики и требования
- `plan-testing.md` - Тестирование сборок

**Session logs**:
- `android/session-logs/session-10c.md` - Первое обнаружение блокировки
- `android/session-logs/session-14z.md` - Комплексный анализ

### Контакты

**Ответственные лица**:
- DevOps: [укажите контакт]
- Android Lead: [укажите контакт]
- Сетевые администраторы: [укажите контакт]

**Эскалация**:
1. Уровень 1: Android разработчики (troubleshooting)
2. Уровень 2: DevOps (инфраструктура, Nexus)
3. Уровень 3: Сетевые администраторы (firewall, прокси)

---

## Версионирование документа

| Дата | Версия | Изменения | Автор |
|------|--------|-----------|-------|
| 24.11.2025 | 1.0 | Начальная версия | GitHub Copilot Agent |

---

**Примечание**: Этот документ будет обновлён после выбора и реализации конкретной стратегии разблокировки.
