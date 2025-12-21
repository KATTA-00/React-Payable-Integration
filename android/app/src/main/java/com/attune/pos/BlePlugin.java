package com.attune.pos;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@CapacitorPlugin(
    name = "Ble",
    permissions = {
        @Permission(strings = {Manifest.permission.BLUETOOTH}, alias = "bluetooth"),
        @Permission(strings = {Manifest.permission.BLUETOOTH_ADMIN}, alias = "bluetoothAdmin"),
        @Permission(strings = {Manifest.permission.BLUETOOTH_SCAN}, alias = "bluetoothScan"),
        @Permission(strings = {Manifest.permission.BLUETOOTH_CONNECT}, alias = "bluetoothConnect"),
        @Permission(strings = {Manifest.permission.ACCESS_FINE_LOCATION}, alias = "location")
    }
)
public class BlePlugin extends Plugin {

    private static final String TAG = "BlePlugin";
    private static final long SCAN_TIMEOUT_MS = 10000; // 10 seconds default scan
    
    // Standard BLE descriptor UUID for enabling notifications
    private static final UUID CCCD_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    private BluetoothAdapter bluetoothAdapter;
    private BluetoothLeScanner bluetoothLeScanner;
    private BluetoothGatt bluetoothGatt;
    private Handler mainHandler;
    
    // Track active calls
    private PluginCall activeScanCall;
    private PluginCall activeConnectCall;
    private PluginCall activeReadCall;
    private PluginCall activeWriteCall;
    
    // Store discovered devices during scan
    private Map<String, BluetoothDevice> discoveredDevices = new HashMap<>();

    @Override
    public void load() {
        mainHandler = new Handler(Looper.getMainLooper());
        
        BluetoothManager bluetoothManager = (BluetoothManager) getContext().getSystemService(Context.BLUETOOTH_SERVICE);
        if (bluetoothManager != null) {
            bluetoothAdapter = bluetoothManager.getAdapter();
            if (bluetoothAdapter != null) {
                bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
                Log.d(TAG, "BLE Plugin loaded successfully");
            } else {
                Log.e(TAG, "BluetoothAdapter is null - device may not support Bluetooth");
            }
        } else {
            Log.e(TAG, "BluetoothManager is null");
        }
    }

    @Override
    protected void handleOnDestroy() {
        disconnect();
        super.handleOnDestroy();
    }

    // =================================================================
    // PERMISSION HANDLING
    // =================================================================

    private boolean hasRequiredBlePermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+
            return ContextCompat.checkSelfPermission(getContext(), Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
                   ContextCompat.checkSelfPermission(getContext(), Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
        } else {
            // Pre-Android 12
            return ContextCompat.checkSelfPermission(getContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        }
    }

    private void requestPermissionsIfNeeded(PluginCall call) {
        if (!hasRequiredBlePermissions()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                requestPermissionForAliases(new String[]{"bluetoothScan", "bluetoothConnect"}, call, "permissionsCallback");
            } else {
                requestPermissionForAlias("location", call, "permissionsCallback");
            }
        }
    }

    @PermissionCallback
    private void permissionsCallback(PluginCall call) {
        if (hasRequiredBlePermissions()) {
            // Retry the original method
            String methodName = call.getMethodName();
            switch (methodName) {
                case "startScan":
                    startScanInternal(call);
                    break;
                case "connect":
                    connectInternal(call);
                    break;
                default:
                    call.reject("Permission granted but unknown method: " + methodName);
            }
        } else {
            call.reject("BLE permissions denied");
        }
    }

    // =================================================================
    // PUBLIC API METHODS
    // =================================================================

    /**
     * Check if Bluetooth is enabled
     * Usage: await Ble.isEnabled()
     */
    @PluginMethod
    public void isEnabled(PluginCall call) {
        JSObject result = new JSObject();
        result.put("enabled", bluetoothAdapter != null && bluetoothAdapter.isEnabled());
        call.resolve(result);
    }

    /**
     * Start scanning for BLE devices
     * Usage: await Ble.startScan({ timeout: 10000, deviceName: "ESP32_Tablet_Link" })
     */
    @PluginMethod
    public void startScan(PluginCall call) {
        if (!hasRequiredBlePermissions()) {
            requestPermissionsIfNeeded(call);
            return;
        }
        startScanInternal(call);
    }

    private void startScanInternal(PluginCall call) {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            call.reject("Bluetooth is not enabled");
            return;
        }

        if (bluetoothLeScanner == null) {
            call.reject("BLE Scanner not available");
            return;
        }

        if (activeScanCall != null) {
            call.reject("Scan already in progress");
            return;
        }

        // Clear previous discoveries
        discoveredDevices.clear();
        activeScanCall = call;

        // Get optional parameters
        long timeout = call.getLong("timeout", SCAN_TIMEOUT_MS);
        String targetDeviceName = call.getString("deviceName");

        try {
            bluetoothLeScanner.startScan(scanCallback);
            Log.d(TAG, "BLE scan started");

            // Stop scan after timeout
            mainHandler.postDelayed(() -> {
                stopScanInternal();
                if (activeScanCall != null) {
                    JSObject result = new JSObject();
                    JSArray devicesArray = new JSArray();
                    
                    for (BluetoothDevice device : discoveredDevices.values()) {
                        JSObject deviceObj = createDeviceObject(device);
                        devicesArray.put(deviceObj);
                    }
                    
                    result.put("devices", devicesArray);
                    result.put("count", discoveredDevices.size());
                    activeScanCall.resolve(result);
                    activeScanCall = null;
                }
            }, timeout);

        } catch (SecurityException e) {
            activeScanCall = null;
            call.reject("Permission error during scan: " + e.getMessage());
        }
    }

    /**
     * Stop scanning for BLE devices
     * Usage: await Ble.stopScan()
     */
    @PluginMethod
    public void stopScan(PluginCall call) {
        stopScanInternal();
        
        JSObject result = new JSObject();
        JSArray devicesArray = new JSArray();
        
        for (BluetoothDevice device : discoveredDevices.values()) {
            JSObject deviceObj = createDeviceObject(device);
            devicesArray.put(deviceObj);
        }
        
        result.put("devices", devicesArray);
        result.put("count", discoveredDevices.size());
        call.resolve(result);
    }

    private void stopScanInternal() {
        if (bluetoothLeScanner != null) {
            try {
                bluetoothLeScanner.stopScan(scanCallback);
                Log.d(TAG, "BLE scan stopped");
            } catch (SecurityException e) {
                Log.e(TAG, "Permission error stopping scan: " + e.getMessage());
            }
        }
    }

    /**
     * Connect to a BLE device
     * Usage: await Ble.connect({ deviceAddress: "AA:BB:CC:DD:EE:FF" })
     */
    @PluginMethod
    public void connect(PluginCall call) {
        if (!hasRequiredBlePermissions()) {
            requestPermissionsIfNeeded(call);
            return;
        }
        connectInternal(call);
    }

    private void connectInternal(PluginCall call) {
        String deviceAddress = call.getString("deviceAddress");
        if (deviceAddress == null || deviceAddress.isEmpty()) {
            call.reject("Device address is required");
            return;
        }

        if (activeConnectCall != null) {
            call.reject("Connection already in progress");
            return;
        }

        if (bluetoothGatt != null) {
            call.reject("Already connected to a device. Disconnect first.");
            return;
        }

        try {
            BluetoothDevice device = bluetoothAdapter.getRemoteDevice(deviceAddress);
            activeConnectCall = call;
            
            bluetoothGatt = device.connectGatt(getContext(), false, gattCallback);
            Log.d(TAG, "Connecting to device: " + deviceAddress);
            
        } catch (SecurityException e) {
            activeConnectCall = null;
            call.reject("Permission error during connection: " + e.getMessage());
        } catch (IllegalArgumentException e) {
            activeConnectCall = null;
            call.reject("Invalid device address: " + e.getMessage());
        }
    }

    /**
     * Disconnect from the connected BLE device
     * Usage: await Ble.disconnect()
     */
    @PluginMethod
    public void disconnect(PluginCall call) {
        disconnect();
        
        JSObject result = new JSObject();
        result.put("success", true);
        call.resolve(result);
    }

    private void disconnect() {
        if (bluetoothGatt != null) {
            try {
                bluetoothGatt.disconnect();
                bluetoothGatt.close();
                bluetoothGatt = null;
                Log.d(TAG, "Disconnected from BLE device");
            } catch (SecurityException e) {
                Log.e(TAG, "Permission error during disconnect: " + e.getMessage());
            }
        }
    }

    /**
     * Read data from a characteristic
     * Usage: await Ble.read({ 
     *   serviceUuid: "4fafc201-1fb5-459e-8fcc-c5c9c331914b",
     *   characteristicUuid: "beb5483e-36e1-4688-b7f5-ea07361b26a8"
     * })
     */
    @PluginMethod
    public void read(PluginCall call) {
        if (bluetoothGatt == null) {
            call.reject("Not connected to any device");
            return;
        }

        String serviceUuid = call.getString("serviceUuid");
        String characteristicUuid = call.getString("characteristicUuid");

        if (serviceUuid == null || characteristicUuid == null) {
            call.reject("serviceUuid and characteristicUuid are required");
            return;
        }

        if (activeReadCall != null) {
            call.reject("Read operation already in progress");
            return;
        }

        try {
            BluetoothGattService service = bluetoothGatt.getService(UUID.fromString(serviceUuid));
            if (service == null) {
                call.reject("Service not found: " + serviceUuid);
                return;
            }

            BluetoothGattCharacteristic characteristic = service.getCharacteristic(UUID.fromString(characteristicUuid));
            if (characteristic == null) {
                call.reject("Characteristic not found: " + characteristicUuid);
                return;
            }

            activeReadCall = call;
            boolean success = bluetoothGatt.readCharacteristic(characteristic);
            
            if (!success) {
                activeReadCall = null;
                call.reject("Failed to initiate read operation");
            }
            
        } catch (SecurityException e) {
            activeReadCall = null;
            call.reject("Permission error during read: " + e.getMessage());
        } catch (IllegalArgumentException e) {
            activeReadCall = null;
            call.reject("Invalid UUID format: " + e.getMessage());
        }
    }

    /**
     * Write data to a characteristic
     * Usage: await Ble.write({ 
     *   serviceUuid: "4fafc201-1fb5-459e-8fcc-c5c9c331914b",
     *   characteristicUuid: "beb5483e-36e1-4688-b7f5-ea07361b26a8",
     *   value: "Cmd:LED_ON"
     * })
     */
    @PluginMethod
    public void write(PluginCall call) {
        if (bluetoothGatt == null) {
            call.reject("Not connected to any device");
            return;
        }

        String serviceUuid = call.getString("serviceUuid");
        String characteristicUuid = call.getString("characteristicUuid");
        String value = call.getString("value");

        if (serviceUuid == null || characteristicUuid == null || value == null) {
            call.reject("serviceUuid, characteristicUuid, and value are required");
            return;
        }

        if (activeWriteCall != null) {
            call.reject("Write operation already in progress");
            return;
        }

        try {
            BluetoothGattService service = bluetoothGatt.getService(UUID.fromString(serviceUuid));
            if (service == null) {
                call.reject("Service not found: " + serviceUuid);
                return;
            }

            BluetoothGattCharacteristic characteristic = service.getCharacteristic(UUID.fromString(characteristicUuid));
            if (characteristic == null) {
                call.reject("Characteristic not found: " + characteristicUuid);
                return;
            }

            activeWriteCall = call;
            characteristic.setValue(value.getBytes());
            boolean success = bluetoothGatt.writeCharacteristic(characteristic);
            
            if (!success) {
                activeWriteCall = null;
                call.reject("Failed to initiate write operation");
            }
            
        } catch (SecurityException e) {
            activeWriteCall = null;
            call.reject("Permission error during write: " + e.getMessage());
        } catch (IllegalArgumentException e) {
            activeWriteCall = null;
            call.reject("Invalid UUID format: " + e.getMessage());
        }
    }

    /**
     * Enable notifications for a characteristic
     * Usage: await Ble.enableNotifications({ 
     *   serviceUuid: "4fafc201-1fb5-459e-8fcc-c5c9c331914b",
     *   characteristicUuid: "beb5483e-36e1-4688-b7f5-ea07361b26a8"
     * })
     */
    @PluginMethod
    public void enableNotifications(PluginCall call) {
        if (bluetoothGatt == null) {
            call.reject("Not connected to any device");
            return;
        }

        String serviceUuid = call.getString("serviceUuid");
        String characteristicUuid = call.getString("characteristicUuid");

        if (serviceUuid == null || characteristicUuid == null) {
            call.reject("serviceUuid and characteristicUuid are required");
            return;
        }

        try {
            BluetoothGattService service = bluetoothGatt.getService(UUID.fromString(serviceUuid));
            if (service == null) {
                call.reject("Service not found: " + serviceUuid);
                return;
            }

            BluetoothGattCharacteristic characteristic = service.getCharacteristic(UUID.fromString(characteristicUuid));
            if (characteristic == null) {
                call.reject("Characteristic not found: " + characteristicUuid);
                return;
            }

            // Enable local notifications
            boolean success = bluetoothGatt.setCharacteristicNotification(characteristic, true);
            if (!success) {
                call.reject("Failed to enable local notification");
                return;
            }

            // Enable remote notifications by writing to CCCD descriptor
            BluetoothGattDescriptor descriptor = characteristic.getDescriptor(CCCD_UUID);
            if (descriptor == null) {
                call.reject("CCCD descriptor not found - characteristic may not support notifications");
                return;
            }

            descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            success = bluetoothGatt.writeDescriptor(descriptor);
            
            if (success) {
                JSObject result = new JSObject();
                result.put("success", true);
                result.put("message", "Notifications enabled");
                call.resolve(result);
            } else {
                call.reject("Failed to write CCCD descriptor");
            }
            
        } catch (SecurityException e) {
            call.reject("Permission error enabling notifications: " + e.getMessage());
        } catch (IllegalArgumentException e) {
            call.reject("Invalid UUID format: " + e.getMessage());
        }
    }

    /**
     * Get list of discovered services and characteristics
     * Usage: await Ble.getServices()
     */
    @PluginMethod
    public void getServices(PluginCall call) {
        if (bluetoothGatt == null) {
            call.reject("Not connected to any device");
            return;
        }

        try {
            List<BluetoothGattService> services = bluetoothGatt.getServices();
            JSArray servicesArray = new JSArray();

            for (BluetoothGattService service : services) {
                JSObject serviceObj = new JSObject();
                serviceObj.put("uuid", service.getUuid().toString());
                serviceObj.put("type", service.getType() == BluetoothGattService.SERVICE_TYPE_PRIMARY ? "primary" : "secondary");

                JSArray characteristicsArray = new JSArray();
                for (BluetoothGattCharacteristic characteristic : service.getCharacteristics()) {
                    JSObject charObj = new JSObject();
                    charObj.put("uuid", characteristic.getUuid().toString());
                    charObj.put("properties", characteristic.getProperties());
                    charObj.put("permissions", characteristic.getPermissions());
                    characteristicsArray.put(charObj);
                }
                
                serviceObj.put("characteristics", characteristicsArray);
                servicesArray.put(serviceObj);
            }

            JSObject result = new JSObject();
            result.put("services", servicesArray);
            call.resolve(result);
            
        } catch (SecurityException e) {
            call.reject("Permission error getting services: " + e.getMessage());
        }
    }

    /**
     * Request larger MTU size for bigger data packets
     * Usage: await Ble.requestMtu({ mtu: 512 })
     */
    @PluginMethod
    public void requestMtu(PluginCall call) {
        if (bluetoothGatt == null) {
            call.reject("Not connected to any device");
            return;
        }

        Integer mtu = call.getInt("mtu", 512);
        if (mtu < 23 || mtu > 517) {
            call.reject("MTU must be between 23 and 517");
            return;
        }

        try {
            boolean success = bluetoothGatt.requestMtu(mtu);
            if (success) {
                JSObject result = new JSObject();
                result.put("success", true);
                result.put("requested", mtu);
                call.resolve(result);
            } else {
                call.reject("Failed to request MTU");
            }
        } catch (SecurityException e) {
            call.reject("Permission error requesting MTU: " + e.getMessage());
        }
    }

    // =================================================================
    // SCAN CALLBACK
    // =================================================================

    private final ScanCallback scanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            try {
                BluetoothDevice device = result.getDevice();
                String address = device.getAddress();
                
                // Store unique devices
                if (!discoveredDevices.containsKey(address)) {
                    discoveredDevices.put(address, device);
                    Log.d(TAG, "Device found: " + address + " | " + device.getName());
                    
                    // Optionally notify React immediately for each device
                    notifyDeviceFound(device, result.getRssi());
                }
            } catch (SecurityException e) {
                Log.e(TAG, "Permission error in scan callback: " + e.getMessage());
            }
        }

        @Override
        public void onScanFailed(int errorCode) {
            Log.e(TAG, "BLE scan failed with error code: " + errorCode);
            if (activeScanCall != null) {
                activeScanCall.reject("Scan failed with error code: " + errorCode);
                activeScanCall = null;
            }
        }
    };

    // =================================================================
    // GATT CALLBACK
    // =================================================================

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            try {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    Log.d(TAG, "Connected to GATT server");
                    gatt.discoverServices();
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    Log.d(TAG, "Disconnected from GATT server");
                    
                    if (activeConnectCall != null) {
                        activeConnectCall.reject("Connection failed or disconnected");
                        activeConnectCall = null;
                    }
                    
                    // Notify React about disconnection
                    notifyDisconnected();
                }
            } catch (SecurityException e) {
                Log.e(TAG, "Permission error in connection state change: " + e.getMessage());
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "Services discovered successfully");
                
                if (activeConnectCall != null) {
                    try {
                        JSObject result = new JSObject();
                        result.put("success", true);
                        result.put("deviceAddress", gatt.getDevice().getAddress());
                        result.put("deviceName", gatt.getDevice().getName());
                        result.put("servicesCount", gatt.getServices().size());
                        
                        activeConnectCall.resolve(result);
                        activeConnectCall = null;
                    } catch (SecurityException e) {
                        activeConnectCall.reject("Permission error: " + e.getMessage());
                        activeConnectCall = null;
                    }
                }
            } else {
                Log.e(TAG, "Service discovery failed with status: " + status);
                if (activeConnectCall != null) {
                    activeConnectCall.reject("Service discovery failed");
                    activeConnectCall = null;
                }
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                byte[] data = characteristic.getValue();
                String value = new String(data);
                
                Log.d(TAG, "Characteristic read: " + value);
                
                if (activeReadCall != null) {
                    JSObject result = new JSObject();
                    result.put("value", value);
                    result.put("characteristicUuid", characteristic.getUuid().toString());
                    activeReadCall.resolve(result);
                    activeReadCall = null;
                }
            } else {
                Log.e(TAG, "Characteristic read failed with status: " + status);
                if (activeReadCall != null) {
                    activeReadCall.reject("Read failed with status: " + status);
                    activeReadCall = null;
                }
            }
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "Characteristic write successful");
                
                if (activeWriteCall != null) {
                    JSObject result = new JSObject();
                    result.put("success", true);
                    result.put("characteristicUuid", characteristic.getUuid().toString());
                    activeWriteCall.resolve(result);
                    activeWriteCall = null;
                }
            } else {
                Log.e(TAG, "Characteristic write failed with status: " + status);
                if (activeWriteCall != null) {
                    activeWriteCall.reject("Write failed with status: " + status);
                    activeWriteCall = null;
                }
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            byte[] data = characteristic.getValue();
            String value = new String(data);
            
            Log.d(TAG, "Characteristic changed (notification): " + value);
            
            // Notify React about the new data
            notifyCharacteristicChanged(characteristic.getUuid().toString(), value);
        }

        @Override
        public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "MTU changed to: " + mtu);
            } else {
                Log.e(TAG, "MTU change failed with status: " + status);
            }
        }
    };

    // =================================================================
    // HELPER METHODS
    // =================================================================

    private JSObject createDeviceObject(BluetoothDevice device) {
        JSObject deviceObj = new JSObject();
        try {
            deviceObj.put("address", device.getAddress());
            deviceObj.put("name", device.getName() != null ? device.getName() : "Unknown");
        } catch (SecurityException e) {
            Log.e(TAG, "Permission error creating device object: " + e.getMessage());
        }
        return deviceObj;
    }

    // Event notification methods to send data to React
    
    private void notifyDeviceFound(BluetoothDevice device, int rssi) {
        JSObject data = new JSObject();
        try {
            data.put("address", device.getAddress());
            data.put("name", device.getName() != null ? device.getName() : "Unknown");
            data.put("rssi", rssi);
            notifyListeners("deviceFound", data);
        } catch (SecurityException e) {
            Log.e(TAG, "Permission error notifying device found: " + e.getMessage());
        }
    }

    private void notifyDisconnected() {
        JSObject data = new JSObject();
        data.put("event", "disconnected");
        notifyListeners("deviceDisconnected", data);
    }

    private void notifyCharacteristicChanged(String characteristicUuid, String value) {
        JSObject data = new JSObject();
        data.put("characteristicUuid", characteristicUuid);
        data.put("value", value);
        notifyListeners("characteristicChanged", data);
    }
}
