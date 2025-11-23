# BLE/OBD Integration Guide

## Overview

This guide documents the integration between the BLE (Bluetooth Low Energy) layer and OBD (On-Board Diagnostics) protocol support in the kiosk application.

## Architecture

### Layer 1: BLE Platform (platform/bluetooth)

The BLE platform provides low-level Bluetooth communication capabilities:

#### Core Components
- `BleConnectionManager`: Manages BLE connections
- `BleScanner`: Discovers nearby BLE devices
- `BleAdapter Manager`: Monitors Bluetooth adapter state
- `BleConnectionPool`: Handles multiple simultaneous connections

#### Supported Operations
1. **Scanning**: Discover OBD adapters nearby
2. **Connection**: Establish GATT connection
3. **Service Discovery**: Enumerate GATT services
4. **Read/Write**: Access characteristics
5. **Notifications**: Subscribe to characteristic updates

### Layer 2: OBD Protocol (feature-obd-core)

The OBD protocol layer implements ELM327 command protocol:

#### Command System
- `BaseObdCommand`: Abstract base for all commands
- `OBDCommand`: Generic PID-based command
- `CustomCommand`: Custom AT commands

#### Parsers
- `DtcParser`: Diagnostic Trouble Codes
- `PidParser`: Parameter IDs
- `VinParser`: Vehicle Identification Number
- `FreezeFrameParser`: Snapshot data

#### Response Handling
- `ObdResponse`: Sealed class hierarchy
- `ObdResponseCache`: TTL-based caching
- `ObdResponseStats`: Performance metrics

### Layer 3: Feature Integration (feature-obd-diagnostics)

Coming in Session 08+

## Usage Examples

### Example 1: Scan for OBD Adapters

```kotlin
val scanner = BlessedBleScanner(context)
val config = BleScanConfig(
    serviceUuids = BlePlatformConfig().obdServiceUuids.toList(),
    minRssi = -80
)

scanner.scanResults
    .filter { it.device.isObdAdapter }
    .collect { result ->
        println("Found: ${result.device.displayName} (${result.device.rssi} dBm)")
    }

scanner.startScan(config)
```

### Example 2: Connect to Adapter

```kotlin
val manager = BlessedBleConnectionManager(context)

manager.connect(
    device = obdDevice,
    autoReconnect = true,
    timeout = 5000L
)

// Wait for connection
manager.connectionState
    .first { it == BleConnectionState.CONNECTED }

// Discover services
val services = manager.discoverServices()
```

### Example 3: Read DTC Codes

```kotlin
// Mode 03: Request DTC
val command = OBDCommand(PID(mode = "03", PID = ""))
val response = command.run(inputStream, outputStream)

// Parse response
val parser = DtcParser()
val dtcs = parser.parse(response.rawResult)

dtcs.forEach { dtc ->
    println("Code: ${dtc.code}, Type: ${parser.getSystemType(dtc.code)}")
}
```

### Example 4: Clear DTC Codes

```kotlin
// Mode 04: Clear DTC
val clearCommand = OBDCommand(PID(mode = "04", PID = ""))
clearCommand.run(inputStream, outputStream)

// Verify cleared
val verifyCommand = OBDCommand(PID(mode = "03", PID = ""))
val response = verifyCommand.run(inputStream, outputStream)

if (response.rawResult.contains("NO DATA")) {
    println("DTCs successfully cleared")
}
```

### Example 5: Read Live Data

```kotlin
// Mode 01 PID 0C: Engine RPM
val pid = PID(
    mode = "01",
    PID = "0C",
    formula = "((A*256)+B)/4",
    units = "RPM"
)

val command = OBDCommand(pid)
val response = command.run(inputStream, outputStream)

println("Engine RPM: ${response.formattedResult}")
```

### Example 6: Connection Pool Management

```kotlin
val pool = BleConnectionPool(config, maxConnections = 3)

// Connect to multiple adapters
val manager1 = pool.getOrCreate("00:11:22:33:44:55") {
    BlessedBleConnectionManager(context)
}
val manager2 = pool.getOrCreate("00:11:22:33:44:66") {
    BlessedBleConnectionManager(context)
}

// Use connections...

// Cleanup
pool.clear()
```

### Example 7: Auto-Reconnection

```kotlin
val strategy = ExponentialBackoffStrategy(
    initialDelay = 1000L,
    maxDelay = 30000L,
    maxAttempts = 5
)

val coordinator = ReconnectionCoordinator(strategy)

manager.connectionEvents
    .collect { event ->
        when (event) {
            is ConnectionEvent.Disconnected -> {
                val delay = coordinator.registerAttempt(device.address)
                if (delay != null) {
                    delay(delay)
                    manager.connect(device)
                }
            }
            is ConnectionEvent.Connected -> {
                coordinator.reset(device.address)
            }
        }
    }
```

## Error Handling

### Common Errors

#### 1. Device Not Found
```kotlin
try {
    manager.connect(device)
} catch (e: BleConnectionException.DeviceNotFound) {
    // Device moved out of range or turned off
    showError("Адаптер не найден")
}
```

#### 2. Connection Timeout
```kotlin
try {
    manager.connect(device, timeout = 5000L)
} catch (e: BleConnectionException.ConnectionTimeout) {
    // Couldn't establish connection in time
    showError("Не удалось подключиться")
}
```

#### 3. GATT Errors
```kotlin
try {
    val data = manager.readCharacteristic(serviceUuid, charUuid)
} catch (e: BleConnectionException.GattError) {
    Log.e(TAG, "GATT error ${e.gattStatus}: ${e.operation}")
}
```

#### 4. Characteristic Not Found
```kotlin
try {
    manager.writeCharacteristic(serviceUuid, charUuid, data)
} catch (e: BleCharacteristicException.CharacteristicNotFound) {
    // Service doesn't support this characteristic
}
```

### Error Recovery Strategies

1. **Automatic Retry**: Use ReconnectionCoordinator
2. **User Notification**: Show localized error messages
3. **Fallback**: Try alternative connection method
4. **Logging**: Record all errors for diagnostics

## Performance Optimization

### 1. Response Caching

Cache static data to reduce OBD queries:

```kotlin
val cache = ObdResponseCache(
    maxSize = 100,
    ttlMillis = 60_000 // 1 minute
)

// Cache VIN (static)
cache.put("VIN", vinResponse)

// Retrieve from cache
val cached = cache.get("VIN")
```

### 2. Connection Pooling

Reuse connections instead of creating new ones:

```kotlin
// Good: Reuse connection
val manager = pool.getOrCreate(deviceAddress) { ... }

// Bad: Create new connection every time
val manager = BlessedBleConnectionManager(context)
```

### 3. Batch Operations

Send multiple commands in sequence:

```kotlin
val commands = listOf(
    OBDCommand(PID(mode = "01", PID = "0C")), // RPM
    OBDCommand(PID(mode = "01", PID = "0D")), // Speed
    OBDCommand(PID(mode = "01", PID = "05"))  // Temperature
)

commands.forEach { command ->
    val response = command.run(inputStream, outputStream)
    // Process response
}
```

### 4. MTU Optimization

Request larger MTU for faster data transfer:

```kotlin
val negotiatedMtu = manager.requestMtu(247)
println("MTU: $negotiatedMtu bytes")
```

## Testing

### Unit Tests

Test individual components in isolation:

```kotlin
@Test
fun `DtcParser should parse P0133 correctly`() {
    val parser = DtcParser()
    val dtcs = parser.parse("43 01 33")
    
    assertEquals(1, dtcs.size)
    assertEquals("P0133", dtcs[0].code)
}
```

### Integration Tests

Test component interaction:

```kotlin
@Test
fun `BleConnectionManager should connect and discover services`() = runTest {
    val manager = BlessedBleConnectionManager(context)
    
    manager.connect(testDevice)
    
    val state = manager.connectionState.first()
    assertEquals(BleConnectionState.CONNECTED, state)
    
    val services = manager.discoverServices()
    assertTrue(services.isNotEmpty())
}
```

### Mock Testing

Use mocks for BLE operations:

```kotlin
val mockManager = mock<BleConnectionManager>()
whenever(mockManager.connect(any())).thenAnswer {
    // Simulate successful connection
}
```

## Configuration

### Development Profile

```kotlin
val config = BlePlatformConfig.development()
// - Longer timeouts
// - More retry attempts  
// - Verbose logging enabled
```

### Production Profile

```kotlin
val config = BlePlatformConfig.production()
// - Standard timeouts
// - Conservative retries
// - Logging disabled
```

### Custom Configuration

```kotlin
val config = blePlatformConfig {
    connectionTimeout(8000L)
    autoReconnect(true)
    maxReconnectAttempts(5)
    verboseLogging(true)
    
    obdServiceUuids(setOf(myCustomUuid))
}
```

## Troubleshooting

### Problem: Scanning doesn't find devices

**Solution**:
1. Check Bluetooth permissions
2. Verify adapter is ON
3. Lower minRssi filter
4. Increase scan duration

### Problem: Connection fails immediately

**Solution**:
1. Check device is in range (RSSI > -80)
2. Verify device is not connected elsewhere
3. Try increasing timeout
4. Check logs for GATT errors

### Problem: Read/write operations timeout

**Solution**:
1. Verify characteristic properties (READ/WRITE)
2. Check MTU size
3. Reduce data size
4. Increase operation timeout

### Problem: Auto-reconnect not working

**Solution**:
1. Verify autoReconnect = true
2. Check ReconnectionStrategy settings
3. Ensure ConnectionEvent collection is active
4. Review error logs

## Best Practices

1. **Always handle errors**: Wrap BLE operations in try-catch
2. **Release resources**: Call manager.release() when done
3. **Use connection pool**: For multiple devices
4. **Cache static data**: VIN, ECU name, calibration IDs
5. **Monitor battery**: BLE scanning drains battery
6. **Respect permissions**: Request at runtime
7. **Log appropriately**: Verbose in dev, minimal in prod
8. **Test on real devices**: Emulators don't support BLE

## References

- [Bluetooth Core Specification](https://www.bluetooth.com/specifications/specs/)
- [ELM327 Command Protocol](https://www.elmelectronics.com/wp-content/uploads/2016/07/ELM327DS.pdf)
- [OBD-II PIDs](https://en.wikipedia.org/wiki/OBD-II_PIDs)
- [SAE J1979](https://www.sae.org/standards/content/j1979_201202/)

## Changelog

### Session 07B (23.11.2025)
- Initial BLE/OBD integration
- BlessedBleConnectionManager implementation
- DTC/PID parsers
- Response caching
- Connection pooling
- Comprehensive error handling

### Session 08 (Planned)
- DI integration
- UI components
- End-to-end tests
- Performance profiling
