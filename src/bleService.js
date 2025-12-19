// BLE Service for ESP32 Communication
// This file provides a JavaScript interface to the native BLE plugin

import { registerPlugin } from '@capacitor/core';

// Initialize the BLE Bridge
const Ble = registerPlugin('Ble');

// ESP32 Configuration - Update these UUIDs to match your ESP32 setup
const ESP32_CONFIG = {
  SERVICE_UUID: '4fafc201-1fb5-459e-8fcc-c5c9c331914b',
  CHARACTERISTIC_UUID: 'beb5483e-36e1-4688-b7f5-ea07361b26a8',
  DEVICE_NAME: 'ESP32_Tablet_Link'
};

class BleService {
  constructor() {
    this.connectedDevice = null;
    this.isScanning = false;
    this.listeners = new Map();
    
    // Set up event listeners for notifications from native code
    Ble.addListener('deviceFound', (device) => {
      console.log('Device found:', device);
      this.emit('deviceFound', device);
    });
    
    Ble.addListener('deviceDisconnected', () => {
      console.log('Device disconnected');
      this.connectedDevice = null;
      this.emit('disconnected');
    });
    
    Ble.addListener('characteristicChanged', (data) => {
      console.log('Characteristic changed:', data);
      this.emit('dataReceived', data);
    });
  }

  // Event emitter pattern for React components
  on(event, callback) {
    if (!this.listeners.has(event)) {
      this.listeners.set(event, []);
    }
    this.listeners.get(event).push(callback);
  }

  off(event, callback) {
    if (this.listeners.has(event)) {
      const callbacks = this.listeners.get(event);
      const index = callbacks.indexOf(callback);
      if (index > -1) {
        callbacks.splice(index, 1);
      }
    }
  }

  emit(event, data) {
    if (this.listeners.has(event)) {
      this.listeners.get(event).forEach(callback => callback(data));
    }
  }

  // Check if Bluetooth is enabled
  async isBluetoothEnabled() {
    try {
      const result = await Ble.isEnabled();
      return result.enabled;
    } catch (error) {
      console.error('Error checking Bluetooth status:', error);
      return false;
    }
  }

  // Scan for BLE devices
  async scanForDevices(timeout = 10000, deviceName = null) {
    try {
      this.isScanning = true;
      const params = { timeout };
      
      if (deviceName) {
        params.deviceName = deviceName;
      }
      
      const result = await Ble.startScan(params);
      this.isScanning = false;
      
      console.log(`Scan complete. Found ${result.count} devices:`, result.devices);
      return result.devices;
    } catch (error) {
      this.isScanning = false;
      console.error('Error scanning for devices:', error);
      throw error;
    }
  }

  // Scan specifically for ESP32
  async scanForESP32(timeout = 10000) {
    return this.scanForDevices(timeout, ESP32_CONFIG.DEVICE_NAME);
  }

  // Stop scanning
  async stopScan() {
    try {
      const result = await Ble.stopScan();
      this.isScanning = false;
      return result.devices;
    } catch (error) {
      console.error('Error stopping scan:', error);
      throw error;
    }
  }

  // Connect to a device by address
  async connect(deviceAddress) {
    try {
      console.log('Connecting to device:', deviceAddress);
      const result = await Ble.connect({ deviceAddress });
      this.connectedDevice = {
        address: deviceAddress,
        name: result.deviceName
      };
      console.log('Connected successfully:', result);
      return result;
    } catch (error) {
      console.error('Error connecting to device:', error);
      throw error;
    }
  }

  // Disconnect from current device
  async disconnect() {
    try {
      await Ble.disconnect();
      this.connectedDevice = null;
      console.log('Disconnected from device');
    } catch (error) {
      console.error('Error disconnecting:', error);
      throw error;
    }
  }

  // Read data from ESP32
  async readData(serviceUuid = ESP32_CONFIG.SERVICE_UUID, characteristicUuid = ESP32_CONFIG.CHARACTERISTIC_UUID) {
    try {
      if (!this.connectedDevice) {
        throw new Error('Not connected to any device');
      }
      
      const result = await Ble.read({ serviceUuid, characteristicUuid });
      console.log('Read data:', result.value);
      return result.value;
    } catch (error) {
      console.error('Error reading data:', error);
      throw error;
    }
  }

  // Write data to ESP32
  async writeData(value, serviceUuid = ESP32_CONFIG.SERVICE_UUID, characteristicUuid = ESP32_CONFIG.CHARACTERISTIC_UUID) {
    try {
      if (!this.connectedDevice) {
        throw new Error('Not connected to any device');
      }
      
      const result = await Ble.write({ 
        serviceUuid, 
        characteristicUuid, 
        value 
      });
      console.log('Write successful:', result);
      return result;
    } catch (error) {
      console.error('Error writing data:', error);
      throw error;
    }
  }

  // Send command to ESP32
  async sendCommand(command) {
    return this.writeData(`Cmd:${command}`);
  }

  // Enable notifications to receive automatic updates from ESP32
  async enableNotifications(serviceUuid = ESP32_CONFIG.SERVICE_UUID, characteristicUuid = ESP32_CONFIG.CHARACTERISTIC_UUID) {
    try {
      if (!this.connectedDevice) {
        throw new Error('Not connected to any device');
      }
      
      const result = await Ble.enableNotifications({ serviceUuid, characteristicUuid });
      console.log('Notifications enabled:', result);
      return result;
    } catch (error) {
      console.error('Error enabling notifications:', error);
      throw error;
    }
  }

  // Get available services and characteristics
  async getServices() {
    try {
      if (!this.connectedDevice) {
        throw new Error('Not connected to any device');
      }
      
      const result = await Ble.getServices();
      console.log('Services:', result.services);
      return result.services;
    } catch (error) {
      console.error('Error getting services:', error);
      throw error;
    }
  }

  // Request larger MTU for bigger data packets
  async requestMtu(mtu = 512) {
    try {
      if (!this.connectedDevice) {
        throw new Error('Not connected to any device');
      }
      
      const result = await Ble.requestMtu({ mtu });
      console.log('MTU request:', result);
      return result;
    } catch (error) {
      console.error('Error requesting MTU:', error);
      throw error;
    }
  }

  // Quick connect to ESP32 and enable notifications
  async quickConnectESP32() {
    try {
      // 1. Scan for ESP32
      console.log('Scanning for ESP32...');
      const devices = await this.scanForESP32(10000);
      
      if (devices.length === 0) {
        throw new Error('ESP32 device not found');
      }
      
      // 2. Connect to first ESP32 found
      const esp32 = devices[0];
      console.log('Found ESP32:', esp32);
      await this.connect(esp32.address);
      
      // 3. Enable notifications for automatic updates
      await this.enableNotifications();
      
      console.log('Quick connect successful!');
      return this.connectedDevice;
    } catch (error) {
      console.error('Quick connect failed:', error);
      throw error;
    }
  }

  // Get connection status
  isConnected() {
    return this.connectedDevice !== null;
  }

  // Get connected device info
  getConnectedDevice() {
    return this.connectedDevice;
  }
}

// Export singleton instance
export const bleService = new BleService();

// Export configuration for customization
export { ESP32_CONFIG };

// Export the Ble plugin for advanced usage
export { Ble };
