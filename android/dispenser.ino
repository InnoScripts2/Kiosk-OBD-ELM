/*
  dispenser.ino
  Dual-door dispensing controller with two MG996R servos and presence sensors.

  Hardware summary / Оборудование:
  - Arduino Uno
  - Servo #0 (OBD) controlled via SERVO_PINS[0] (default D9)
  - Servo #1 (Thickness gauge) via SERVO_PINS[1] (default D10)
  - Presence sensors (buttons) on SENSOR_PINS[] with INPUT_PULLUP (LOW = device present)
  - Shared 5V/GND for both servos; keep grounds common between logic and power.

  Serial commands / Команды:
  - OPEN_OBD        -> open door 0 (OBD)
  - OPEN_THICKNESS  -> open door 1 (thickness gauge)
  - STATUS          -> immediate status print

  States per door:
  CLOSED -> OPENING -> OPEN_WAITING (30 s countdown) ->
  - if device removed -> WAITING_RETURN (wait for return then 10 s countdown)
  - if device still inside after countdown -> CLOSING
  CLOSING -> CLOSED

  Safety:
  - Ignores rapid repeated commands (cooldown)
  - Warns if a door stays open longer than MAX_DOOR_OPEN_TIME

  Output:
  - Periodic STATUS lines for web UI consumption
  - WARNING lines for exceptional events
*/

#include <Servo.h>
#include <EEPROM.h>
#include <string.h>
#include <stdlib.h>
#include <ctype.h>

// --- Pin and timing configuration -------------------------------------------------------

const uint8_t SERVO_COUNT = 2;
const uint8_t SERVO_PINS[SERVO_COUNT] = {9, 10};          // OBD -> D9, Толщиномер -> D10
const uint8_t SENSOR_PINS[SERVO_COUNT] = {2, 3};          // LOW = устройство внутри
const bool DEFAULT_INVERTED[SERVO_COUNT] = {false, true}; // true -> инвертировать направление

const int MIN_LOGICAL_ANGLE = 0;
const int MAX_LOGICAL_ANGLE = 275;
const int SERVO_MIN_PULSE_US = 300;
const int SERVO_MAX_PULSE_US = 2700;
const int LEGACY_MAX_LOGICAL_ANGLE = 250;

const int DEFAULT_CLOSE_ANGLES[SERVO_COUNT] = {200, 230};
const int DEFAULT_OPEN_ANGLES[SERVO_COUNT] = {55, 55};

int doorCloseAngles[SERVO_COUNT] = {200, 230};
int doorOpenAngles[SERVO_COUNT] = {55, 55};
bool doorInverted[SERVO_COUNT] = {DEFAULT_INVERTED[0], DEFAULT_INVERTED[1]};
bool doorLatchedOpen[SERVO_COUNT] = {false, false};
bool doorForceHoldOpen[SERVO_COUNT] = {false, false};

const unsigned long WAIT_BEFORE_CLOSE = 30000UL;        // 30 секунд ожидания
const unsigned long WAIT_AFTER_RETURN = 10000UL;        // 10 секунд после возврата
const unsigned long MAX_DOOR_OPEN_TIME = 300000UL;      // 5 минут в открытом состоянии
const unsigned long STATUS_BROADCAST_INTERVAL = 1000UL; // Интервал отправки статуса
const unsigned long COMMAND_COOLDOWN = 1000UL;          // Пауза между командами

// По-умолчанию скорость/шаг для открытия/закрытия — теперь настраиваемые по дверям
const uint8_t DEFAULT_OPEN_DELAY = 18;  // ms между шагами при открытии (меньше = быстрее)
const uint8_t DEFAULT_OPEN_STEP = 4;    // градусы за шаг при открытии (больше = быстрее)
const uint8_t DEFAULT_CLOSE_DELAY = 18; // ms между шагами при закрытии
const uint8_t DEFAULT_CLOSE_STEP = 4;   // градусы за шаг при закрытии

// Отладочные логи сервопривода сильно замедляют движение при 9600 бод, поэтому ограничиваем частоту
const bool SERVO_VERBOSE_LOGGING = true;
const uint8_t SERVO_LOG_DECIMATION = 10; // 0 = лог на каждый шаг, иначе каждые N шагов

// Псевдомощность — не реальный ток, а параметр для UI/логов (0..100)
const uint8_t DEFAULT_OPEN_POWER = 80;   // условная 'мощность' при открытии
const uint8_t DEFAULT_CLOSE_POWER = 100; // условная 'мощность' при закрытии
const uint8_t DEFAULT_SOFT_START = 18;   // мягкий старт (мс паузы перед началом движения)
const uint8_t DEFAULT_SOFT_STOP = 14;    // мягкая остановка (мс рейзер перед завершением)
const uint8_t DEFAULT_HOLD_POWER = 55;   // "удержание" в процентах (0 = detach)

const uint8_t PROFILE_CUSTOM = 0;
const uint8_t PROFILE_FAST = 1;
const uint8_t PROFILE_GENTLE = 2;
const uint8_t PROFILE_POWER = 3;
const uint8_t PROFILE_COUNT = 4;

const unsigned long AUTOCAL_MOVE_TIMEOUT = 8000UL;
const unsigned long AUTOCAL_SENSOR_TIMEOUT = 12000UL;
const unsigned long AUTOCAL_RETURN_TIMEOUT = 20000UL;
const unsigned long AUTOCAL_CLOSE_TIMEOUT = 12000UL;
const int AUTOCAL_OPEN_MARGIN = 4;
const int AUTOCAL_CLOSE_MARGIN = 6;

uint8_t doorOpenDelay[SERVO_COUNT] = {DEFAULT_OPEN_DELAY, DEFAULT_OPEN_DELAY};
uint8_t doorOpenStep[SERVO_COUNT] = {DEFAULT_OPEN_STEP, DEFAULT_OPEN_STEP};
uint8_t doorCloseDelay[SERVO_COUNT] = {DEFAULT_CLOSE_DELAY, DEFAULT_CLOSE_DELAY};
uint8_t doorCloseStep[SERVO_COUNT] = {DEFAULT_CLOSE_STEP, DEFAULT_CLOSE_STEP};
uint8_t doorOpenPower[SERVO_COUNT] = {DEFAULT_OPEN_POWER, DEFAULT_OPEN_POWER};
uint8_t doorClosePower[SERVO_COUNT] = {DEFAULT_CLOSE_POWER, DEFAULT_CLOSE_POWER};
uint8_t doorSoftStart[SERVO_COUNT] = {DEFAULT_SOFT_START, DEFAULT_SOFT_START};
uint8_t doorSoftStop[SERVO_COUNT] = {DEFAULT_SOFT_STOP, DEFAULT_SOFT_STOP};
uint8_t doorHoldPower[SERVO_COUNT] = {DEFAULT_HOLD_POWER, DEFAULT_HOLD_POWER};
uint8_t doorProfileId[SERVO_COUNT] = {PROFILE_CUSTOM, PROFILE_CUSTOM};

uint8_t servoLogDecimator[SERVO_COUNT] = {0};

struct DoorProfilePreset
{
    const char *name;
    uint8_t openDelay;
    uint8_t openStep;
    uint8_t closeDelay;
    uint8_t closeStep;
    uint8_t openPower;
    uint8_t closePower;
    uint8_t softStart;
    uint8_t softStop;
    uint8_t holdPower;
};

const DoorProfilePreset PROFILE_PRESETS[PROFILE_COUNT] = {
    {"CUSTOM", DEFAULT_OPEN_DELAY, DEFAULT_OPEN_STEP, DEFAULT_CLOSE_DELAY, DEFAULT_CLOSE_STEP, DEFAULT_OPEN_POWER, DEFAULT_CLOSE_POWER, DEFAULT_SOFT_START, DEFAULT_SOFT_STOP, DEFAULT_HOLD_POWER},
    {"FAST", 12, 6, 12, 6, 95, 100, 6, 6, 70},
    {"GENTLE", 35, 2, 35, 2, 70, 80, 22, 20, 50},
    {"POWER", 22, 4, 22, 4, 100, 100, 14, 12, 85}};

const uint8_t EEPROM_MAGIC_ADDR = 0;
const uint8_t EEPROM_MAGIC_VALUE_V1 = 0x43;
const uint8_t EEPROM_MAGIC_VALUE = 0x44;
const uint8_t EEPROM_DATA_ADDR = 1;
// EEPROM layout per door (bytes): close(2), open(2), invert, o_delay, o_step, c_delay, c_step, o_power, c_power, soft_start, soft_stop, hold_power, profile_id
const uint8_t EEPROM_DOOR_STRIDE = 15;
const uint8_t EEPROM_CLOSE_OFFSET = 0;
const uint8_t EEPROM_OPEN_OFFSET = 2;
const uint8_t EEPROM_INVERT_OFFSET = 4;
const uint8_t EEPROM_O_DELAY_OFFSET = 5;
const uint8_t EEPROM_O_STEP_OFFSET = 6;
const uint8_t EEPROM_C_DELAY_OFFSET = 7;
const uint8_t EEPROM_C_STEP_OFFSET = 8;
const uint8_t EEPROM_O_POWER_OFFSET = 9;
const uint8_t EEPROM_C_POWER_OFFSET = 10;
const uint8_t EEPROM_SOFT_START_OFFSET = 11;
const uint8_t EEPROM_SOFT_STOP_OFFSET = 12;
const uint8_t EEPROM_HOLD_POWER_OFFSET = 13;
const uint8_t EEPROM_PROFILE_OFFSET = 14;

// --- State representation ---------------------------------------------------------------

enum DoorState
{
    DOOR_CLOSED = 0,
    DOOR_OPENING,
    DOOR_OPEN_WAITING,
    DOOR_WAITING_RETURN,
    DOOR_CLOSING,
    DOOR_CALIBRATING
};

enum AutoCalPhase : uint8_t
{
    AC_IDLE = 0,
    AC_PREPARE_CLOSE,
    AC_WAIT_SENSOR_READY,
    AC_OPEN_SWEEP,
    AC_WAIT_DEVICE_RETURN,
    AC_CLOSE_SWEEP,
    AC_VALIDATE,
    AC_COMPLETE,
    AC_ABORTED
};

const char *DOOR_STATE_NAMES[] = {
    "CLOSED",
    "OPENING",
    "OPEN_WAITING",
    "WAITING_RETURN",
    "CLOSING",
    "CALIBRATING"};

struct DoorController
{
    Servo servo;
    DoorState state = DOOR_CLOSED;
    bool deviceInside = true;
    bool warningIssued = false;
    unsigned long stateStartMs = 0;
    unsigned long openStartMs = 0;
    unsigned long timerStartMs = 0;
    unsigned long returnWaitStartMs = 0;
    unsigned long lastCommandMs = 0;
    bool statusDirty = true;
    int currentAngle = 0;             // Текущее логическое положение сервопривода
    int targetAngle = 0;              // Цель для плавного движения
    bool moving = false;              // Флаг движения к цели
    unsigned long lastMoveStepMs = 0; // Последний шаг плавного движения
    unsigned long motionStartMs = 0;  // Когда началось движение
    bool servoAttached = false;       // Управление удержанием
    unsigned long detachAfterMs = 0;  // Когда можно отпустить сервопривод

    struct AutoCal
    {
        uint8_t phase = 0; // AutoCalPhase
        bool active = false;
        bool fastMode = false;
        unsigned long phaseStartMs = 0;
        int detectedOpenAngle = -1;
        int detectedCloseAngle = -1;
        bool sensorEdgeCaptured = false;
        unsigned long sensorTimeoutAtMs = 0;
        bool lastSensorState = false;
        int lastResultOpen = -1;
        int lastResultClose = -1;
        bool lastResultFast = false;
        bool lastResultSuccess = false;
        unsigned long lastResultMs = 0;
        unsigned long runStartMs = 0;
        unsigned long lastResultDurationMs = 0;
        char lastResultReason[24];
    } autoCal;
};

DoorController doors[SERVO_COUNT];

// --- Labels for readability -------------------------------------------------------------

const char *DOOR_LABELS[SERVO_COUNT] = {"OBD", "Thickness"};

// --- Serial parsing buffer --------------------------------------------------------------

const size_t COMMAND_BUFFER_SIZE = 32;
char commandBuffer[COMMAND_BUFFER_SIZE];
size_t commandLength = 0;

// --- Timing helpers --------------------------------------------------------------------

unsigned long lastStatusBroadcastMs = 0;

bool calibrationActive = false;
uint8_t calibrationDoor = 255;

// --- Forward declarations ---------------------------------------------------------------

void handleSerial();
void processCommand(char *command);
void requestDoorOpen(uint8_t doorIndex);
void updateDoorState(uint8_t doorIndex);
void beginDoorClosing(uint8_t doorIndex);
void broadcastStatus();
void printDoorStatus(uint8_t doorIndex);
void printWarning(uint8_t doorIndex, const char *message);
bool readDeviceInside(uint8_t doorIndex);
void updateDevicePresence(uint8_t doorIndex);
long calculateRemainingSeconds(uint8_t doorIndex);
const char *boolToString(bool value);
void setDoorTargetAngle(uint8_t doorIndex, int logicalAngle);
void setDoorAngleImmediate(uint8_t doorIndex, int logicalAngle);
void updateServoMotion(uint8_t doorIndex);
void applyPhysicalServoAngle(uint8_t doorIndex, int logicalAngle);
void loadCalibrationFromEEPROM();
void saveCalibrationToEEPROM();
void resetCalibrationToDefaults();
void updateServoAttachment(uint8_t doorIndex, bool forceAttach);
void applyHoldPolicy(uint8_t doorIndex);
void applyProfileToDoor(uint8_t doorIndex, uint8_t profileId);
const char *getProfileName(uint8_t profileId);
void markProfileCustom(uint8_t doorIndex);
void logEvent(const char *level, uint8_t doorIndex, const char *message);
void logEventValue(const char *level, uint8_t doorIndex, const char *label, long value);
void startAutoCalibration(uint8_t doorIndex, bool fastMode);
void abortAutoCalibration(uint8_t doorIndex, const char *reason);
void updateAutoCalibration(uint8_t doorIndex);
const char *getAutoCalPhaseName(uint8_t phase);
void setAutoCalResultReason(uint8_t doorIndex, const char *reason);
void forceToggleDoor(uint8_t doorIndex);
void forceToggleAllDoors();
void forceSetDoorState(uint8_t doorIndex, bool openRequest);
void forceSetAllDoors(bool openRequest);
bool isDoorConsideredOpen(uint8_t doorIndex);
int16_t eepromReadInt16(int addr);
void eepromWriteInt16(int addr, int16_t value);

// ----------------------------------------------------------------------------------------

void setup()
{
    Serial.begin(9600);
    while (!Serial)
    {
        ; // Wait for Serial (for boards with native USB). On Uno this is immediate.
    }

    loadCalibrationFromEEPROM();

    for (uint8_t i = 0; i < SERVO_COUNT; ++i)
    {
        pinMode(SENSOR_PINS[i], INPUT_PULLUP);
        doors[i].servo.attach(SERVO_PINS[i], SERVO_MIN_PULSE_US, SERVO_MAX_PULSE_US);
        doors[i].servoAttached = true;
        doors[i].detachAfterMs = 0;
        setDoorAngleImmediate(i, doorCloseAngles[i]);
        doors[i].state = DOOR_CLOSED;
        doors[i].stateStartMs = millis();
        doors[i].timerStartMs = 0;
        doors[i].openStartMs = 0;
        doors[i].returnWaitStartMs = 0;
        doors[i].warningIssued = false;
        doors[i].deviceInside = readDeviceInside(i);
        doors[i].statusDirty = true;
        // initialize per-door dynamic parameters if EEPROM didn't provide them
        doorOpenDelay[i] = doorOpenDelay[i] ? doorOpenDelay[i] : DEFAULT_OPEN_DELAY;
        doorOpenStep[i] = doorOpenStep[i] ? doorOpenStep[i] : DEFAULT_OPEN_STEP;
        doorCloseDelay[i] = doorCloseDelay[i] ? doorCloseDelay[i] : DEFAULT_CLOSE_DELAY;
        doorCloseStep[i] = doorCloseStep[i] ? doorCloseStep[i] : DEFAULT_CLOSE_STEP;
        doorOpenPower[i] = (doorOpenPower[i] <= 100) ? doorOpenPower[i] : DEFAULT_OPEN_POWER;
        doorClosePower[i] = (doorClosePower[i] <= 100) ? doorClosePower[i] : DEFAULT_CLOSE_POWER;
        doorSoftStart[i] = (doorSoftStart[i] <= 255) ? doorSoftStart[i] : DEFAULT_SOFT_START;
        doorSoftStop[i] = (doorSoftStop[i] <= 255) ? doorSoftStop[i] : DEFAULT_SOFT_STOP;
        doorHoldPower[i] = (doorHoldPower[i] <= 100) ? doorHoldPower[i] : DEFAULT_HOLD_POWER;
        const bool legacySpeed = (doorOpenDelay[i] == 27 && doorOpenStep[i] == 3 && doorCloseDelay[i] == 27 && doorCloseStep[i] == 3);
        if (legacySpeed && doorProfileId[i] == PROFILE_CUSTOM)
        {
            doorOpenDelay[i] = DEFAULT_OPEN_DELAY;
            doorOpenStep[i] = DEFAULT_OPEN_STEP;
            doorCloseDelay[i] = DEFAULT_CLOSE_DELAY;
            doorCloseStep[i] = DEFAULT_CLOSE_STEP;
            doors[i].statusDirty = true;
            logEvent("INFO", i, "SPEED_PRESET_MIGRATED");
        }
        if (doorProfileId[i] >= PROFILE_COUNT)
        {
            doorProfileId[i] = PROFILE_CUSTOM;
        }
        if (doorProfileId[i] != PROFILE_CUSTOM)
        {
            applyProfileToDoor(i, doorProfileId[i]);
        }

        doorLatchedOpen[i] = false;
    }

    Serial.println(F("Dispenser controller ready"));
    Serial.println(F("Commands: OPEN_OBD, OPEN_THICKNESS, STATUS, FORCE_TOGGLE <door|ALL>, FORCE_SET <door|ALL> <OPEN|CLOSE>"));
    Serial.println(F("Calibration: CAL_START <door>, CAL_MOVE <angle>, CAL_SAVE_OPEN, CAL_SAVE_CLOSE, CAL_EXIT"));
    Serial.println(F("Angles: SET_OPEN <door> <angle>, SET_CLOSE <door> <angle>, SET_INVERT <door> <0|1>, SAVE_CALIBRATION, LOAD_CALIBRATION, RESET_CALIBRATION"));
    Serial.println(F("Tuning: SET_SPEED, SET_POWER, SET_SOFT, SET_HOLD, SET_PROFILE"));
    Serial.println(F("Autocal: AUTOCAL_START <door> [FAST], AUTOCAL_ABORT <door>"));
    broadcastStatus();
}

void loop()
{
    handleSerial();

    for (uint8_t i = 0; i < SERVO_COUNT; ++i)
    {
        updateServoMotion(i);
        updateDevicePresence(i);
        updateDoorState(i);
        updateAutoCalibration(i);
        applyHoldPolicy(i);
    }

    const unsigned long now = millis();
    if (now - lastStatusBroadcastMs >= STATUS_BROADCAST_INTERVAL)
    {
        broadcastStatus();
        lastStatusBroadcastMs = now;
    }
}

// --- Command handling ------------------------------------------------------------------

void handleSerial()
{
    while (Serial.available() > 0)
    {
        const char incoming = static_cast<char>(Serial.read());

        if (incoming == '\n' || incoming == '\r')
        {
            if (commandLength > 0)
            {
                commandBuffer[commandLength] = '\0';
                processCommand(commandBuffer);
                commandLength = 0;
            }
        }
        else
        {
            if (commandLength < (COMMAND_BUFFER_SIZE - 1))
            {
                commandBuffer[commandLength++] = incoming;
            }
            else
            {
                commandLength = 0; // Overflow safeguard: reset buffer
            }
        }
    }
}

void processCommand(char *command)
{
    if (!command || !*command)
    {
        return;
    }

    char *token = strtok(command, " ");
    if (!token)
    {
        return;
    }

    if (strcmp(token, "OPEN_OBD") == 0)
    {
        requestDoorOpen(0);
        return;
    }

    if (strcmp(token, "OPEN_THICKNESS") == 0)
    {
        requestDoorOpen(1);
        return;
    }

    if (strcmp(token, "STATUS") == 0)
    {
        broadcastStatus();
        return;
    }

    if (strcmp(token, "FORCE_SET") == 0)
    {
        char *doorToken = strtok(NULL, " ");
        char *stateToken = strtok(NULL, " ");

        if (!doorToken || !stateToken)
        {
            Serial.println(F("ERR: FORCE_SET requires <door> <OPEN|CLOSE>"));
            return;
        }

        for (char *p = doorToken; *p; ++p)
        {
            *p = static_cast<char>(toupper(static_cast<unsigned char>(*p)));
        }

        for (char *p = stateToken; *p; ++p)
        {
            *p = static_cast<char>(toupper(static_cast<unsigned char>(*p)));
        }

        bool openRequest = false;
        if (strcmp(stateToken, "OPEN") == 0)
        {
            openRequest = true;
        }
        else if (strcmp(stateToken, "CLOSE") == 0)
        {
            openRequest = false;
        }
        else
        {
            Serial.println(F("ERR: FORCE_SET state must be OPEN or CLOSE"));
            return;
        }

        if (strcmp(doorToken, "ALL") == 0)
        {
            forceSetAllDoors(openRequest);
            Serial.print(F("CMD: force set all -> "));
            Serial.println(openRequest ? F("OPEN") : F("CLOSE"));
            return;
        }

        char *endPtr = nullptr;
        long doorIdx = strtol(doorToken, &endPtr, 10);
        if (*doorToken == '\0' || (endPtr && *endPtr != '\0') || doorIdx < 0 || doorIdx >= SERVO_COUNT)
        {
            Serial.println(F("ERR: invalid door index"));
            return;
        }

        const uint8_t doorIndex = static_cast<uint8_t>(doorIdx);
        if (doors[doorIndex].state == DOOR_CALIBRATING)
        {
            Serial.print(F("IGNORED: Force set skipped (calibrating) -> "));
            Serial.println(DOOR_LABELS[doorIndex]);
            return;
        }
        forceSetDoorState(doorIndex, openRequest);
        Serial.print(F("CMD: force set -> "));
        Serial.print(DOOR_LABELS[doorIndex]);
        Serial.print(F(" = "));
        Serial.println(openRequest ? F("OPEN") : F("CLOSE"));
        return;
    }

    if (strcmp(token, "FORCE_TOGGLE") == 0)
    {
        char *doorToken = strtok(NULL, " ");
        if (!doorToken)
        {
            Serial.println(F("ERR: FORCE_TOGGLE requires <door>"));
            return;
        }

        if (strcmp(doorToken, "ALL") == 0)
        {
            forceToggleAllDoors();
            Serial.println(F("CMD: force toggle all"));
            return;
        }

        char *endPtr = nullptr;
        long doorIdx = strtol(doorToken, &endPtr, 10);
        if (*doorToken == '\0' || (endPtr && *endPtr != '\0') || doorIdx < 0 || doorIdx >= SERVO_COUNT)
        {
            Serial.println(F("ERR: invalid door index"));
            return;
        }

        const uint8_t doorIndex = static_cast<uint8_t>(doorIdx);
        forceToggleDoor(doorIndex);
        Serial.print(F("CMD: force toggle -> "));
        Serial.println(DOOR_LABELS[doorIndex]);
        return;
    }

    if (strcmp(token, "FORCE_TOGGLE_ALL") == 0)
    {
        forceToggleAllDoors();
        Serial.println(F("CMD: force toggle all"));
        return;
    }

    if (strcmp(token, "SET_OPEN") == 0 || strcmp(token, "SET_CLOSE") == 0)
    {
        const bool isOpen = (strcmp(token, "SET_OPEN") == 0);
        char *doorToken = strtok(NULL, " ");
        char *angleToken = strtok(NULL, " ");

        if (!doorToken || !angleToken)
        {
            Serial.println(F("ERR: SET_* requires <door> <angle>"));
            return;
        }

        char *endPtr = nullptr;
        long doorIdx = strtol(doorToken, &endPtr, 10);
        if (*doorToken == '\0' || (endPtr && *endPtr != '\0') || doorIdx < 0 || doorIdx >= SERVO_COUNT)
        {
            Serial.println(F("ERR: invalid door index"));
            return;
        }

        endPtr = nullptr;
        long angle = strtol(angleToken, &endPtr, 10);
        if (*angleToken == '\0' || (endPtr && *endPtr != '\0') || angle < MIN_LOGICAL_ANGLE || angle > MAX_LOGICAL_ANGLE)
        {
            Serial.println(F("ERR: invalid angle"));
            return;
        }

        const uint8_t doorIndex = static_cast<uint8_t>(doorIdx);
        const int constrained = constrain(static_cast<int>(angle), MIN_LOGICAL_ANGLE, MAX_LOGICAL_ANGLE);
        if (isOpen)
        {
            doorOpenAngles[doorIndex] = constrained;
            Serial.print(F("CAL: door "));
            Serial.print(DOOR_LABELS[doorIndex]);
            Serial.print(F(" open -> "));
            Serial.println(constrained);
        }
        else
        {
            doorCloseAngles[doorIndex] = constrained;
            Serial.print(F("CAL: door "));
            Serial.print(DOOR_LABELS[doorIndex]);
            Serial.print(F(" close -> "));
            Serial.println(constrained);
            setDoorAngleImmediate(doorIndex, doorCloseAngles[doorIndex]);
            doors[doorIndex].statusDirty = true;
        }
        return;
    }

    if (strcmp(token, "SET_INVERT") == 0)
    {
        char *doorToken = strtok(NULL, " ");
        char *flagToken = strtok(NULL, " ");

        if (!doorToken || !flagToken)
        {
            Serial.println(F("ERR: SET_INVERT requires <door> <0|1>"));
            return;
        }

        char *endPtr = nullptr;
        long doorIdx = strtol(doorToken, &endPtr, 10);
        if (*doorToken == '\0' || (endPtr && *endPtr != '\0') || doorIdx < 0 || doorIdx >= SERVO_COUNT)
        {
            Serial.println(F("ERR: invalid door index"));
            return;
        }

        endPtr = nullptr;
        long flagValue = strtol(flagToken, &endPtr, 10);
        if (*flagToken == '\0' || (endPtr && *endPtr != '\0') || (flagValue != 0 && flagValue != 1))
        {
            Serial.println(F("ERR: invert flag must be 0 or 1"));
            return;
        }

        const uint8_t doorIndex = static_cast<uint8_t>(doorIdx);
        doorInverted[doorIndex] = (flagValue == 1);
        DoorController &door = doors[doorIndex];
        door.moving = false;
        door.lastMoveStepMs = 0;
        door.targetAngle = door.currentAngle;
        applyPhysicalServoAngle(doorIndex, door.currentAngle); // reapply with new direction
        doors[doorIndex].statusDirty = true;

        Serial.print(F("CAL: door "));
        Serial.print(DOOR_LABELS[doorIndex]);
        Serial.print(F(" invert -> "));
        Serial.println(doorInverted[doorIndex] ? F("ON") : F("OFF"));
        return;
    }

    if (strcmp(token, "SET_SPEED") == 0)
    {
        // SET_SPEED <door> <o_delay> <o_step> <c_delay> <c_step>
        char *doorToken = strtok(NULL, " ");
        char *oDelayT = strtok(NULL, " ");
        char *oStepT = strtok(NULL, " ");
        char *cDelayT = strtok(NULL, " ");
        char *cStepT = strtok(NULL, " ");
        if (!doorToken || !oDelayT || !oStepT || !cDelayT || !cStepT)
        {
            Serial.println(F("ERR: SET_SPEED requires <door> <o_delay> <o_step> <c_delay> <c_step>"));
            return;
        }
        char *endPtr = nullptr;
        long doorIdx = strtol(doorToken, &endPtr, 10);
        if (*doorToken == '\0' || (endPtr && *endPtr != '\0') || doorIdx < 0 || doorIdx >= SERVO_COUNT)
        {
            Serial.println(F("ERR: invalid door index"));
            return;
        }
        const uint8_t di = static_cast<uint8_t>(doorIdx);
        doorOpenDelay[di] = (uint8_t)constrain(strtol(oDelayT, NULL, 10), 1L, 255L);
        doorOpenStep[di] = (uint8_t)constrain(strtol(oStepT, NULL, 10), 1L, 50L);
        doorCloseDelay[di] = (uint8_t)constrain(strtol(cDelayT, NULL, 10), 1L, 255L);
        doorCloseStep[di] = (uint8_t)constrain(strtol(cStepT, NULL, 10), 1L, 50L);
        markProfileCustom(di);
        doors[di].statusDirty = true;
        Serial.print(F("CAL: speed updated -> "));
        Serial.print(DOOR_LABELS[di]);
        Serial.print(F(" o_delay="));
        Serial.print(doorOpenDelay[di]);
        Serial.print(F(" o_step="));
        Serial.print(doorOpenStep[di]);
        Serial.print(F(" c_delay="));
        Serial.print(doorCloseDelay[di]);
        Serial.print(F(" c_step="));
        Serial.println(doorCloseStep[di]);
        return;
    }

    if (strcmp(token, "SET_POWER") == 0)
    {
        // SET_POWER <door> <o_power> <c_power>
        char *doorToken = strtok(NULL, " ");
        char *oPowerT = strtok(NULL, " ");
        char *cPowerT = strtok(NULL, " ");
        if (!doorToken || !oPowerT || !cPowerT)
        {
            Serial.println(F("ERR: SET_POWER requires <door> <o_power> <c_power>"));
            return;
        }
        char *endPtr = nullptr;
        long doorIdx = strtol(doorToken, &endPtr, 10);
        if (*doorToken == '\0' || (endPtr && *endPtr != '\0') || doorIdx < 0 || doorIdx >= SERVO_COUNT)
        {
            Serial.println(F("ERR: invalid door index"));
            return;
        }
        const uint8_t di = static_cast<uint8_t>(doorIdx);
        doorOpenPower[di] = (uint8_t)constrain(strtol(oPowerT, NULL, 10), 0L, 100L);
        doorClosePower[di] = (uint8_t)constrain(strtol(cPowerT, NULL, 10), 0L, 100L);
        markProfileCustom(di);
        doors[di].statusDirty = true;
        Serial.print(F("CAL: power updated -> "));
        Serial.print(DOOR_LABELS[di]);
        Serial.print(F(" o_power="));
        Serial.print(doorOpenPower[di]);
        Serial.print(F(" c_power="));
        Serial.println(doorClosePower[di]);
        return;
    }

    if (strcmp(token, "SET_SOFT") == 0)
    {
        // SET_SOFT <door> <soft_start> <soft_stop>
        char *doorToken = strtok(NULL, " ");
        char *softStartT = strtok(NULL, " ");
        char *softStopT = strtok(NULL, " ");
        if (!doorToken || !softStartT || !softStopT)
        {
            Serial.println(F("ERR: SET_SOFT requires <door> <soft_start> <soft_stop>"));
            return;
        }
        char *endPtr = nullptr;
        long doorIdx = strtol(doorToken, &endPtr, 10);
        if (*doorToken == '\0' || (endPtr && *endPtr != '\0') || doorIdx < 0 || doorIdx >= SERVO_COUNT)
        {
            Serial.println(F("ERR: invalid door index"));
            return;
        }
        const uint8_t di = static_cast<uint8_t>(doorIdx);
        doorSoftStart[di] = (uint8_t)constrain(strtol(softStartT, NULL, 10), 0L, 255L);
        doorSoftStop[di] = (uint8_t)constrain(strtol(softStopT, NULL, 10), 0L, 255L);
        markProfileCustom(di);
        doors[di].statusDirty = true;
        Serial.print(F("CAL: soft updated -> "));
        Serial.print(DOOR_LABELS[di]);
        Serial.print(F(" soft_start="));
        Serial.print(doorSoftStart[di]);
        Serial.print(F(" soft_stop="));
        Serial.println(doorSoftStop[di]);
        return;
    }

    if (strcmp(token, "SET_HOLD") == 0)
    {
        // SET_HOLD <door> <hold_power>
        char *doorToken = strtok(NULL, " ");
        char *holdToken = strtok(NULL, " ");
        if (!doorToken || !holdToken)
        {
            Serial.println(F("ERR: SET_HOLD requires <door> <hold_power>"));
            return;
        }
        char *endPtr = nullptr;
        long doorIdx = strtol(doorToken, &endPtr, 10);
        if (*doorToken == '\0' || (endPtr && *endPtr != '\0') || doorIdx < 0 || doorIdx >= SERVO_COUNT)
        {
            Serial.println(F("ERR: invalid door index"));
            return;
        }
        const uint8_t di = static_cast<uint8_t>(doorIdx);
        doorHoldPower[di] = (uint8_t)constrain(strtol(holdToken, NULL, 10), 0L, 100L);
        markProfileCustom(di);
        doors[di].statusDirty = true;
        Serial.print(F("CAL: hold updated -> "));
        Serial.print(DOOR_LABELS[di]);
        Serial.print(F(" hold="));
        Serial.println(doorHoldPower[di]);
        applyHoldPolicy(di);
        return;
    }

    if (strcmp(token, "SET_PROFILE") == 0)
    {
        // SET_PROFILE <door> <profile_id|name>
        char *doorToken = strtok(NULL, " ");
        char *profileToken = strtok(NULL, " ");
        if (!doorToken || !profileToken)
        {
            Serial.println(F("ERR: SET_PROFILE requires <door> <profile>"));
            return;
        }
        char *endPtr = nullptr;
        long doorIdx = strtol(doorToken, &endPtr, 10);
        if (*doorToken == '\0' || (endPtr && *endPtr != '\0') || doorIdx < 0 || doorIdx >= SERVO_COUNT)
        {
            Serial.println(F("ERR: invalid door index"));
            return;
        }
        uint8_t resolvedProfile = PROFILE_CUSTOM;
        bool profileValid = false;
        if (isdigit(static_cast<unsigned char>(profileToken[0])))
        {
            long parsed = strtol(profileToken, &endPtr, 10);
            if (*profileToken != '\0' && !(endPtr && *endPtr != '\0') && parsed >= 0 && parsed < PROFILE_COUNT)
            {
                resolvedProfile = static_cast<uint8_t>(parsed);
                profileValid = true;
            }
        }
        if (!profileValid)
        {
            char upperBuf[12];
            size_t len = strlen(profileToken);
            if (len >= sizeof(upperBuf))
            {
                len = sizeof(upperBuf) - 1;
            }
            for (size_t i = 0; i < len; ++i)
            {
                upperBuf[i] = (char)toupper(static_cast<unsigned char>(profileToken[i]));
            }
            upperBuf[len] = '\0';
            for (uint8_t idx = 0; idx < PROFILE_COUNT; ++idx)
            {
                if (strcmp(upperBuf, PROFILE_PRESETS[idx].name) == 0)
                {
                    resolvedProfile = idx;
                    profileValid = true;
                    break;
                }
            }
        }
        if (!profileValid)
        {
            Serial.println(F("ERR: unknown profile"));
            return;
        }
        const uint8_t di = static_cast<uint8_t>(doorIdx);
        applyProfileToDoor(di, resolvedProfile);
        applyHoldPolicy(di);
        Serial.print(F("PROFILE: "));
        Serial.print(DOOR_LABELS[di]);
        Serial.print(F(" -> "));
        Serial.println(getProfileName(resolvedProfile));
        return;
    }

    if (strcmp(token, "AUTOCAL_START") == 0)
    {
        // AUTOCAL_START <door> [FAST]
        char *doorToken = strtok(NULL, " ");
        char *modeToken = strtok(NULL, " ");
        if (!doorToken)
        {
            Serial.println(F("ERR: AUTOCAL_START requires <door> [FAST]"));
            return;
        }
        char *endPtr = nullptr;
        long doorIdx = strtol(doorToken, &endPtr, 10);
        if (*doorToken == '\0' || (endPtr && *endPtr != '\0') || doorIdx < 0 || doorIdx >= SERVO_COUNT)
        {
            Serial.println(F("ERR: invalid door index"));
            return;
        }
        bool fastMode = false;
        if (modeToken)
        {
            char upperBuf[8];
            size_t len = strlen(modeToken);
            if (len >= sizeof(upperBuf))
            {
                len = sizeof(upperBuf) - 1;
            }
            for (size_t i = 0; i < len; ++i)
            {
                upperBuf[i] = (char)toupper(static_cast<unsigned char>(modeToken[i]));
            }
            upperBuf[len] = '\0';
            if (strcmp(upperBuf, "FAST") == 0 || strcmp(upperBuf, "1") == 0)
            {
                fastMode = true;
            }
        }
        startAutoCalibration(static_cast<uint8_t>(doorIdx), fastMode);
        return;
    }

    if (strcmp(token, "AUTOCAL_ABORT") == 0)
    {
        // AUTOCAL_ABORT <door>
        char *doorToken = strtok(NULL, " ");
        if (!doorToken)
        {
            Serial.println(F("ERR: AUTOCAL_ABORT requires <door>"));
            return;
        }
        char *endPtr = nullptr;
        long doorIdx = strtol(doorToken, &endPtr, 10);
        if (*doorToken == '\0' || (endPtr && *endPtr != '\0') || doorIdx < 0 || doorIdx >= SERVO_COUNT)
        {
            Serial.println(F("ERR: invalid door index"));
            return;
        }
        abortAutoCalibration(static_cast<uint8_t>(doorIdx), "USER_ABORT");
        return;
    }

    if (strcmp(token, "AUTOCAL_SWEEP") == 0)
    {
        // AUTOCAL_SWEEP <door> -- blocking sweep from min to max and back, logs microsteps
        char *doorToken = strtok(NULL, " ");
        if (!doorToken)
        {
            Serial.println(F("ERR: AUTOCAL_SWEEP requires <door>"));
            return;
        }
        char *endPtr = nullptr;
        long doorIdx = strtol(doorToken, &endPtr, 10);
        if (*doorToken == '\0' || (endPtr && *endPtr != '\0') || doorIdx < 0 || doorIdx >= SERVO_COUNT)
        {
            Serial.println(F("ERR: invalid door index"));
            return;
        }
        const uint8_t di = static_cast<uint8_t>(doorIdx);
        Serial.print(F("AUTOCAL: starting sweep -> "));
        Serial.println(DOOR_LABELS[di]);
        // sweep to min
        for (int a = doorCloseAngles[di]; a >= MIN_LOGICAL_ANGLE; a -= 2)
        {
            applyPhysicalServoAngle(di, a);
            delay(12);
        }
        Serial.println(F("AUTOCAL: reached min"));
        delay(250);
        // sweep to max
        for (int a = MIN_LOGICAL_ANGLE; a <= doorCloseAngles[di]; a += 2)
        {
            applyPhysicalServoAngle(di, a);
            delay(12);
        }
        Serial.println(F("AUTOCAL: sweep complete. Please verify endpoints and save calibration if correct."));
        doors[di].statusDirty = true;
        return;
    }

    if (strcmp(token, "SAVE_CALIBRATION") == 0)
    {
        saveCalibrationToEEPROM();
        return;
    }

    if (strcmp(token, "LOAD_CALIBRATION") == 0)
    {
        loadCalibrationFromEEPROM();
        for (uint8_t i = 0; i < SERVO_COUNT; ++i)
        {
            setDoorAngleImmediate(i, doorCloseAngles[i]);
            doors[i].statusDirty = true;
        }
        if (calibrationActive && calibrationDoor < SERVO_COUNT)
        {
            calibrationActive = false;
            DoorController &door = doors[calibrationDoor];
            door.state = DOOR_CLOSED;
            door.statusDirty = true;
            setDoorAngleImmediate(calibrationDoor, doorCloseAngles[calibrationDoor]);
            calibrationDoor = 255;
        }
        Serial.println(F("CAL: values reloaded"));
        broadcastStatus();
        return;
    }

    if (strcmp(token, "RESET_CALIBRATION") == 0)
    {
        resetCalibrationToDefaults();
        for (uint8_t i = 0; i < SERVO_COUNT; ++i)
        {
            setDoorAngleImmediate(i, doorCloseAngles[i]);
            doors[i].statusDirty = true;
        }
        if (calibrationActive && calibrationDoor < SERVO_COUNT)
        {
            calibrationActive = false;
            DoorController &door = doors[calibrationDoor];
            door.state = DOOR_CLOSED;
            door.statusDirty = true;
            setDoorAngleImmediate(calibrationDoor, doorCloseAngles[calibrationDoor]);
            calibrationDoor = 255;
        }
        Serial.println(F("CAL: defaults applied"));
        broadcastStatus();
        return;
    }

    if (strcmp(token, "CAL_START") == 0)
    {
        char *doorToken = strtok(NULL, " ");
        if (!doorToken)
        {
            Serial.println(F("ERR: CAL_START requires <door>"));
            return;
        }
        char *endPtr = nullptr;
        long doorIdx = strtol(doorToken, &endPtr, 10);
        if (*doorToken == '\0' || (endPtr && *endPtr != '\0') || doorIdx < 0 || doorIdx >= SERVO_COUNT)
        {
            Serial.println(F("ERR: invalid door index"));
            return;
        }
        const uint8_t doorIndex = static_cast<uint8_t>(doorIdx);
        if (calibrationActive && calibrationDoor != doorIndex)
        {
            Serial.println(F("ERR: finish current calibration first"));
            return;
        }

        DoorController &door = doors[doorIndex];
        if (door.state != DOOR_CLOSED && door.state != DOOR_CALIBRATING)
        {
            Serial.println(F("ERR: door busy"));
            return;
        }
        calibrationActive = true;
        calibrationDoor = doorIndex;
        door.state = DOOR_CALIBRATING;
        door.statusDirty = true;
        Serial.print(F("CAL: start -> "));
        Serial.println(DOOR_LABELS[doorIndex]);
        broadcastStatus();
        return;
    }

    if (strcmp(token, "CAL_MOVE") == 0)
    {
        if (!calibrationActive || calibrationDoor >= SERVO_COUNT)
        {
            Serial.println(F("ERR: start calibration first"));
            return;
        }
        char *angleToken = strtok(NULL, " ");
        if (!angleToken)
        {
            Serial.println(F("ERR: CAL_MOVE requires <angle>"));
            return;
        }
        char *endPtr = nullptr;
        long angle = strtol(angleToken, &endPtr, 10);
        if (*angleToken == '\0' || (endPtr && *endPtr != '\0') || angle < MIN_LOGICAL_ANGLE || angle > MAX_LOGICAL_ANGLE)
        {
            Serial.println(F("ERR: invalid angle"));
            return;
        }
        setDoorTargetAngle(calibrationDoor, static_cast<int>(angle));
        doors[calibrationDoor].statusDirty = true;
        Serial.print(F("CAL: move -> "));
        Serial.println(angle);
        return;
    }

    if (strcmp(token, "CAL_SAVE_OPEN") == 0)
    {
        if (!calibrationActive || calibrationDoor >= SERVO_COUNT)
        {
            Serial.println(F("ERR: start calibration first"));
            return;
        }
        const int angle = doors[calibrationDoor].moving ? doors[calibrationDoor].targetAngle : doors[calibrationDoor].currentAngle;
        doorOpenAngles[calibrationDoor] = constrain(angle, MIN_LOGICAL_ANGLE, MAX_LOGICAL_ANGLE);
        doors[calibrationDoor].statusDirty = true;
        Serial.print(F("CAL: saved open -> "));
        Serial.println(doorOpenAngles[calibrationDoor]);
        return;
    }

    if (strcmp(token, "CAL_SAVE_CLOSE") == 0)
    {
        if (!calibrationActive || calibrationDoor >= SERVO_COUNT)
        {
            Serial.println(F("ERR: start calibration first"));
            return;
        }
        const int angle = doors[calibrationDoor].moving ? doors[calibrationDoor].targetAngle : doors[calibrationDoor].currentAngle;
        doorCloseAngles[calibrationDoor] = constrain(angle, MIN_LOGICAL_ANGLE, MAX_LOGICAL_ANGLE);
        doors[calibrationDoor].statusDirty = true;
        Serial.print(F("CAL: saved close -> "));
        Serial.println(doorCloseAngles[calibrationDoor]);
        return;
    }

    if (strcmp(token, "CAL_EXIT") == 0)
    {
        if (!calibrationActive || calibrationDoor >= SERVO_COUNT)
        {
            Serial.println(F("ERR: calibration not active"));
            return;
        }
        DoorController &door = doors[calibrationDoor];
        calibrationActive = false;
        door.state = DOOR_CLOSED;
        door.statusDirty = true;
        setDoorAngleImmediate(calibrationDoor, doorCloseAngles[calibrationDoor]);
        Serial.print(F("CAL: exit -> "));
        Serial.println(DOOR_LABELS[calibrationDoor]);
        calibrationDoor = 255;
        broadcastStatus();
        return;
    }

    Serial.print(F("UNKNOWN_COMMAND:"));
    Serial.println(token);
}

void requestDoorOpen(uint8_t doorIndex)
{
    if (doorIndex >= SERVO_COUNT)
    {
        return;
    }

    DoorController &door = doors[doorIndex];
    const unsigned long now = millis();

    if (door.state != DOOR_CLOSED)
    {
        Serial.print(F("IGNORED: Door already active -> "));
        Serial.println(DOOR_LABELS[doorIndex]);
        return;
    }

    if (door.state == DOOR_CALIBRATING || (calibrationActive && calibrationDoor == doorIndex))
    {
        Serial.print(F("IGNORED: Calibration active -> "));
        Serial.println(DOOR_LABELS[doorIndex]);
        return;
    }

    if ((now - door.lastCommandMs) < COMMAND_COOLDOWN)
    {
        Serial.print(F("IGNORED: Command cooldown -> "));
        Serial.println(DOOR_LABELS[doorIndex]);
        return;
    }

    doorForceHoldOpen[doorIndex] = false;
    door.lastCommandMs = now;
    door.warningIssued = false;
    door.openStartMs = now;
    door.timerStartMs = now;
    door.stateStartMs = now;
    door.state = DOOR_OPENING;
    setDoorTargetAngle(doorIndex, doorOpenAngles[doorIndex]);
    doorLatchedOpen[doorIndex] = true;

    Serial.print(F("CMD: Opening door -> "));
    Serial.println(DOOR_LABELS[doorIndex]);
    door.statusDirty = true;
}

// --- State machine ---------------------------------------------------------------------

void updateDoorState(uint8_t doorIndex)
{
    DoorController &door = doors[doorIndex];
    const unsigned long now = millis();

    switch (door.state)
    {
    case DOOR_CLOSED:
        // Idle; nothing to do.
        break;

    case DOOR_OPENING:
        if (!door.moving && door.currentAngle == doorOpenAngles[doorIndex])
        {
            door.state = DOOR_OPEN_WAITING;
            door.stateStartMs = now;
            door.timerStartMs = now;
            door.statusDirty = true;
            doorLatchedOpen[doorIndex] = true;
            Serial.print(F("STATE: Door opened -> "));
            Serial.println(DOOR_LABELS[doorIndex]);
        }
        break;

    case DOOR_OPEN_WAITING:
        if (doorForceHoldOpen[doorIndex])
        {
            break;
        }
        if (!door.deviceInside)
        {
            door.state = DOOR_WAITING_RETURN;
            door.stateStartMs = now;
            door.returnWaitStartMs = 0;
            door.statusDirty = true;
            Serial.print(F("STATE: Waiting for return -> "));
            Serial.println(DOOR_LABELS[doorIndex]);
        }
        else if (now - door.timerStartMs >= WAIT_BEFORE_CLOSE)
        {
            beginDoorClosing(doorIndex);
        }
        else if (!door.warningIssued && (now - door.openStartMs >= MAX_DOOR_OPEN_TIME))
        {
            printWarning(doorIndex, "OPEN_TOO_LONG_WITH_DEVICE");
        }
        break;

    case DOOR_WAITING_RETURN:
        if (doorForceHoldOpen[doorIndex])
        {
            break;
        }
        if (door.deviceInside)
        {
            if (door.returnWaitStartMs == 0)
            {
                door.returnWaitStartMs = now;
                door.statusDirty = true;
                Serial.print(F("STATE: Device returned, countdown -> "));
                Serial.println(DOOR_LABELS[doorIndex]);
            }
            else if (now - door.returnWaitStartMs >= WAIT_AFTER_RETURN)
            {
                beginDoorClosing(doorIndex);
            }
        }
        else
        {
            door.returnWaitStartMs = 0;
        }

        if (!door.warningIssued && (now - door.openStartMs >= MAX_DOOR_OPEN_TIME))
        {
            printWarning(doorIndex, "OPEN_TOO_LONG_NO_DEVICE");
        }
        break;

    case DOOR_CLOSING:
        if (!door.moving && door.currentAngle == doorCloseAngles[doorIndex])
        {
            door.state = DOOR_CLOSED;
            door.stateStartMs = now;
            door.timerStartMs = 0;
            door.returnWaitStartMs = 0;
            door.openStartMs = 0;
            door.warningIssued = false;
            door.statusDirty = true;
            doorLatchedOpen[doorIndex] = false;
            Serial.print(F("STATE: Door closed -> "));
            Serial.println(DOOR_LABELS[doorIndex]);
        }
        break;

    case DOOR_CALIBRATING:
        // Manual control; nothing to update automatically.
        break;
    }
}

void beginDoorClosing(uint8_t doorIndex)
{
    DoorController &door = doors[doorIndex];
    doorForceHoldOpen[doorIndex] = false;
    door.state = DOOR_CLOSING;
    door.stateStartMs = millis();
    door.statusDirty = true;
    setDoorTargetAngle(doorIndex, doorCloseAngles[doorIndex]);
    Serial.print(F("STATE: Closing door -> "));
    Serial.println(DOOR_LABELS[doorIndex]);
}

// --- Sensor updates --------------------------------------------------------------------

bool readDeviceInside(uint8_t doorIndex)
{
    return digitalRead(SENSOR_PINS[doorIndex]) == LOW; // LOW = device present
}

void updateDevicePresence(uint8_t doorIndex)
{
    DoorController &door = doors[doorIndex];
    const bool currentPresence = readDeviceInside(doorIndex);
    if (currentPresence != door.deviceInside)
    {
        door.deviceInside = currentPresence;
        door.statusDirty = true;

        Serial.print(F("SENSOR: "));
        Serial.print(DOOR_LABELS[doorIndex]);
        Serial.print(F(" presence -> "));
        Serial.println(door.deviceInside ? F("INSIDE") : F("REMOVED"));
    }
}

// --- Status output ---------------------------------------------------------------------

void broadcastStatus()
{
    Serial.print(F("STATUS:"));
    for (uint8_t i = 0; i < SERVO_COUNT; ++i)
    {
        printDoorStatus(i);
        if (i == 0)
        {
            Serial.print(';');
        }
    }
    Serial.println();

    for (uint8_t i = 0; i < SERVO_COUNT; ++i)
    {
        doors[i].statusDirty = false;
    }
}

void printDoorStatus(uint8_t doorIndex)
{
    DoorController &door = doors[doorIndex];
    Serial.print(F("DOOR"));
    Serial.print(doorIndex);
    Serial.print('=');
    Serial.print(DOOR_STATE_NAMES[door.state]);
    Serial.print(F(",DEVICE"));
    Serial.print(doorIndex);
    Serial.print('=');
    Serial.print(boolToString(door.deviceInside));
    Serial.print(F(",FORCE_HOLD"));
    Serial.print(doorIndex);
    Serial.print('=');
    Serial.print(boolToString(doorForceHoldOpen[doorIndex]));
    Serial.print(F(",TIMER"));
    Serial.print(doorIndex);
    Serial.print('=');
    Serial.print(calculateRemainingSeconds(doorIndex));
    Serial.print(F(",INVERT"));
    Serial.print(doorIndex);
    Serial.print('=');
    Serial.print(doorInverted[doorIndex] ? F("ON") : F("OFF"));
    // extended runtime parameters for UI / diagnostics
    Serial.print(F(",O_DELAY"));
    Serial.print(doorIndex);
    Serial.print('=');
    Serial.print(doorOpenDelay[doorIndex]);
    Serial.print(F(",O_STEP"));
    Serial.print(doorIndex);
    Serial.print('=');
    Serial.print(doorOpenStep[doorIndex]);
    Serial.print(F(",C_DELAY"));
    Serial.print(doorIndex);
    Serial.print('=');
    Serial.print(doorCloseDelay[doorIndex]);
    Serial.print(F(",C_STEP"));
    Serial.print(doorIndex);
    Serial.print('=');
    Serial.print(doorCloseStep[doorIndex]);
    Serial.print(F(",O_POWER"));
    Serial.print(doorIndex);
    Serial.print('=');
    Serial.print(doorOpenPower[doorIndex]);
    Serial.print(F(",C_POWER"));
    Serial.print(doorIndex);
    Serial.print('=');
    Serial.print(doorClosePower[doorIndex]);
    Serial.print(F(",SOFT_START"));
    Serial.print(doorIndex);
    Serial.print('=');
    Serial.print(doorSoftStart[doorIndex]);
    Serial.print(F(",SOFT_STOP"));
    Serial.print(doorIndex);
    Serial.print('=');
    Serial.print(doorSoftStop[doorIndex]);
    Serial.print(F(",HOLD"));
    Serial.print(doorIndex);
    Serial.print('=');
    Serial.print(doorHoldPower[doorIndex]);
    Serial.print(F(",PROFILE"));
    Serial.print(doorIndex);
    Serial.print('=');
    Serial.print(getProfileName(doorProfileId[doorIndex]));
    Serial.print(F(",AUTO_ACTIVE"));
    Serial.print(doorIndex);
    Serial.print('=');
    Serial.print(boolToString(door.autoCal.active));
    Serial.print(F(",AUTO_PHASE"));
    Serial.print(doorIndex);
    Serial.print('=');
    Serial.print(getAutoCalPhaseName(door.autoCal.phase));
    Serial.print(F(",AUTO_FAST"));
    Serial.print(doorIndex);
    Serial.print('=');
    Serial.print(door.autoCal.fastMode ? F("TRUE") : F("FALSE"));
    Serial.print(F(",AUTO_LAST_OPEN"));
    Serial.print(doorIndex);
    Serial.print('=');
    Serial.print(door.autoCal.lastResultOpen);
    Serial.print(F(",AUTO_LAST_CLOSE"));
    Serial.print(doorIndex);
    Serial.print('=');
    Serial.print(door.autoCal.lastResultClose);
    Serial.print(F(",AUTO_LAST_FAST"));
    Serial.print(doorIndex);
    Serial.print('=');
    Serial.print(door.autoCal.lastResultFast ? F("TRUE") : F("FALSE"));
    Serial.print(F(",AUTO_LAST_OK"));
    Serial.print(doorIndex);
    Serial.print('=');
    Serial.print(boolToString(door.autoCal.lastResultSuccess));
    Serial.print(F(",AUTO_LAST_MS"));
    Serial.print(doorIndex);
    Serial.print('=');
    Serial.print(door.autoCal.lastResultMs);
    Serial.print(F(",AUTO_LAST_DUR"));
    Serial.print(doorIndex);
    Serial.print('=');
    Serial.print(door.autoCal.lastResultDurationMs);
    Serial.print(F(",AUTO_LAST_REASON"));
    Serial.print(doorIndex);
    Serial.print('=');
    if (door.autoCal.lastResultReason[0])
    {
        Serial.print(door.autoCal.lastResultReason);
    }
    else
    {
        Serial.print(F("NONE"));
    }
}

long calculateRemainingSeconds(uint8_t doorIndex)
{
    const DoorController &door = doors[doorIndex];
    const unsigned long now = millis();

    switch (door.state)
    {
    case DOOR_OPEN_WAITING:
    {
        const long remaining = (long)WAIT_BEFORE_CLOSE - (long)(now - door.timerStartMs);
        return remaining > 0 ? remaining / 1000L : 0L;
    }
    case DOOR_WAITING_RETURN:
        if (door.deviceInside && door.returnWaitStartMs > 0)
        {
            const long remaining = (long)WAIT_AFTER_RETURN - (long)(now - door.returnWaitStartMs);
            return remaining > 0 ? remaining / 1000L : 0L;
        }
        return -1L; // Indicates waiting for device return
    case DOOR_OPENING:
    case DOOR_CLOSING:
        if (!door.moving)
        {
            return 0L;
        }
        else
        {
            const int remainingDelta = abs(door.targetAngle - door.currentAngle);
            // choose step/delay depending on direction
            const bool movingOpening = (door.targetAngle > door.currentAngle);
            const int step = movingOpening ? doorOpenStep[doorIndex] : doorCloseStep[doorIndex];
            const int delayMs = movingOpening ? doorOpenDelay[doorIndex] : doorCloseDelay[doorIndex];
            const int steps = (remainingDelta + step - 1) / (step > 0 ? step : 1);
            const long remainingMs = (long)steps * (long)delayMs;
            return remainingMs > 0 ? remainingMs / 1000L : 0L;
        }
    default:
        return 0L;
    }
}

void printWarning(uint8_t doorIndex, const char *message)
{
    DoorController &door = doors[doorIndex];
    door.warningIssued = true;
    Serial.print(F("WARNING:"));
    Serial.print(DOOR_LABELS[doorIndex]);
    Serial.print(':');
    Serial.println(message);
}

const char *boolToString(bool value)
{
    return value ? "TRUE" : "FALSE";
}

void setDoorTargetAngle(uint8_t doorIndex, int logicalAngle)
{
    if (doorIndex >= SERVO_COUNT)
    {
        return;
    }

    DoorController &door = doors[doorIndex];
    const int constrained = constrain(logicalAngle, MIN_LOGICAL_ANGLE, MAX_LOGICAL_ANGLE);
    door.targetAngle = constrained;
    if (door.currentAngle == constrained)
    {
        door.moving = false;
        door.lastMoveStepMs = 0;
        door.motionStartMs = 0;
        return;
    }

    door.moving = true;
    door.lastMoveStepMs = 0;
    door.motionStartMs = millis();
    servoLogDecimator[doorIndex] = 0;
}

void setDoorAngleImmediate(uint8_t doorIndex, int logicalAngle)
{
    if (doorIndex >= SERVO_COUNT)
    {
        return;
    }

    DoorController &door = doors[doorIndex];
    const int constrained = constrain(logicalAngle, MIN_LOGICAL_ANGLE, MAX_LOGICAL_ANGLE);
    door.currentAngle = constrained;
    door.targetAngle = constrained;
    door.moving = false;
    door.lastMoveStepMs = 0;
    door.motionStartMs = 0;
    servoLogDecimator[doorIndex] = 0;
    applyPhysicalServoAngle(doorIndex, constrained);
}

bool isDoorConsideredOpen(uint8_t doorIndex)
{
    if (doorIndex >= SERVO_COUNT)
    {
        return false;
    }

    return doorLatchedOpen[doorIndex];
}

void forceSetDoorState(uint8_t doorIndex, bool openRequest)
{
    if (doorIndex >= SERVO_COUNT)
    {
        return;
    }

    DoorController &door = doors[doorIndex];
    const unsigned long now = millis();

    if (door.autoCal.active)
    {
        abortAutoCalibration(doorIndex, "FORCE_TOGGLE");
    }

    if (openRequest)
    {
        setDoorAngleImmediate(doorIndex, doorOpenAngles[doorIndex]);
        door.state = DOOR_OPEN_WAITING;
        door.stateStartMs = now;
        door.openStartMs = now;
        door.timerStartMs = now;
        door.returnWaitStartMs = 0;
        door.warningIssued = false;
        logEvent("INFO", doorIndex, "FORCE_OPEN");
        doorLatchedOpen[doorIndex] = true;
        doorForceHoldOpen[doorIndex] = true;
    }
    else
    {
        setDoorAngleImmediate(doorIndex, doorCloseAngles[doorIndex]);
        door.state = DOOR_CLOSED;
        door.stateStartMs = now;
        door.openStartMs = 0;
        door.timerStartMs = 0;
        door.returnWaitStartMs = 0;
        door.warningIssued = false;
        logEvent("INFO", doorIndex, "FORCE_CLOSE");
        doorLatchedOpen[doorIndex] = false;
        doorForceHoldOpen[doorIndex] = false;
    }

    door.deviceInside = readDeviceInside(doorIndex);
    door.lastCommandMs = now;
    door.statusDirty = true;
}

void forceToggleDoor(uint8_t doorIndex)
{
    if (doorIndex >= SERVO_COUNT)
    {
        return;
    }

    DoorController &door = doors[doorIndex];
    if (door.state == DOOR_CALIBRATING)
    {
        Serial.print(F("IGNORED: Door calibrating -> "));
        Serial.println(DOOR_LABELS[doorIndex]);
        return;
    }

    const bool currentlyOpen = isDoorConsideredOpen(doorIndex);
    forceSetDoorState(doorIndex, !currentlyOpen);
}

void forceToggleAllDoors()
{
    bool anyOpen = false;
    for (uint8_t i = 0; i < SERVO_COUNT; ++i)
    {
        if (doors[i].state == DOOR_CALIBRATING)
        {
            continue;
        }

        if (doorLatchedOpen[i])
        {
            anyOpen = true;
            break;
        }
    }

    const bool targetOpen = !anyOpen;
    forceSetAllDoors(targetOpen);
}

void forceSetAllDoors(bool openRequest)
{
    bool acted = false;
    for (uint8_t i = 0; i < SERVO_COUNT; ++i)
    {
        if (doors[i].state == DOOR_CALIBRATING)
        {
            Serial.print(F("IGNORED: Force set skipped (calibrating) -> "));
            Serial.println(DOOR_LABELS[i]);
            continue;
        }

        forceSetDoorState(i, openRequest);
        acted = true;
    }

    if (!acted)
    {
        Serial.println(F("CMD: force set all skipped (no eligible doors)"));
    }
}

void updateServoMotion(uint8_t doorIndex)
{
    if (doorIndex >= SERVO_COUNT)
    {
        return;
    }

    DoorController &door = doors[doorIndex];
    if (!door.moving)
    {
        return;
    }

    const unsigned long now = millis();
    // choose per-door delay depending on opening/closing
    const int deltaNow = door.targetAngle - door.currentAngle;
    const bool willOpen = deltaNow > 0;
    const unsigned long perDoorDelay = willOpen ? doorOpenDelay[doorIndex] : doorCloseDelay[doorIndex];
    const unsigned long softLead = willOpen ? doorSoftStart[doorIndex] : doorSoftStart[doorIndex];
    const unsigned long softTail = doorSoftStop[doorIndex];

    if (door.motionStartMs != 0 && (now - door.motionStartMs) < softLead)
    {
        door.lastMoveStepMs = now;
        return;
    }

    unsigned long requiredDelay = perDoorDelay;
    if (door.lastMoveStepMs != 0)
    {
        const unsigned long elapsed = now - door.lastMoveStepMs;
        if (elapsed < requiredDelay)
        {
            return;
        }
    }

    const int delta = door.targetAngle - door.currentAngle;
    const int direction = (delta > 0) ? 1 : -1;
    // choose per-door step depending on opening/closing
    int step = (delta > 0) ? doorOpenStep[doorIndex] : doorCloseStep[doorIndex];
    const int absDelta = abs(delta);
    if (absDelta < step)
    {
        step = absDelta;
    }

    if (step <= 0)
    {
        door.moving = false;
        door.lastMoveStepMs = 0;
        door.motionStartMs = 0;
        return;
    }

    if (absDelta <= step && (softTail > perDoorDelay))
    {
        if (door.lastMoveStepMs != 0)
        {
            const unsigned long elapsed = now - door.lastMoveStepMs;
            if (elapsed < softTail)
            {
                return;
            }
        }
    }

    door.currentAngle += direction * step;
    applyPhysicalServoAngle(doorIndex, door.currentAngle);
    door.lastMoveStepMs = now;

    if (door.currentAngle == door.targetAngle)
    {
        door.moving = false;
        door.lastMoveStepMs = 0;
        door.motionStartMs = 0;
    }
}

void applyPhysicalServoAngle(uint8_t doorIndex, int logicalAngle)
{
    DoorController &door = doors[doorIndex];
    const int constrained = constrain(logicalAngle, MIN_LOGICAL_ANGLE, MAX_LOGICAL_ANGLE);
    long effective = constrained;
    if (doorInverted[doorIndex])
    {
        effective = static_cast<long>(MIN_LOGICAL_ANGLE) + static_cast<long>(MAX_LOGICAL_ANGLE) - constrained;
    }

    const long logicalRange = static_cast<long>(MAX_LOGICAL_ANGLE) - MIN_LOGICAL_ANGLE;
    double pulse = SERVO_MIN_PULSE_US;
    if (logicalRange > 0)
    {
        const double offset = static_cast<double>(effective - MIN_LOGICAL_ANGLE);
        const double scale = static_cast<double>(SERVO_MAX_PULSE_US - SERVO_MIN_PULSE_US) / static_cast<double>(logicalRange);
        pulse = static_cast<double>(SERVO_MIN_PULSE_US) + offset * scale;
    }

    long boundedPulse = static_cast<long>(pulse + 0.5);
    if (boundedPulse < SERVO_MIN_PULSE_US)
    {
        boundedPulse = SERVO_MIN_PULSE_US;
    }
    else if (boundedPulse > SERVO_MAX_PULSE_US)
    {
        boundedPulse = SERVO_MAX_PULSE_US;
    }

    updateServoAttachment(doorIndex, true);
    door.servo.writeMicroseconds(static_cast<int>(boundedPulse));

    if (doorHoldPower[doorIndex] == 0)
    {
        door.detachAfterMs = millis() + 220UL;
    }
    else
    {
        door.detachAfterMs = 0;
    }

    if (SERVO_VERBOSE_LOGGING)
    {
        bool shouldLog = (SERVO_LOG_DECIMATION == 0);
        uint8_t &logCounter = servoLogDecimator[doorIndex];

        if (!shouldLog)
        {
            if (door.currentAngle == door.targetAngle || !door.moving)
            {
                shouldLog = true;
                logCounter = 0;
            }
            else
            {
                if (logCounter == 0)
                {
                    shouldLog = true;
                }
                logCounter = (logCounter + 1) % SERVO_LOG_DECIMATION;
            }
        }

        if (shouldLog)
        {
            Serial.print(F("LOG|"));
            Serial.print(millis());
            Serial.print(F("|SERVO|"));
            Serial.print(DOOR_LABELS[doorIndex]);
            Serial.print(F("|L="));
            Serial.print(logicalAngle);
            Serial.print(F(",E="));
            Serial.print(effective);
            Serial.print(F(",PULSE="));
            Serial.print(pulse);
            Serial.print(F(",O_PWR="));
            Serial.print(doorOpenPower[doorIndex]);
            Serial.print(F(",C_PWR="));
            Serial.print(doorClosePower[doorIndex]);
            Serial.print(F(",HOLD="));
            Serial.print(doorHoldPower[doorIndex]);
            Serial.println();
        }
    }
}

void updateServoAttachment(uint8_t doorIndex, bool forceAttach)
{
    if (doorIndex >= SERVO_COUNT)
    {
        return;
    }

    DoorController &door = doors[doorIndex];
    if (forceAttach)
    {
        if (!door.servoAttached)
        {
            door.servo.attach(SERVO_PINS[doorIndex], SERVO_MIN_PULSE_US, SERVO_MAX_PULSE_US);
            door.servoAttached = true;
            logEvent("INFO", doorIndex, "SERVO_ATTACHED");
        }
        return;
    }

    if (!door.servoAttached)
    {
        return;
    }

    if (doorHoldPower[doorIndex] == 0 && !door.moving)
    {
        if (door.detachAfterMs == 0)
        {
            door.detachAfterMs = millis() + 180UL;
        }
    }
}

void applyHoldPolicy(uint8_t doorIndex)
{
    if (doorIndex >= SERVO_COUNT)
    {
        return;
    }

    DoorController &door = doors[doorIndex];
    const unsigned long now = millis();

    if (door.moving)
    {
        door.detachAfterMs = (doorHoldPower[doorIndex] == 0) ? now + 220UL : 0;
        if (!door.servoAttached)
        {
            updateServoAttachment(doorIndex, true);
        }
        return;
    }

    if (doorHoldPower[doorIndex] == 0)
    {
        if (door.servoAttached)
        {
            if (door.detachAfterMs != 0 && now >= door.detachAfterMs)
            {
                door.servo.detach();
                door.servoAttached = false;
                door.detachAfterMs = 0;
                logEvent("INFO", doorIndex, "SERVO_DETACHED");
            }
            else if (door.detachAfterMs == 0)
            {
                door.detachAfterMs = now + 220UL;
            }
        }
    }
    else
    {
        if (!door.servoAttached)
        {
            updateServoAttachment(doorIndex, true);
            logEvent("INFO", doorIndex, "SERVO_HOLD_REATTACH");
        }
        door.detachAfterMs = 0;
    }
}

void applyProfileToDoor(uint8_t doorIndex, uint8_t profileId)
{
    if (doorIndex >= SERVO_COUNT)
    {
        return;
    }

    if (profileId >= PROFILE_COUNT)
    {
        profileId = PROFILE_CUSTOM;
    }

    if (profileId == PROFILE_CUSTOM)
    {
        if (doorProfileId[doorIndex] != PROFILE_CUSTOM)
        {
            doorProfileId[doorIndex] = PROFILE_CUSTOM;
            doors[doorIndex].statusDirty = true;
            logEvent("INFO", doorIndex, "PROFILE_CUSTOM");
        }
        return;
    }

    const DoorProfilePreset &preset = PROFILE_PRESETS[profileId];
    doorOpenDelay[doorIndex] = preset.openDelay;
    doorOpenStep[doorIndex] = preset.openStep;
    doorCloseDelay[doorIndex] = preset.closeDelay;
    doorCloseStep[doorIndex] = preset.closeStep;
    doorOpenPower[doorIndex] = preset.openPower;
    doorClosePower[doorIndex] = preset.closePower;
    doorSoftStart[doorIndex] = preset.softStart;
    doorSoftStop[doorIndex] = preset.softStop;
    doorHoldPower[doorIndex] = preset.holdPower;
    doorProfileId[doorIndex] = profileId;
    doors[doorIndex].statusDirty = true;
    logEvent("INFO", doorIndex, "PROFILE_APPLIED");
    applyHoldPolicy(doorIndex);
}

const char *getProfileName(uint8_t profileId)
{
    if (profileId >= PROFILE_COUNT)
    {
        profileId = PROFILE_CUSTOM;
    }
    return PROFILE_PRESETS[profileId].name;
}

void markProfileCustom(uint8_t doorIndex)
{
    if (doorIndex >= SERVO_COUNT)
    {
        return;
    }
    if (doorProfileId[doorIndex] != PROFILE_CUSTOM)
    {
        doorProfileId[doorIndex] = PROFILE_CUSTOM;
        logEvent("INFO", doorIndex, "PROFILE_CUSTOM");
        doors[doorIndex].statusDirty = true;
    }
}

void logEvent(const char *level, uint8_t doorIndex, const char *message)
{
    Serial.print(F("LOG|"));
    Serial.print(millis());
    Serial.print('|');
    Serial.print(level);
    Serial.print('|');
    if (doorIndex < SERVO_COUNT)
    {
        Serial.print(DOOR_LABELS[doorIndex]);
    }
    else
    {
        Serial.print(F("SYS"));
    }
    Serial.print('|');
    Serial.println(message);
}

void logEventValue(const char *level, uint8_t doorIndex, const char *label, long value)
{
    Serial.print(F("LOG|"));
    Serial.print(millis());
    Serial.print('|');
    Serial.print(level);
    Serial.print('|');
    if (doorIndex < SERVO_COUNT)
    {
        Serial.print(DOOR_LABELS[doorIndex]);
    }
    else
    {
        Serial.print(F("SYS"));
    }
    Serial.print('|');
    Serial.print(label);
    Serial.print('=');
    Serial.println(value);
}

int16_t eepromReadInt16(int addr)
{
    const uint8_t low = EEPROM.read(addr);
    const uint8_t high = EEPROM.read(addr + 1);
    return static_cast<int16_t>((static_cast<int16_t>(high) << 8) | low);
}

void eepromWriteInt16(int addr, int16_t value)
{
    const uint8_t low = static_cast<uint8_t>(value & 0xFF);
    const uint8_t high = static_cast<uint8_t>((value >> 8) & 0xFF);
    EEPROM.update(addr, low);
    EEPROM.update(addr + 1, high);
}

const char *getAutoCalPhaseName(uint8_t phase)
{
    switch (phase)
    {
    case AC_IDLE:
        return "IDLE";
    case AC_PREPARE_CLOSE:
        return "PREPARE_CLOSE";
    case AC_WAIT_SENSOR_READY:
        return "WAIT_SENSOR";
    case AC_OPEN_SWEEP:
        return "OPEN_SWEEP";
    case AC_WAIT_DEVICE_RETURN:
        return "WAIT_RETURN";
    case AC_CLOSE_SWEEP:
        return "CLOSE_SWEEP";
    case AC_VALIDATE:
        return "VALIDATE";
    case AC_COMPLETE:
        return "COMPLETE";
    case AC_ABORTED:
        return "ABORTED";
    default:
        return "UNKNOWN";
    }
}

void loadCalibrationFromEEPROM()
{
    const uint8_t magic = EEPROM.read(EEPROM_MAGIC_ADDR);
    if (magic == EEPROM_MAGIC_VALUE)
    {
        for (uint8_t i = 0; i < SERVO_COUNT; ++i)
        {
            const int base = EEPROM_DATA_ADDR + i * EEPROM_DOOR_STRIDE;
            const int16_t closeVal = eepromReadInt16(base + EEPROM_CLOSE_OFFSET);
            const int16_t openVal = eepromReadInt16(base + EEPROM_OPEN_OFFSET);
            const uint8_t invertVal = EEPROM.read(base + EEPROM_INVERT_OFFSET);
            const uint8_t oDelay = EEPROM.read(base + EEPROM_O_DELAY_OFFSET);
            const uint8_t oStep = EEPROM.read(base + EEPROM_O_STEP_OFFSET);
            const uint8_t cDelay = EEPROM.read(base + EEPROM_C_DELAY_OFFSET);
            const uint8_t cStep = EEPROM.read(base + EEPROM_C_STEP_OFFSET);
            const uint8_t oPower = EEPROM.read(base + EEPROM_O_POWER_OFFSET);
            const uint8_t cPower = EEPROM.read(base + EEPROM_C_POWER_OFFSET);
            const uint8_t softStart = EEPROM.read(base + EEPROM_SOFT_START_OFFSET);
            const uint8_t softStop = EEPROM.read(base + EEPROM_SOFT_STOP_OFFSET);
            const uint8_t hold = EEPROM.read(base + EEPROM_HOLD_POWER_OFFSET);
            const uint8_t profile = EEPROM.read(base + EEPROM_PROFILE_OFFSET);

            doorCloseAngles[i] = (closeVal >= MIN_LOGICAL_ANGLE && closeVal <= MAX_LOGICAL_ANGLE) ? static_cast<int>(closeVal) : DEFAULT_CLOSE_ANGLES[i];
            doorOpenAngles[i] = (openVal >= MIN_LOGICAL_ANGLE && openVal <= MAX_LOGICAL_ANGLE) ? static_cast<int>(openVal) : DEFAULT_OPEN_ANGLES[i];
            doorInverted[i] = invertVal > 0 ? true : false;
            doorOpenDelay[i] = oDelay ? oDelay : DEFAULT_OPEN_DELAY;
            doorOpenStep[i] = oStep ? oStep : DEFAULT_OPEN_STEP;
            doorCloseDelay[i] = cDelay ? cDelay : DEFAULT_CLOSE_DELAY;
            doorCloseStep[i] = cStep ? cStep : DEFAULT_CLOSE_STEP;
            doorOpenPower[i] = oPower ? oPower : DEFAULT_OPEN_POWER;
            doorClosePower[i] = cPower ? cPower : DEFAULT_CLOSE_POWER;
            doorSoftStart[i] = (softStart <= 200) ? softStart : DEFAULT_SOFT_START;
            doorSoftStop[i] = (softStop <= 200) ? softStop : DEFAULT_SOFT_STOP;
            doorHoldPower[i] = (hold <= 100) ? hold : DEFAULT_HOLD_POWER;
            doorProfileId[i] = (profile < PROFILE_COUNT) ? profile : PROFILE_CUSTOM;
        }

        Serial.println(F("EEPROM: calibration loaded"));
        return;
    }

    if (magic == EEPROM_MAGIC_VALUE_V1)
    {
        const uint8_t LEGACY_DOOR_STRIDE = 13;
        const uint8_t LEGACY_CLOSE_OFFSET = 0;
        const uint8_t LEGACY_OPEN_OFFSET = 1;
        const uint8_t LEGACY_INVERT_OFFSET = 2;
        const uint8_t LEGACY_O_DELAY_OFFSET = 3;
        const uint8_t LEGACY_O_STEP_OFFSET = 4;
        const uint8_t LEGACY_C_DELAY_OFFSET = 5;
        const uint8_t LEGACY_C_STEP_OFFSET = 6;
        const uint8_t LEGACY_O_POWER_OFFSET = 7;
        const uint8_t LEGACY_C_POWER_OFFSET = 8;
        const uint8_t LEGACY_SOFT_START_OFFSET = 9;
        const uint8_t LEGACY_SOFT_STOP_OFFSET = 10;
        const uint8_t LEGACY_HOLD_POWER_OFFSET = 11;
        const uint8_t LEGACY_PROFILE_OFFSET = 12;

        for (uint8_t i = 0; i < SERVO_COUNT; ++i)
        {
            const int base = EEPROM_DATA_ADDR + i * LEGACY_DOOR_STRIDE;
            const uint8_t closeVal = EEPROM.read(base + LEGACY_CLOSE_OFFSET);
            const uint8_t openVal = EEPROM.read(base + LEGACY_OPEN_OFFSET);
            const uint8_t invertVal = EEPROM.read(base + LEGACY_INVERT_OFFSET);
            const uint8_t oDelay = EEPROM.read(base + LEGACY_O_DELAY_OFFSET);
            const uint8_t oStep = EEPROM.read(base + LEGACY_O_STEP_OFFSET);
            const uint8_t cDelay = EEPROM.read(base + LEGACY_C_DELAY_OFFSET);
            const uint8_t cStep = EEPROM.read(base + LEGACY_C_STEP_OFFSET);
            const uint8_t oPower = EEPROM.read(base + LEGACY_O_POWER_OFFSET);
            const uint8_t cPower = EEPROM.read(base + LEGACY_C_POWER_OFFSET);
            const uint8_t softStart = EEPROM.read(base + LEGACY_SOFT_START_OFFSET);
            const uint8_t softStop = EEPROM.read(base + LEGACY_SOFT_STOP_OFFSET);
            const uint8_t hold = EEPROM.read(base + LEGACY_HOLD_POWER_OFFSET);
            const uint8_t profile = EEPROM.read(base + LEGACY_PROFILE_OFFSET);

            doorCloseAngles[i] = (closeVal <= LEGACY_MAX_LOGICAL_ANGLE) ? closeVal : DEFAULT_CLOSE_ANGLES[i];
            doorOpenAngles[i] = (openVal <= LEGACY_MAX_LOGICAL_ANGLE) ? openVal : DEFAULT_OPEN_ANGLES[i];
            doorInverted[i] = invertVal > 0 ? true : false;
            doorOpenDelay[i] = oDelay ? oDelay : DEFAULT_OPEN_DELAY;
            doorOpenStep[i] = oStep ? oStep : DEFAULT_OPEN_STEP;
            doorCloseDelay[i] = cDelay ? cDelay : DEFAULT_CLOSE_DELAY;
            doorCloseStep[i] = cStep ? cStep : DEFAULT_CLOSE_STEP;
            doorOpenPower[i] = oPower ? oPower : DEFAULT_OPEN_POWER;
            doorClosePower[i] = cPower ? cPower : DEFAULT_CLOSE_POWER;
            doorSoftStart[i] = (softStart <= 200) ? softStart : DEFAULT_SOFT_START;
            doorSoftStop[i] = (softStop <= 200) ? softStop : DEFAULT_SOFT_STOP;
            doorHoldPower[i] = (hold <= 100) ? hold : DEFAULT_HOLD_POWER;
            doorProfileId[i] = (profile < PROFILE_COUNT) ? profile : PROFILE_CUSTOM;
        }

        Serial.println(F("EEPROM: legacy calibration migrated"));
        saveCalibrationToEEPROM();
        return;
    }

    resetCalibrationToDefaults();
    Serial.println(F("EEPROM: defaults in use"));
}

void saveCalibrationToEEPROM()
{
    EEPROM.update(EEPROM_MAGIC_ADDR, EEPROM_MAGIC_VALUE);

    for (uint8_t i = 0; i < SERVO_COUNT; ++i)
    {
        const int16_t closeVal = static_cast<int16_t>(constrain(doorCloseAngles[i], MIN_LOGICAL_ANGLE, MAX_LOGICAL_ANGLE));
        const int16_t openVal = static_cast<int16_t>(constrain(doorOpenAngles[i], MIN_LOGICAL_ANGLE, MAX_LOGICAL_ANGLE));
        const uint8_t invertVal = doorInverted[i] ? 1 : 0;
        const uint8_t oDelay = doorOpenDelay[i];
        const uint8_t oStep = doorOpenStep[i];
        const uint8_t cDelay = doorCloseDelay[i];
        const uint8_t cStep = doorCloseStep[i];
        const uint8_t oPower = doorOpenPower[i];
        const uint8_t cPower = doorClosePower[i];
        const uint8_t softStart = doorSoftStart[i];
        const uint8_t softStop = doorSoftStop[i];
        const uint8_t hold = doorHoldPower[i];
        const uint8_t profile = (doorProfileId[i] < PROFILE_COUNT) ? doorProfileId[i] : PROFILE_CUSTOM;
        const int base = EEPROM_DATA_ADDR + i * EEPROM_DOOR_STRIDE;
        eepromWriteInt16(base + EEPROM_CLOSE_OFFSET, closeVal);
        eepromWriteInt16(base + EEPROM_OPEN_OFFSET, openVal);
        EEPROM.update(base + EEPROM_INVERT_OFFSET, invertVal);
        EEPROM.update(base + EEPROM_O_DELAY_OFFSET, oDelay);
        EEPROM.update(base + EEPROM_O_STEP_OFFSET, oStep);
        EEPROM.update(base + EEPROM_C_DELAY_OFFSET, cDelay);
        EEPROM.update(base + EEPROM_C_STEP_OFFSET, cStep);
        EEPROM.update(base + EEPROM_O_POWER_OFFSET, oPower);
        EEPROM.update(base + EEPROM_C_POWER_OFFSET, cPower);
        EEPROM.update(base + EEPROM_SOFT_START_OFFSET, softStart);
        EEPROM.update(base + EEPROM_SOFT_STOP_OFFSET, softStop);
        EEPROM.update(base + EEPROM_HOLD_POWER_OFFSET, hold);
        EEPROM.update(base + EEPROM_PROFILE_OFFSET, profile);
    }

    Serial.println(F("EEPROM: calibration saved"));
}

void resetCalibrationToDefaults()
{
    for (uint8_t i = 0; i < SERVO_COUNT; ++i)
    {
        doorCloseAngles[i] = DEFAULT_CLOSE_ANGLES[i];
        doorOpenAngles[i] = DEFAULT_OPEN_ANGLES[i];
        doorInverted[i] = DEFAULT_INVERTED[i];
        // reset new parameters to defaults
        doorOpenDelay[i] = DEFAULT_OPEN_DELAY;
        doorOpenStep[i] = DEFAULT_OPEN_STEP;
        doorCloseDelay[i] = DEFAULT_CLOSE_DELAY;
        doorCloseStep[i] = DEFAULT_CLOSE_STEP;
        doorOpenPower[i] = DEFAULT_OPEN_POWER;
        doorClosePower[i] = DEFAULT_CLOSE_POWER;
        doorSoftStart[i] = DEFAULT_SOFT_START;
        doorSoftStop[i] = DEFAULT_SOFT_STOP;
        doorHoldPower[i] = DEFAULT_HOLD_POWER;
        doorProfileId[i] = PROFILE_CUSTOM;
        doorLatchedOpen[i] = false;
    }
}

void setAutoCalResultReason(uint8_t doorIndex, const char *reason)
{
    if (doorIndex >= SERVO_COUNT)
    {
        return;
    }

    DoorController &door = doors[doorIndex];
    if (!reason || !*reason)
    {
        door.autoCal.lastResultReason[0] = '\0';
        return;
    }

    strncpy(door.autoCal.lastResultReason, reason, sizeof(door.autoCal.lastResultReason) - 1);
    door.autoCal.lastResultReason[sizeof(door.autoCal.lastResultReason) - 1] = '\0';
}

void startAutoCalibration(uint8_t doorIndex, bool fastMode)
{
    if (doorIndex >= SERVO_COUNT)
    {
        Serial.println(F("ERR: invalid door index"));
        return;
    }

    DoorController &door = doors[doorIndex];
    if (door.autoCal.active)
    {
        Serial.print(F("AUTOCAL: already running -> "));
        Serial.println(DOOR_LABELS[doorIndex]);
        return;
    }

    if (door.state != DOOR_CLOSED)
    {
        Serial.print(F("ERR: door not closed -> "));
        Serial.println(DOOR_LABELS[doorIndex]);
        return;
    }

    if (calibrationActive && calibrationDoor != doorIndex)
    {
        Serial.println(F("ERR: other calibration running"));
        return;
    }

    calibrationActive = true;
    calibrationDoor = doorIndex;

    const unsigned long now = millis();
    door.state = DOOR_CALIBRATING;
    door.statusDirty = true;
    door.autoCal.active = true;
    door.autoCal.fastMode = fastMode;
    door.autoCal.phase = AC_PREPARE_CLOSE;
    door.autoCal.phaseStartMs = now;
    door.autoCal.runStartMs = now;
    door.autoCal.detectedOpenAngle = -1;
    door.autoCal.detectedCloseAngle = -1;
    door.autoCal.sensorEdgeCaptured = false;
    door.autoCal.sensorTimeoutAtMs = now + AUTOCAL_MOVE_TIMEOUT;
    door.autoCal.lastSensorState = door.deviceInside;
    door.autoCal.lastResultDurationMs = 0;
    door.autoCal.lastResultMs = 0;
    door.autoCal.lastResultOpen = -1;
    door.autoCal.lastResultClose = -1;
    door.autoCal.lastResultFast = fastMode;
    door.autoCal.lastResultSuccess = false;
    setAutoCalResultReason(doorIndex, "");

    logEvent("INFO", doorIndex, fastMode ? "AUTOCAL_START_FAST" : "AUTOCAL_START");
    Serial.print(F("AUTOCAL: starting -> "));
    Serial.print(DOOR_LABELS[doorIndex]);
    Serial.println(fastMode ? F(" (FAST)") : F(""));

    updateServoAttachment(doorIndex, true);
    door.detachAfterMs = 0;
    setDoorTargetAngle(doorIndex, doorCloseAngles[doorIndex]);
    doorLatchedOpen[doorIndex] = false;
}

void abortAutoCalibration(uint8_t doorIndex, const char *reason)
{
    if (doorIndex >= SERVO_COUNT)
    {
        return;
    }

    DoorController &door = doors[doorIndex];
    const bool wasActive = door.autoCal.active;
    const bool wasFast = door.autoCal.fastMode;
    const unsigned long finishedMs = millis();

    door.autoCal.active = false;
    door.autoCal.phase = AC_ABORTED;
    door.autoCal.sensorTimeoutAtMs = 0;
    door.autoCal.sensorEdgeCaptured = false;
    door.autoCal.fastMode = false;
    door.autoCal.lastResultFast = wasFast;
    door.autoCal.lastResultSuccess = false;
    door.autoCal.lastResultOpen = -1;
    door.autoCal.lastResultClose = -1;
    door.autoCal.lastResultMs = finishedMs;
    door.autoCal.lastResultDurationMs = (door.autoCal.runStartMs > 0 && finishedMs >= door.autoCal.runStartMs)
                                            ? (finishedMs - door.autoCal.runStartMs)
                                            : 0;
    door.autoCal.runStartMs = 0;
    if (reason && *reason)
    {
        setAutoCalResultReason(doorIndex, reason);
    }
    else
    {
        setAutoCalResultReason(doorIndex, "AUTOCAL_ABORTED");
    }

    door.state = DOOR_CLOSED;
    setDoorAngleImmediate(doorIndex, doorCloseAngles[doorIndex]);
    door.statusDirty = true;
    applyHoldPolicy(doorIndex);
    doorLatchedOpen[doorIndex] = false;

    if (calibrationActive && calibrationDoor == doorIndex)
    {
        calibrationActive = false;
        calibrationDoor = 255;
    }

    if (reason)
    {
        Serial.print(F("AUTOCAL: aborted -> "));
        Serial.print(DOOR_LABELS[doorIndex]);
        Serial.print(F(" cause="));
        Serial.println(reason);
        logEvent(wasActive ? "WARN" : "INFO", doorIndex, reason);
    }
    else
    {
        Serial.print(F("AUTOCAL: aborted -> "));
        Serial.println(DOOR_LABELS[doorIndex]);
        logEvent(wasActive ? "WARN" : "INFO", doorIndex, "AUTOCAL_ABORTED");
    }
}

void updateAutoCalibration(uint8_t doorIndex)
{
    if (doorIndex >= SERVO_COUNT)
    {
        return;
    }

    DoorController &door = doors[doorIndex];
    if (!door.autoCal.active)
    {
        return;
    }

    const unsigned long now = millis();
    const unsigned long sensorTimeoutBudget = door.autoCal.fastMode ? (AUTOCAL_SENSOR_TIMEOUT / 2) : AUTOCAL_SENSOR_TIMEOUT;
    const unsigned long returnTimeoutBudget = door.autoCal.fastMode ? (AUTOCAL_RETURN_TIMEOUT / 2) : AUTOCAL_RETURN_TIMEOUT;
    const unsigned long closeTimeoutBudget = door.autoCal.fastMode ? (AUTOCAL_CLOSE_TIMEOUT / 2) : AUTOCAL_CLOSE_TIMEOUT;
    const int openMargin = door.autoCal.fastMode ? max(1, AUTOCAL_OPEN_MARGIN / 2) : AUTOCAL_OPEN_MARGIN;
    const int closeMargin = door.autoCal.fastMode ? max(1, AUTOCAL_CLOSE_MARGIN / 2) : AUTOCAL_CLOSE_MARGIN;

    switch (door.autoCal.phase)
    {
    case AC_PREPARE_CLOSE:
        if (!door.moving && door.currentAngle == doorCloseAngles[doorIndex])
        {
            door.autoCal.phase = AC_WAIT_SENSOR_READY;
            door.autoCal.phaseStartMs = now;
            door.autoCal.sensorTimeoutAtMs = now + sensorTimeoutBudget;
            door.autoCal.lastSensorState = door.deviceInside;
            door.statusDirty = true;
            Serial.print(F("AUTOCAL: waiting sensor ready -> "));
            Serial.println(DOOR_LABELS[doorIndex]);
            logEvent("INFO", doorIndex, "AUTOCAL_WAIT_SENSOR");
            break;
        }

        if (now - door.autoCal.phaseStartMs > AUTOCAL_MOVE_TIMEOUT)
        {
            abortAutoCalibration(doorIndex, "PREPARE_TIMEOUT");
            return;
        }

        if (!door.moving)
        {
            setDoorTargetAngle(doorIndex, doorCloseAngles[doorIndex]);
        }
        break;

    case AC_WAIT_SENSOR_READY:
        if (door.deviceInside)
        {
            door.autoCal.phase = AC_OPEN_SWEEP;
            door.autoCal.phaseStartMs = now;
            door.autoCal.sensorTimeoutAtMs = now + sensorTimeoutBudget;
            door.autoCal.sensorEdgeCaptured = false;
            door.autoCal.detectedOpenAngle = -1;
            door.autoCal.lastSensorState = door.deviceInside;
            door.statusDirty = true;
            logEvent("INFO", doorIndex, "AUTOCAL_OPEN_SWEEP");
            Serial.print(F("AUTOCAL: opening sweep -> remove device when free ("));
            Serial.print(DOOR_LABELS[doorIndex]);
            Serial.println(')');
            setDoorTargetAngle(doorIndex, MIN_LOGICAL_ANGLE);
            break;
        }

        if (door.autoCal.sensorTimeoutAtMs != 0 && now > door.autoCal.sensorTimeoutAtMs)
        {
            abortAutoCalibration(doorIndex, "SENSOR_WAIT_TIMEOUT");
            return;
        }
        break;

    case AC_OPEN_SWEEP:
    {
        if (door.autoCal.lastSensorState != door.deviceInside)
        {
            if (!door.deviceInside)
            {
                door.autoCal.sensorEdgeCaptured = true;
                door.autoCal.detectedOpenAngle = door.currentAngle;
                logEventValue("INFO", doorIndex, "AUTOCAL_EDGE_OPEN", door.currentAngle);
                const int recommended = constrain(door.currentAngle - openMargin, MIN_LOGICAL_ANGLE, doorCloseAngles[doorIndex] - 5);
                door.autoCal.detectedOpenAngle = recommended;
                setDoorTargetAngle(doorIndex, recommended);
                door.autoCal.phase = AC_WAIT_DEVICE_RETURN;
                door.autoCal.phaseStartMs = now;
                door.autoCal.sensorTimeoutAtMs = now + returnTimeoutBudget;
                door.autoCal.lastSensorState = door.deviceInside;
                door.statusDirty = true;
                Serial.print(F("AUTOCAL: waiting device return -> "));
                Serial.println(DOOR_LABELS[doorIndex]);
                logEvent("INFO", doorIndex, "AUTOCAL_WAIT_RETURN");
                return;
            }
            door.autoCal.lastSensorState = door.deviceInside;
        }

        if (door.autoCal.sensorTimeoutAtMs != 0 && now > door.autoCal.sensorTimeoutAtMs)
        {
            abortAutoCalibration(doorIndex, "OPEN_SWEEP_TIMEOUT");
            return;
        }

        if (!door.moving)
        {
            abortAutoCalibration(doorIndex, "OPEN_EDGE_MISSING");
            return;
        }
        break;
    }

    case AC_WAIT_DEVICE_RETURN:
        if (door.autoCal.lastSensorState != door.deviceInside)
        {
            door.autoCal.lastSensorState = door.deviceInside;
            if (door.deviceInside)
            {
                door.autoCal.phase = AC_CLOSE_SWEEP;
                door.autoCal.phaseStartMs = now;
                door.autoCal.sensorTimeoutAtMs = now + closeTimeoutBudget;
                door.autoCal.sensorEdgeCaptured = false;
                door.statusDirty = true;
                logEvent("INFO", doorIndex, "AUTOCAL_CLOSE_SWEEP");
                Serial.print(F("AUTOCAL: closing sweep -> hold device steady ("));
                Serial.print(DOOR_LABELS[doorIndex]);
                Serial.println(')');
                setDoorTargetAngle(doorIndex, MAX_LOGICAL_ANGLE);
                break;
            }
        }

        if (door.autoCal.sensorTimeoutAtMs != 0 && now > door.autoCal.sensorTimeoutAtMs)
        {
            abortAutoCalibration(doorIndex, "DEVICE_RETURN_TIMEOUT");
            return;
        }
        break;

    case AC_CLOSE_SWEEP:
        if (door.autoCal.lastSensorState != door.deviceInside)
        {
            door.autoCal.lastSensorState = door.deviceInside;
            if (door.deviceInside)
            {
                door.autoCal.sensorEdgeCaptured = true;
                door.autoCal.detectedCloseAngle = door.currentAngle;
                logEventValue("INFO", doorIndex, "AUTOCAL_EDGE_CLOSE", door.currentAngle);
            }
        }

        if (!door.moving)
        {
            if (door.autoCal.detectedCloseAngle < 0)
            {
                door.autoCal.detectedCloseAngle = door.currentAngle;
            }
            door.autoCal.phase = AC_VALIDATE;
            door.autoCal.phaseStartMs = now;
            door.statusDirty = true;
            break;
        }

        if (door.autoCal.sensorTimeoutAtMs != 0 && now > door.autoCal.sensorTimeoutAtMs)
        {
            abortAutoCalibration(doorIndex, "CLOSE_SWEEP_TIMEOUT");
            return;
        }
        break;

    case AC_VALIDATE:
    {
        if (door.autoCal.detectedOpenAngle < 0)
        {
            abortAutoCalibration(doorIndex, "VALIDATE_NO_OPEN");
            return;
        }

        int newOpen = door.autoCal.detectedOpenAngle;
        int newClose = (door.autoCal.detectedCloseAngle >= 0) ? door.autoCal.detectedCloseAngle : doorCloseAngles[doorIndex];

        newOpen = constrain(newOpen, MIN_LOGICAL_ANGLE, MAX_LOGICAL_ANGLE);
        newClose = constrain(newClose + closeMargin, MIN_LOGICAL_ANGLE, MAX_LOGICAL_ANGLE);
        newOpen = constrain(newOpen - openMargin, MIN_LOGICAL_ANGLE, newClose - 5);
        if (newClose <= newOpen + 5)
        {
            newClose = constrain(newOpen + 10, newOpen + 6, MAX_LOGICAL_ANGLE);
        }

        doorOpenAngles[doorIndex] = newOpen;
        doorCloseAngles[doorIndex] = newClose;
        door.state = DOOR_CLOSED;
        door.statusDirty = true;
        door.autoCal.active = false;
        door.autoCal.phase = AC_COMPLETE;
        door.autoCal.sensorTimeoutAtMs = 0;

        const bool wasFastMode = door.autoCal.fastMode;

        setDoorAngleImmediate(doorIndex, newClose);
        applyHoldPolicy(doorIndex);
        logEventValue("INFO", doorIndex, "AUTOCAL_NEW_OPEN", newOpen);
        logEventValue("INFO", doorIndex, "AUTOCAL_NEW_CLOSE", newClose);
        logEvent("INFO", doorIndex, "AUTOCAL_COMPLETE");

        door.autoCal.lastResultOpen = newOpen;
        door.autoCal.lastResultClose = newClose;
        door.autoCal.lastResultFast = wasFastMode;
        door.autoCal.lastResultSuccess = true;
        door.autoCal.lastResultMs = now;
        door.autoCal.lastResultDurationMs = (door.autoCal.runStartMs > 0 && now >= door.autoCal.runStartMs)
                                                ? (now - door.autoCal.runStartMs)
                                                : 0;
        door.autoCal.runStartMs = 0;
        setAutoCalResultReason(doorIndex, "COMPLETE");
        door.autoCal.fastMode = false;

        Serial.print(F("AUTOCAL: complete -> "));
        Serial.print(DOOR_LABELS[doorIndex]);
        Serial.print(F(" open="));
        Serial.print(newOpen);
        Serial.print(F(" close="));
        Serial.println(newClose);
        Serial.println(F("Hint: run SAVE_CALIBRATION to persist"));

        if (calibrationActive && calibrationDoor == doorIndex)
        {
            calibrationActive = false;
            calibrationDoor = 255;
        }
        break;
    }

    case AC_COMPLETE:
    case AC_ABORTED:
    case AC_IDLE:
    default:
        door.autoCal.active = false;
        if (door.autoCal.runStartMs != 0)
        {
            door.autoCal.runStartMs = 0;
        }
        break;
    }
}
