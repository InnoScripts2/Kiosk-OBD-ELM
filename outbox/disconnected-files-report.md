# Disconnected Files Report - 2025-11-30

## Summary

This report identifies files and modules that are not properly integrated into the build system.
**UPDATES APPLIED**: Several integration issues have been fixed in this session.

---

## Category: Android Modules (Disconnected from settings.gradle.kts)

### 1. feature-kiosk-mode

- **Path**: `feature-kiosk-mode/`
- **Status**: Commented out in `settings.gradle.kts` (line 46)
- **Comment**: `// ":feature-kiosk-mode",  // ВРЕМЕННО ОТКЛЮЧЁН: отсутствуют зависимости fuseforge, room, compose, car`
- **File Count**: 40+ Kotlin files in `src/main/java/` and `src/test/kotlin/`
- **Contents**:
  - Main activity and car dashboard app
  - OBD service components (`OBDService.kt`, `OBDServiceWithDiagnostics.kt`, etc.)
  - Bluetooth manager
  - UI metric cards (RPM, fuel, coolant temperature, etc.)
  - Test files (`OBDLoggerTest.kt`, `OBDServiceTest.kt`, `AppDatabaseTest.kt`)
- **Recommendation**: Module needs dependency resolution. Dependencies for `fuseforge`, `room`, `compose`, and `car` APIs should be added to `build.gradle.kts` or temporarily mocked.
- **Priority**: HIGH

---

## Category: TypeScript Packages - FIXED ✅

### 2. device-obd-kit - INTEGRATED

- **Path**: `feature-obd-core/device-obd-kit/`
- **Status**: ✅ FIXED - Added `package.json`, `tsconfig.json`, `jest.config.js`, and `src/index.ts`
- **Gradle Tasks**: ✅ Added to `feature-obd-core/build.gradle.kts`
- **Priority**: RESOLVED

### 3. device-thickness-kit - INTEGRATED

- **Path**: `feature-thickness/device-thickness-kit/`
- **Status**: ✅ FIXED - Added `src/index.ts` stub
- **Gradle Tasks**: Already present in `feature-thickness/build.gradle.kts`
- **Priority**: RESOLVED

### 4. payment-mock-kit - INTEGRATED

- **Path**: `feature-payments/payment-mock-kit/`
- **Status**: ✅ FIXED - Added `src/index.ts` stub
- **Gradle Tasks**: Already present in `feature-payments/build.gradle.kts`
- **Priority**: RESOLVED

### 5. report-kit - INTEGRATED

- **Path**: `feature-reports/report-kit/`
- **Status**: ✅ FIXED - Added Gradle npm tasks to `feature-reports/build.gradle.kts`
- **Source Code**: Already present and complete
- **Priority**: RESOLVED

---

## Category: CI/CD References - FIXED ✅

### 6. ci.yml Workflow - FIXED

- **CI File**: `.github/workflows/ci.yml`
- **Previous Issues**:
  - Referenced non-existent `03-apps/02-application/kiosk-agent`
  - Referenced non-existent `03-apps/02-application/cloud-api`
  - Duplicate `security-scan` job keys
- **Status**: ✅ FIXED
  - Updated all references to `kiosk-shell/agent`
  - Removed cloud-api references
  - Fixed duplicate job definitions
- **Priority**: RESOLVED

### 7. packages-ci.yml Workflow - FIXED

- **CI File**: `.github/workflows/packages-ci.yml`
- **Previous Issues**: Used `android/` prefix for paths that don't exist
- **Status**: ✅ FIXED - Updated all paths to use correct root-relative paths
- **Priority**: RESOLVED

---

## Category: Duplicate Agent Implementations (Informational)

### 8. platform/ui/web/agent vs 03-apps/.../kiosk-shell/agent

- **Path 1**: `platform/ui/web/agent/`
- **Path 2**: `03-apps/02-application/kiosk-shell/agent/`
- **Status**: Both contain similar services (PaymentService, ReportService, LockController, ArduinoAdapter)
- **Observation**: `kiosk-shell/agent` has more complete implementation with additional services:
  - `AgentHeartbeatService`
  - `DeviceCommandService`
  - `ReportIngestService`
  - `workers/report-delivery/` queue system
  - `integrations/supabase/`
- **Recommendation**: Consolidate into single agent implementation; `kiosk-shell/agent` appears to be the active version
- **Priority**: LOW (functional but redundant)

---

## Category: Config Files Not Loaded (Informational)

### 9. supabase/config.toml

- **Path**: `supabase/config.toml`
- **Status**: Configuration file exists but may not be loaded by any service
- **Recommendation**: Verify Supabase CLI uses this configuration during development/migrations
- **Priority**: LOW

### 10. platform/ui/web/kiosk-frontend tokens.json

- **Path**: `platform/ui/web/kiosk-frontend/tokens.json`
- **Status**: Design tokens file, may need import in styles
- **Recommendation**: Verify tokens.json is imported in design system
- **Priority**: LOW

---

## Category: Test Files Properly Registered ✅

### Verified - All test files are using `testMatch: ['**/*.test.ts']` pattern:

- ✅ `03-apps/02-application/kiosk-shell/agent/jest.config.js`
- ✅ `platform/ui/web/agent/jest.config.js`
- ✅ `feature-reports/report-kit/jest.config.js`
- ✅ `feature-thickness/device-thickness-kit/jest.config.js`
- ✅ `feature-payments/payment-mock-kit/jest.config.js`
- ✅ `feature-obd-core/device-obd-kit/jest.config.js` (NEW)

**Status**: All Jest configs properly configured

---

## Category: Gradle-npm Integration Status - UPDATED

| Module | Gradle Task | package.json | src/ directory | Status |
|--------|-------------|--------------|----------------|--------|
| feature-thickness/device-thickness-kit | ✅ Present | ✅ Present | ✅ Present (stub) | READY |
| feature-payments/payment-mock-kit | ✅ Present | ✅ Present | ✅ Present (stub) | READY |
| feature-obd-core/device-obd-kit | ✅ Present (NEW) | ✅ Present (NEW) | ✅ Present (stub) | READY |
| feature-reports/report-kit | ✅ Present (NEW) | ✅ Present | ✅ Present | READY |

---

## Changes Applied in This Session

1. **Created** `feature-obd-core/device-obd-kit/package.json`
2. **Created** `feature-obd-core/device-obd-kit/tsconfig.json`
3. **Created** `feature-obd-core/device-obd-kit/jest.config.js`
4. **Created** `feature-obd-core/device-obd-kit/src/index.ts`
5. **Created** `feature-thickness/device-thickness-kit/src/index.ts`
6. **Created** `feature-payments/payment-mock-kit/src/index.ts`
7. **Updated** `feature-obd-core/build.gradle.kts` - Added npm tasks
8. **Updated** `feature-reports/build.gradle.kts` - Added npm tasks
9. **Fixed** `.github/workflows/ci.yml` - Corrected all path references
10. **Fixed** `.github/workflows/packages-ci.yml` - Removed android/ prefix

---

## Remaining Actions

### HIGH Priority

1. **Enable feature-kiosk-mode**: Resolve dependencies and uncomment in `settings.gradle.kts`

### LOW Priority

2. **Consolidate agent implementations**: Decide between `platform/ui/web/agent` and `kiosk-shell/agent`
3. **Verify config file usage**: Ensure `config.toml` and `tokens.json` are properly loaded

---

## File Discovery Statistics

| Category | Count |
|----------|-------|
| Kotlin files in feature-kiosk-mode | 40+ |
| TypeScript packages integrated | 4 |
| CI workflows fixed | 2 |
| Stub source files created | 3 |
| Gradle build files updated | 2 |

---

*Report generated: 2025-11-30*
*Agent: File Discovery Agent*
