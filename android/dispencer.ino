/**
 * Dispencer.ino - Arduino скрипт для управления замками выдачи устройств
 * 
 * Управляет электронными замками для выдачи толщиномера и OBD-адаптера
 * Протокол связи: Serial (9600 baud)
 * 
 * Команды (ASCII):
 *   OPEN_THICKNESS\n - открыть замок толщиномера
 *   OPEN_OBD\n       - открыть замок OBD-адаптера
 *   CLOSE_THICKNESS\n - закрыть замок толщиномера
 *   CLOSE_OBD\n       - закрыть замок OBD-адаптера
 *   STATUS\n          - запросить статус всех замков
 *   PING\n            - проверка связи
 * 
 * Ответы:
 *   OK:OPENED:THICKNESS\n - замок толщиномера открыт
 *   OK:CLOSED:THICKNESS\n - замок толщиномера закрыт
 *   OK:OPENED:OBD\n       - замок OBD открыт
 *   OK:CLOSED:OBD\n       - замок OBD закрыт
 *   ERROR:TIMEOUT:THICKNESS\n - таймаут открытия замка
 *   ERROR:HARDWARE:THICKNESS\n - аппаратная ошибка
 *   STATUS:THICKNESS=CLOSED,OBD=CLOSED\n - статус замков
 *   PONG\n - ответ на ping
 * 
 * Требования к оборудованию:
 * - Arduino Uno/Nano/Mega
 * - 2× реле 5V для управления электромагнитными замками
 * - 2× датчика положения (герконы) для обратной связи
 * - Питание 12V для замков
 * 
 * Пины:
 * - PIN 2: Реле замка толщиномера
 * - PIN 3: Датчик положения толщиномера (LOW=закрыт, HIGH=открыт)
 * - PIN 4: Реле замка OBD-адаптера
 * - PIN 5: Датчик положения OBD-адаптера (LOW=закрыт, HIGH=открыт)
 * - PIN 13: Встроенный LED для индикации активности
 * 
 * Логика работы:
 * 1. Получение команды через Serial
 * 2. Активация реле (открытие замка)
 * 3. Ожидание подтверждения от датчика положения (таймаут 5 сек)
 * 4. Отправка ответа о результате операции
 * 5. Автоматическое закрытие замка через 10 сек после открытия (безопасность)
 * 
 * Безопасность:
 * - Блокировка повторных команд до завершения текущей операции
 * - Автоматическое закрытие замков при потере связи (watchdog)
 * - Ограничение времени удержания замка открытым
 * - Логирование всех операций через Serial
 */

// === Конфигурация пинов ===
const int RELAY_THICKNESS = 2;      // Реле замка толщиномера
const int SENSOR_THICKNESS = 3;     // Датчик положения толщиномера
const int RELAY_OBD = 4;            // Реле замка OBD-адаптера
const int SENSOR_OBD = 5;           // Датчик положения OBD-адаптера
const int LED_ACTIVITY = 13;        // Встроенный LED индикатор

// === Константы ===
const long SERIAL_BAUD = 9600;           // Скорость Serial порта
const unsigned long LOCK_TIMEOUT = 5000; // Таймаут операции замка (мс)
const unsigned long AUTO_CLOSE_DELAY = 10000; // Автоматическое закрытие через 10 сек
const unsigned long WATCHDOG_TIMEOUT = 60000; // Watchdog таймаут (1 минута)
const int MAX_COMMAND_LENGTH = 32;       // Максимальная длина команды

// === Состояние замков ===
enum LockState {
  LOCK_CLOSED,
  LOCK_OPENING,
  LOCK_OPEN,
  LOCK_CLOSING,
  LOCK_ERROR
};

struct Lock {
  const char* name;
  int relayPin;
  int sensorPin;
  LockState state;
  unsigned long stateStartTime;
  unsigned long openTime;
};

Lock thicknessLock = {"THICKNESS", RELAY_THICKNESS, SENSOR_THICKNESS, LOCK_CLOSED, 0, 0};
Lock obdLock = {"OBD", RELAY_OBD, SENSOR_OBD, LOCK_CLOSED, 0, 0};

// === Буфер команд ===
char commandBuffer[MAX_COMMAND_LENGTH];
int commandIndex = 0;

// === Watchdog ===
unsigned long lastCommandTime = 0;

// === Функции управления замками ===

/**
 * Инициализация замка
 */
void initLock(Lock& lock) {
  pinMode(lock.relayPin, OUTPUT);
  pinMode(lock.sensorPin, INPUT_PULLUP);
  digitalWrite(lock.relayPin, LOW); // Реле выключено = замок закрыт
  lock.state = LOCK_CLOSED;
  lock.stateStartTime = millis();
  lock.openTime = 0;
}

/**
 * Проверка состояния датчика положения
 * @return true если замок открыт
 */
bool isLockOpen(const Lock& lock) {
  return digitalRead(lock.sensorPin) == HIGH;
}

/**
 * Открыть замок
 */
void openLock(Lock& lock) {
  if (lock.state == LOCK_OPEN) {
    sendResponse("OK:ALREADY_OPEN:" + String(lock.name));
    return;
  }
  
  if (lock.state != LOCK_CLOSED) {
    sendResponse("ERROR:BUSY:" + String(lock.name));
    return;
  }
  
  // Активируем реле
  digitalWrite(lock.relayPin, HIGH);
  lock.state = LOCK_OPENING;
  lock.stateStartTime = millis();
  
  // Индикация активности
  digitalWrite(LED_ACTIVITY, HIGH);
  
  logOperation("OPEN_START", lock.name);
}

/**
 * Закрыть замок
 */
void closeLock(Lock& lock) {
  if (lock.state == LOCK_CLOSED) {
    sendResponse("OK:ALREADY_CLOSED:" + String(lock.name));
    return;
  }
  
  // Деактивируем реле
  digitalWrite(lock.relayPin, LOW);
  lock.state = LOCK_CLOSING;
  lock.stateStartTime = millis();
  lock.openTime = 0;
  
  digitalWrite(LED_ACTIVITY, LOW);
  
  logOperation("CLOSE_START", lock.name);
}

/**
 * Обновление состояния замка
 */
void updateLock(Lock& lock) {
  unsigned long currentTime = millis();
  unsigned long elapsed = currentTime - lock.stateStartTime;
  
  switch (lock.state) {
    case LOCK_OPENING:
      if (isLockOpen(lock)) {
        // Замок успешно открыт
        lock.state = LOCK_OPEN;
        lock.openTime = currentTime;
        sendResponse("OK:OPENED:" + String(lock.name));
        logOperation("OPENED", lock.name);
      } else if (elapsed > LOCK_TIMEOUT) {
        // Таймаут открытия
        digitalWrite(lock.relayPin, LOW);
        lock.state = LOCK_ERROR;
        sendResponse("ERROR:TIMEOUT:" + String(lock.name));
        logOperation("TIMEOUT", lock.name);
      }
      break;
      
    case LOCK_OPEN:
      // Автоматическое закрытие после AUTO_CLOSE_DELAY
      if (currentTime - lock.openTime > AUTO_CLOSE_DELAY) {
        closeLock(lock);
        logOperation("AUTO_CLOSE", lock.name);
      }
      break;
      
    case LOCK_CLOSING:
      if (!isLockOpen(lock)) {
        // Замок успешно закрыт
        lock.state = LOCK_CLOSED;
        sendResponse("OK:CLOSED:" + String(lock.name));
        logOperation("CLOSED", lock.name);
      } else if (elapsed > LOCK_TIMEOUT) {
        // Таймаут закрытия
        lock.state = LOCK_ERROR;
        sendResponse("ERROR:TIMEOUT:" + String(lock.name));
        logOperation("TIMEOUT", lock.name);
      }
      break;
      
    case LOCK_ERROR:
      // Попытка восстановления
      if (elapsed > 5000) {
        digitalWrite(lock.relayPin, LOW);
        lock.state = LOCK_CLOSED;
        logOperation("RECOVERED", lock.name);
      }
      break;
      
    case LOCK_CLOSED:
      // Нормальное состояние, ничего не делаем
      break;
  }
}

// === Функции протокола ===

/**
 * Отправка ответа через Serial
 */
void sendResponse(const String& message) {
  Serial.println(message);
  Serial.flush();
}

/**
 * Логирование операции
 */
void logOperation(const char* operation, const char* lockName) {
  Serial.print("[LOG] ");
  Serial.print(millis());
  Serial.print(" ");
  Serial.print(operation);
  Serial.print(" ");
  Serial.println(lockName);
  Serial.flush();
}

/**
 * Обработка команды
 */
void processCommand(const char* cmd) {
  lastCommandTime = millis();
  
  // Убираем пробелы в начале и конце
  String command = String(cmd);
  command.trim();
  
  if (command.length() == 0) {
    return;
  }
  
  logOperation("CMD_RECEIVED", command.c_str());
  
  // Обработка команд
  if (command == "OPEN_THICKNESS") {
    openLock(thicknessLock);
  }
  else if (command == "OPEN_OBD") {
    openLock(obdLock);
  }
  else if (command == "CLOSE_THICKNESS") {
    closeLock(thicknessLock);
  }
  else if (command == "CLOSE_OBD") {
    closeLock(obdLock);
  }
  else if (command == "STATUS") {
    String status = "STATUS:THICKNESS=";
    status += (thicknessLock.state == LOCK_OPEN ? "OPEN" : "CLOSED");
    status += ",OBD=";
    status += (obdLock.state == LOCK_OPEN ? "OPEN" : "CLOSED");
    sendResponse(status);
  }
  else if (command == "PING") {
    sendResponse("PONG");
  }
  else {
    sendResponse("ERROR:UNKNOWN_COMMAND:" + command);
  }
}

/**
 * Чтение команды из Serial
 */
void readCommand() {
  while (Serial.available() > 0) {
    char c = Serial.read();
    
    if (c == '\n' || c == '\r') {
      if (commandIndex > 0) {
        commandBuffer[commandIndex] = '\0';
        processCommand(commandBuffer);
        commandIndex = 0;
      }
    }
    else if (commandIndex < MAX_COMMAND_LENGTH - 1) {
      commandBuffer[commandIndex++] = c;
    }
    else {
      // Переполнение буфера
      sendResponse("ERROR:BUFFER_OVERFLOW");
      commandIndex = 0;
    }
  }
}

/**
 * Проверка watchdog таймаута
 */
void checkWatchdog() {
  unsigned long currentTime = millis();
  
  if (lastCommandTime > 0 && (currentTime - lastCommandTime > WATCHDOG_TIMEOUT)) {
    // Потеря связи - закрываем все замки
    if (thicknessLock.state == LOCK_OPEN) {
      closeLock(thicknessLock);
      logOperation("WATCHDOG_CLOSE", thicknessLock.name);
    }
    if (obdLock.state == LOCK_OPEN) {
      closeLock(obdLock);
      logOperation("WATCHDOG_CLOSE", obdLock.name);
    }
    lastCommandTime = currentTime; // Сброс таймера
  }
}

// === Arduino callbacks ===

void setup() {
  // Инициализация Serial
  Serial.begin(SERIAL_BAUD);
  while (!Serial) {
    ; // Ожидание подключения Serial порта
  }
  
  // Инициализация LED
  pinMode(LED_ACTIVITY, OUTPUT);
  digitalWrite(LED_ACTIVITY, LOW);
  
  // Инициализация замков
  initLock(thicknessLock);
  initLock(obdLock);
  
  // Приветственное сообщение
  sendResponse("READY:DISPENCER:v1.0");
  logOperation("STARTUP", "SYSTEM");
  
  lastCommandTime = millis();
}

void loop() {
  // Чтение команд
  readCommand();
  
  // Обновление состояния замков
  updateLock(thicknessLock);
  updateLock(obdLock);
  
  // Проверка watchdog
  checkWatchdog();
  
  // Небольшая задержка для стабильности
  delay(10);
}
