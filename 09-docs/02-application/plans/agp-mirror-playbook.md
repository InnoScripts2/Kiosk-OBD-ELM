# Playbook: офлайн-сборка Android через зеркала Gradle

Документ описывает постоянный процесс обхода блокировки `dl.google.com` и замены сессионных памяток. Следуем ему при любых проблемах со скачиванием AGP или SDK.

## 1. Self-hosted раннер

### Требования
- Java 17 (Temurin).
- Android SDK (API 35, Build Tools 35.0.0).
- Доступ в интернет к `github.com`, `objects.githubusercontent.com`, `raw.githubusercontent.com` и зеркалам Maven.
- 10 GB свободного места под Gradle cache.

### Настройка (Windows PowerShell)
```powershell
cd C:\actions-runner
Invoke-WebRequest -Uri https://github.com/actions/runner/releases/download/v2.319.1/actions-runner-win-x64-2.319.1.zip -OutFile actions-runner.zip
Expand-Archive -Path actions-runner.zip -DestinationPath .
./config.cmd --url https://github.com/InnoScripts2/Kiosk-OBD-ELM --token <TOKEN> --labels "self-hosted,Windows,kiosk-agp"
./run.cmd
```

### Настройка (Linux/macOS)
```bash
mkdir -p ~/actions-runner && cd ~/actions-runner
curl -o actions-runner.tar.gz -L https://github.com/actions/runner/releases/download/v2.319.1/actions-runner-linux-x64-2.319.1.tar.gz
tar xzf actions-runner.tar.gz
./config.sh --url https://github.com/InnoScripts2/Kiosk-OBD-ELM --token <TOKEN> --labels "self-hosted,linux,kiosk-agp"
./run.sh
```

> После первичного прогона установите раннер как сервис (`./svc install`/`./svc start`).

## 2. Workflow `.github/workflows/android-build.yml`

Добавлены входные параметры:
- `runner_mode`: `github-hosted` или `self-hosted`.
- `self_hosted_labels`: массив ярлыков раннера (по умолчанию `["self-hosted"]`).
- `gradle_tasks`: список команд Gradle (`clean lint detekt test assembleDebug`).

### Как запускать
1. Actions → **Android Build (AGP Unblock)** → **Run workflow**.
2. Выберите ветку, установите `runner_mode = self-hosted`.
3. При необходимости уточните `self_hosted_labels`.
4. Настройте `gradle_tasks` (опционально).
5. Запустите workflow и дождитесь job `Gradle · self-hosted runner`.

### Что делает job
- Ставит Java 17 + Android SDK.
- Выполняет `./gradlew clean lint detekt test assembleDebug --build-cache --stacktrace` (учитывает .bat на Windows).
- Публикует `app-debug.apk` и архив `gradle-caches-selfhosted`.

## 3. Офлайн-сборка

1. Скачайте `gradle-caches-selfhosted.zip` из последнего успешного раннера.
2. Распакуйте в `%USERPROFILE%\.gradle` или `~/.gradle`.
3. Выполните:
   ```powershell
   cd android
   ./gradlew.bat clean lint detekt test assembleDebug --offline --stacktrace
   ```
4. При запросе новых артефактов повторите шаги 1–2.

## 4. Контрольные точки
- ✅ Self-hosted раннер отображается в `Settings ▸ Actions ▸ Runners`.
- ✅ Workflow завершается успешно, артефакты загружены.
- ✅ Локальная машина проходит `./gradlew ... --offline`.
- ✅ Отчёты lint/test приложены к change-report.
- ✅ Размер APK измерен (`Get-Item app-debug.apk | Select Length`).

## 5. Чек-лист DevOps
- [ ] Поддерживать актуальные зеркала Maven в `settings.gradle.kts`.
- [ ] Обновлять версию AGP и Build Tools синхронно с workflow.
- [ ] При разблокировке `dl.google.com` переключаться на `github-hosted` режим.
- [ ] Документировать любые изменения в этом playbook и в AI summary.
