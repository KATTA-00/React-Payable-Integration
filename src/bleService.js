// BLE Service for ESP32 Communication
// This file provides a JavaScript interface to the native BLE plugin

import { registerPlugin, Capacitor } from '@capacitor/core';

// Initialize the BLE Bridge
const Ble = registerPlugin('Ble');

// Check if running on web
const isWeb = Capacitor.getPlatform() === 'web';

class BleService {
  constructor() {
    this.connectedDevice = null;
    this.isScanning = false;
    this.listeners = new Map();
    this.isWeb = isWeb;
    this.selectedService = null;
    this.selectedCharacteristic = null;
    
    // Only set up event listeners on native platforms
    if (!isWeb) {
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
    if (isWeb) {
      throw new Error('BLE is only available on Android devices. Please build and run the app on an Android device.');
    }
    try {
      const result = await Ble.isEnabled();
      return result.enabled;
    } catch (error) {
      console.error('Error checking Bluetooth status:', error);
      throw error;
    }
  }

  // Scan for BLE devices
  async scanForDevices(timeout = 10000) {
    try {
      this.isScanning = true;
      const params = { timeout };
      
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

  // Read data from BLE device
  async readData(serviceUuid = null, characteristicUuid = null) {
    try {
      if (!this.connectedDevice) {
        throw new Error('Not connected to any device');
      }
      
      const sUuid = serviceUuid || this.selectedService;
      const cUuid = characteristicUuid || this.selectedCharacteristic;
      
      if (!sUuid || !cUuid) {
        throw new Error('Service and Characteristic must be selected');
      }
      
      const result = await Ble.read({ serviceUuid: sUuid, characteristicUuid: cUuid });
      console.log('Read data:', result.value);
      return result.value;
    } catch (error) {
      console.error('Error reading data:', error);
      throw error;
    }
  }

  // Write data to BLE device
  async writeData(value, serviceUuid = null, characteristicUuid = null) {
    try {
      if (!this.connectedDevice) {
        throw new Error('Not connected to any device');
      }
      
      const sUuid = serviceUuid || this.selectedService;
      const cUuid = characteristicUuid || this.selectedCharacteristic;
      
      if (!sUuid || !cUuid) {
        throw new Error('Service and Characteristic must be selected');
      }
      
      const result = await Ble.write({ 
        serviceUuid: sUuid, 
        characteristicUuid: cUuid, 
        value 
      });
      console.log('Write successful:', result);
      return result;
    } catch (error) {
      console.error('Error writing data:', error);
      throw error;
    }
  }

  // Send text message to BLE device
  async sendMessage(message) {
    return this.writeData(message);
  }

  // Set the active service and characteristic for communication
  setActiveCharacteristic(serviceUuid, characteristicUuid) {
    this.selectedService = serviceUuid;
    this.selectedCharacteristic = characteristicUuid;
    console.log('Active characteristic set:', { serviceUuid, characteristicUuid });
  }

  // Enable notifications to receive automatic updates
  async enableNotifications(serviceUuid = null, characteristicUuid = null) {
    try {
      if (!this.connectedDevice) {
        throw new Error('Not connected to any device');
      }
      
      const sUuid = serviceUuid || this.selectedService;
      const cUuid = characteristicUuid || this.selectedCharacteristic;
      
      if (!sUuid || !cUuid) {
        throw new Error('Service and Characteristic must be selected');
      }
      
      const result = await Ble.enableNotifications({ serviceUuid: sUuid, characteristicUuid: cUuid });
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

  // Get active characteristic info
  getActiveCharacteristic() {
    return {
      service: this.selectedService,
      characteristic: this.selectedCharacteristic
    };
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

// Export the Ble plugin for advanced usage
export { Ble };
