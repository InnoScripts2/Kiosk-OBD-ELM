# Maven Mirror Status - Session 08

**Дата**: 23.11.2025  
**Проблема**: Android Gradle Plugin 8.4.1 недоступен

## Попытка 1: Aliyun mirrors

Добавлены зеркала:
- `maven.aliyun.com/repository/google`
- `maven.aliyun.com/repository/public`

**Результат**: ❌ Plugin не найден

```
Plugin [id: 'com.android.application', version: '8.4.1', apply: false] was not found
Searched in:
  - maven(https://maven.aliyun.com/repository/google)
  - maven2(https://maven.aliyun.com/repository/public)
  - Gradle Central Plugin Repository
  - Google
  - MavenRepo
```

## Вывод

Зеркала Aliyun не содержат Android Gradle Plugin 8.4.1.

## Варианты решения

### 1. Whitelist dl.google.com (РЕКОМЕНДУЕТСЯ)
- Добавить домен в whitelist сетевого доступа
- Самое простое и надёжное решение

### 2. Локальный Maven proxy
- Настроить Nexus/Artifactory с кешированием
- Требует инфраструктуру

### 3. Downgrade AGP до версии, доступной в зеркалах
- Проверить какие версии есть в Aliyun
- Может потребовать изменения кода

### 4. Pre-download dependencies
- Загрузить все зависимости локально
- Работать в offline режиме

## Статус

**Текущий**: Блокер не устранён  
**Workaround**: Невозможен без доступа к dl.google.com или альтернативному источнику AGP 8.4.1

## Следующие действия

1. Запросить whitelist dl.google.com
2. ИЛИ настроить Maven proxy с доступом к Google Maven
3. ИЛИ предоставить pre-downloaded dependencies

До тех пор работаем с:
- ✅ Code review
- ✅ Unit-тесты (без запуска)
- ✅ Node/TypeScript агент (независим от Android build)
- ✅ Документация
