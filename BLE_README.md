# BLE Communication Setup for ESP32 Integration

This project includes a complete BLE (Bluetooth Low Energy) implementation for communicating between your Android tablet and an ESP32 device.

## 📁 Files Added

### Android (Java)
- **`BlePlugin.java`** - Native Android BLE plugin for Capacitor
  - Location: `android/app/src/main/java/com/attune/pos/BlePlugin.java`
  - Features: Scanning, connecting, reading, writing, notifications

### React (JavaScript)
- **`bleService.js`** - JavaScript service wrapper for BLE operations
  - Location: `src/bleService.js`
  - Provides simplified API and event handling
  
- **`BleExample.js`** - Example React component demonstrating BLE usage
  - Location: `src/BleExample.js`
  - Complete UI for testing BLE functionality
  
- **`BleExample.css`** - Styling for the example component
  - Location: `src/BleExample.css`

### Configuration Files
- **`AndroidManifest.xml`** - Updated with BLE permissions
  - Location: `android/app/src/main/AndroidManifest.xml`
  
- **`MainActivity.java`** - Updated to register BlePlugin
  - Location: `android/app/src/main/java/com/attune/pos/MainActivity.java`

---

## 🔧 ESP32 Setup

### Arduino Code for ESP32

Upload this code to your ESP32 using Arduino IDE:

```cpp
#include <BLEDevice.h>
#include <BLEUtils.h>
#include <BLEServer.h>

// UUIDs - Must match the ones in bleService.js
#define SERVICE_UUID        "4fafc201-1fb5-459e-8fcc-c5c9c331914b"
#define CHARACTERISTIC_UUID "beb5483e-36e1-4688-b7f5-ea07361b26a8"

BLECharacteristic *pCharacteristic;
bool deviceConnected = false;

class MyServerCallbacks: public BLEServerCallbacks {
    void onConnect(BLEServer* pServer) {
      deviceConnected = true;
      Serial.println("Device Connected");
    };

    void onDisconnect(BLEServer* pServer) {
      deviceConnected = false;
      Serial.println("Device Disconnected");
      // Restart advertising
      BLEDevice::startAdvertising();
    }
};

class MyCallbacks: public BLECharacteristicCallbacks {
    void onWrite(BLECharacteristic *pCharacteristic) {
      std::string value = pCharacteristic->getValue();

      if (value.length() > 0) {
        Serial.print("Received command: ");
        for (int i = 0; i < value.length(); i++)
          Serial.print(value[i]);
        Serial.println();

        // Handle commands
        String cmd = String(value.c_str());
        if (cmd.startsWith("Cmd:LED_ON")) {
          digitalWrite(LED_BUILTIN, HIGH);
          pCharacteristic->setValue("LED is ON");
          pCharacteristic->notify();
        } 
        else if (cmd.startsWith("Cmd:LED_OFF")) {
          digitalWrite(LED_BUILTIN, LOW);
          pCharacteristic->setValue("LED is OFF");
          pCharacteristic->notify();
        }
        else if (cmd.startsWith("Cmd:STATUS")) {
          String status = "ESP32 Status: OK";
          pCharacteristic->setValue(status.c_str());
          pCharacteristic->notify();
        }
      }
    }
};

void setup() {
  Serial.begin(115200);
  pinMode(LED_BUILTIN, OUTPUT);
  
  Serial.println("Starting BLE Server...");

  // Initialize BLE
  BLEDevice::init("ESP32_Tablet_Link");
  BLEServer *pServer = BLEDevice::createServer();
  pServer->setCallbacks(new MyServerCallbacks());

  // Create BLE Service
  BLEService *pService = pServer->createService(SERVICE_UUID);

  // Create BLE Characteristic
  pCharacteristic = pService->createCharacteristic(
                      CHARACTERISTIC_UUID,
                      BLECharacteristic::PROPERTY_READ |
                      BLECharacteristic::PROPERTY_WRITE |
                      BLECharacteristic::PROPERTY_NOTIFY
                    );

  pCharacteristic->setCallbacks(new MyCallbacks());
  pCharacteristic->setValue("Hello from ESP32");

  // Start the service
  pService->start();

  // Start advertising
  BLEAdvertising *pAdvertising = BLEDevice::getAdvertising();
  pAdvertising->addServiceUUID(SERVICE_UUID);
  pAdvertising->setScanResponse(true);
  pAdvertising->setMinPreferred(0x06);  // functions that help with iPhone connections
  pAdvertising->setMinPreferred(0x12);
  BLEDevice::startAdvertising();
  
  Serial.println("BLE Server is ready!");
  Serial.println("Waiting for a client connection...");
}

void loop() {
  // Send periodic updates if connected
  if (deviceConnected) {
    // Example: Send sensor data every 5 seconds
    static unsigned long lastUpdate = 0;
    if (millis() - lastUpdate > 5000) {
      String data = "Time: " + String(millis()/1000) + "s";
      pCharacteristic->setValue(data.c_str());
      pCharacteristic->notify();
      lastUpdate = millis();
    }
  }
  delay(100);
}
```

---

## 🚀 Quick Start Guide

### Step 1: Rebuild Your Android App

```bash
# Build the React app
npm run build

# Sync with Android
npx cap sync android

# Open in Android Studio
npx cap open android
```

### Step 2: Test the BLE Integration

#### Option A: Using the Example Component

Add the BLE example to your `App.js`:

```javascript
import BleExample from './BleExample';

function App() {
  return (
    <div className="App">
      <BleExample />
    </div>
  );
}
```

#### Option B: Using the Service Directly

```javascript
import { bleService } from './bleService';

// Quick connect to ESP32
const connectToESP32 = async () => {
  try {
    const device = await bleService.quickConnectESP32();
    console.log('Connected:', device);
  } catch (error) {
    console.error('Connection failed:', error);
  }
};

// Send a command
const sendCommand = async () => {
  try {
    await bleService.sendCommand('LED_ON');
    console.log('Command sent');
  } catch (error) {
    console.error('Send failed:', error);
  }
};

// Listen for notifications
bleService.on('dataReceived', (data) => {
  console.log('Received from ESP32:', data.value);
});
```

---

## 📱 Available API Methods

### BleService Methods

```javascript
// Check if Bluetooth is enabled
await bleService.isBluetoothEnabled()

// Scan for devices
await bleService.scanForDevices(timeout, deviceName)
await bleService.scanForESP32(timeout)
await bleService.stopScan()

// Connection
await bleService.connect(deviceAddress)
await bleService.disconnect()
await bleService.quickConnectESP32() // Scan + Connect + Enable Notifications

// Data Transfer
await bleService.readData(serviceUuid, characteristicUuid)
await bleService.writeData(value, serviceUuid, characteristicUuid)
await bleService.sendCommand(command) // Sends "Cmd:{command}"

// Notifications
await bleService.enableNotifications(serviceUuid, characteristicUuid)

// Info
await bleService.getServices()
await bleService.requestMtu(mtu)

// Status
bleService.isConnected()
bleService.getConnectedDevice()
```

### Event Listeners

```javascript
bleService.on('deviceFound', (device) => {
  console.log('Found:', device.name, device.address);
});

bleService.on('disconnected', () => {
  console.log('Device disconnected');
});

bleService.on('dataReceived', (data) => {
  console.log('Data:', data.value);
});
```

---

## 🔐 Permissions

The app will automatically request these permissions when needed:

### Android 12+ (API 31+)
- `BLUETOOTH_SCAN` - For scanning devices
- `BLUETOOTH_CONNECT` - For connecting to devices

### Android < 12 (API < 31)
- `BLUETOOTH` - Legacy Bluetooth
- `BLUETOOTH_ADMIN` - Legacy Bluetooth admin
- `ACCESS_FINE_LOCATION` - Required for BLE scanning

---

## 🎯 Common Use Cases

### Example 1: Toggle LED

```javascript
// Turn LED on
await bleService.sendCommand('LED_ON');

// Turn LED off
await bleService.sendCommand('LED_OFF');
```

### Example 2: Get Status

```javascript
// Request status
await bleService.sendCommand('STATUS');

// Listen for response
bleService.on('dataReceived', (data) => {
  console.log('Status:', data.value);
});
```

### Example 3: Read Sensor Data

```javascript
// Enable notifications for automatic updates
await bleService.enableNotifications();

// Listen for sensor data
bleService.on('dataReceived', (data) => {
  console.log('Sensor reading:', data.value);
});
```

---

## 🐛 Troubleshooting

### Device Not Found
- Ensure ESP32 is powered on and running the BLE server code
- Check that the device name matches: `ESP32_Tablet_Link`
- Verify Bluetooth is enabled on the tablet
- Try increasing scan timeout: `scanForDevices(15000)`

### Permission Denied
- Grant Bluetooth and Location permissions when prompted
- For Android 12+, ensure location services are enabled
- Check AndroidManifest.xml has correct permissions

### Connection Failed
- Ensure you're not already connected to another device
- Disconnect and retry
- Restart the ESP32
- Clear Bluetooth cache on Android (Settings → Apps → Your App → Clear Cache)

### No Data Received
- Verify UUIDs match between ESP32 and bleService.js
- Check that notifications are enabled: `enableNotifications()`
- Ensure ESP32 is calling `pCharacteristic->notify()` after setting value

### MTU Issues (Large Data)
- Request larger MTU: `await bleService.requestMtu(512)`
- Split large data into smaller chunks on ESP32 side
- Default MTU is only 23 bytes (20 bytes usable data)

---

## 🔧 Customization

### Change ESP32 Configuration

Edit `src/bleService.js`:

```javascript
const ESP32_CONFIG = {
  SERVICE_UUID: 'your-service-uuid-here',
  CHARACTERISTIC_UUID: 'your-characteristic-uuid-here',
  DEVICE_NAME: 'YourDeviceName'
};
```

### Add Custom Commands

In ESP32 code:

```cpp
else if (cmd.startsWith("Cmd:CUSTOM")) {
  // Your custom logic here
  pCharacteristic->setValue("Custom response");
  pCharacteristic->notify();
}
```

In React:

```javascript
await bleService.sendCommand('CUSTOM');
```

---

## 📊 Data Flow

```
┌─────────────┐         ┌──────────────┐         ┌─────────────┐
│   React     │ <-----> │  BleService  │ <-----> │  BlePlugin  │
│ (UI Layer)  │  JS     │   (Bridge)   │  JS     │   (Native)  │
└─────────────┘         └──────────────┘         └─────────────┘
                                                         │
                                                    Bluetooth
                                                         │
                                                         ▼
                                                  ┌─────────────┐
                                                  │    ESP32    │
                                                  │ (GATT Server)│
                                                  └─────────────┘
```

---

## 📚 Further Reading

- [ESP32 BLE Documentation](https://docs.espressif.com/projects/esp-idf/en/latest/esp32/api-reference/bluetooth/esp_gap_ble.html)
- [Android BLE Guide](https://developer.android.com/guide/topics/connectivity/bluetooth/ble-overview)
- [Capacitor Plugin Development](https://capacitorjs.com/docs/plugins)
- [GATT Specifications](https://www.bluetooth.com/specifications/gatt/)

---

## 💡 Tips

1. **Battery Optimization**: BLE is power-efficient, but continuous scanning drains battery. Stop scanning once devices are found.

2. **Connection Stability**: Keep the ESP32 within 10 meters for reliable connection. Walls and obstacles affect signal strength.

3. **Debugging**: Use `adb logcat | grep BlePlugin` to see detailed logs from the native plugin.

4. **Testing**: Test with the BleExample component first before integrating into your main app.

5. **Production**: Remove or comment out verbose console.logs in bleService.js for production builds.

---

## 📄 License

This BLE implementation is part of your React-Payable-Integration project.

---

**Ready to test!** Power on your ESP32, run the app, and tap "Quick Connect to ESP32" 🚀
