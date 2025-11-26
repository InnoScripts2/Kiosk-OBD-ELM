# Сессия 08 — снятие блокировки AGP 8.4.1

**Проблема.** Домен `dl.google.com` недоступен из локальной сети, поэтому Gradle не скачивает Android Gradle Plugin 8.4.1 и связанные артефакты. Любые локальные запуски `./gradlew clean lint detekt test assembleDebug` обрываются на стадии загрузки `com.android.tools.build:gradle:8.4.1` и `com.android.tools:sdk-common`. В результате тесты/сборка (блок плана «6. Тесты и сборка») не выполняются, а вес APK и отчёты lint остались незафиксированными.

**Решение.** Использовать GitHub Actions с self-hosted раннером, запущенным на машине с доступом к Google Maven, и хранить скачанные артефакты как артефакты workflow. Локальная машина без доступа к `dl.google.com` может забирать `gradle-caches` архив из workflow и распаковывать его в `~/.gradle` для офлайновых прогонов.

## 1. Регистрация self-hosted раннера

### 1.1 Подготовка
- Требуется Java 17, Android SDK (API 35, Build Tools 35.0.0) и достаточный свободный диск для Gradle cache (~10 GB).
- Порт 443 должен быть открыт для `github.com`, `objects.githubusercontent.com` и `raw.githubusercontent.com`.

### 1.2 Windows PowerShell
```powershell
cd C:\actions-runner
Invoke-WebRequest -Uri https://github.com/actions/runner/releases/download/v2.319.1/actions-runner-win-x64-2.319.1.zip -OutFile actions-runner.zip
Expand-Archive -Path actions-runner.zip -DestinationPath .
# токен берём из Settings ▸ Actions ▸ Runners ▸ New self-hosted runner
type token.txt
./config.cmd --url https://github.com/InnoScripts2/Kiosk-OBD-ELM --token <TOKEN> --labels "self-hosted,Windows,kiosk-agp"
./run.cmd
```

### 1.3 Linux/macOS
```bash
mkdir -p ~/actions-runner && cd ~/actions-runner
curl -o actions-runner.tar.gz -L https://github.com/actions/runner/releases/download/v2.319.1/actions-runner-linux-x64-2.319.1.tar.gz
tar xzf actions-runner.tar.gz
./config.sh --url https://github.com/InnoScripts2/Kiosk-OBD-ELM --token <TOKEN> --labels "self-hosted,linux,kiosk-agp"
./run.sh
```

> После проверки можно установить раннер как сервис (`./svc install` / `./svc start`), чтобы workflow мог запускаться без ручного старта.

## 2. Workflow `.github/workflows/android-build.yml`

### 2.1 Новые параметры
- `runner_mode`: `github-hosted` (по умолчанию) или `self-hosted`.
- `self_hosted_labels`: JSON-массив с ярлыками раннера. По умолчанию `["self-hosted"]`.
- `gradle_tasks`: список команд Gradle (default `clean lint detekt test assembleDebug`).

### 2.2 Как запускать
1. Откройте Actions ▸ **Android Build (AGP Unblock)** ▸ **Run workflow**.
2. Выберите ветку, установите `runner_mode = self-hosted`.
3. Если ярлыки раннера отличаются, замените `self_hosted_labels`, например `["self-hosted","Windows","kiosk-agp"]`.
4. (Опционально) скорректируйте `gradle_tasks`.
5. Запустите workflow и дождитесь завершения job `Gradle · self-hosted runner` (тайм-аут 60 минут).

### 2.3 Что делает job
- Устанавливает Java 17 (Temurin), Android SDK (API 35) и подготавливает Gradle cache.
- Выполняет `./gradlew clean lint detekt test assembleDebug --build-cache --stacktrace` (автоматически переключается на `gradlew.bat` на Windows).
- Публикует APK `android/app/build/outputs/apk/debug/app-debug.apk`.
- Публикует архив `gradle-caches-selfhosted` (каталоги `~/.gradle/caches` и `~/.gradle/wrapper`).

## 3. Использование артефактов для офлайн-сборки
1. Скачайте `gradle-caches-selfhosted.zip` из завершённого workflow.
2. Распакуйте поверх локального `%USERPROFILE%\.gradle` (Windows) или `~/.gradle` (Linux/macOS).
3. Добавьте зеркала (уже настроено в `settings.gradle.kts`), затем запускайте локально:
   ```powershell
   cd android
   ./gradlew.bat clean lint detekt test assembleDebug --offline --stacktrace
   ```
4. Если Gradle просит перекачать артефакт — повторите шаг 1 для свежего workflow.

## 4. Контрольные точки
- **Тесты**: отчёты доступны в `android/**/build/reports/tests/`. При прогоне self-hosted job приложите ссылки к логу сессии 08.
- **Lint/Detekt**: отчёты находятся в `android/**/build/reports/lint-*` и `android/**/build/reports/detekt/` и должны прикладываться к сессии.
- **Размер APK**: скачайте `app-debug-apk-selfhosted` → измерьте `Get-Item .\app-debug.apk | Select Length`.
- **Логи блокировки**: issue `outbox/change-reports/2025-11-23-agp-blocker.json` обновляется ссылками на workflow, подтверждающими снятие блокировки.

## 5. Чек-лист DEVOPS
- [ ] Self-hosted раннер зарегистрирован, отображается в `Settings ▸ Actions ▸ Runners`.
- [ ] Workflow успешно проходит на self-hosted (зелёная галочка, apk+cache загружены).
- [ ] Локальная машина способна выполнить `./gradlew ... --offline` с выгруженным cache.
- [ ] Логи lint/test прикреплены к журналу сессии 08 и к `plan-80-session-roadmap.md`.
- [ ] При возвращении доступа к `dl.google.com` переключить `runner_mode` обратно на `github-hosted`.

Документ хранится в `09-docs/02-application/plans/session08-agp-unblock.md` и обязателен к обновлению при смене версии AGP или зеркал Gradle.
