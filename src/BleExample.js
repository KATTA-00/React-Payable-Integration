import React, { useState, useEffect } from 'react';
import { bleService } from './bleService';
import './BleExample.css';

/**
 * Example React Component for ESP32 BLE Communication
 * 
 * This demonstrates:
 * - Scanning for devices
 * - Connecting to ESP32
 * - Reading/Writing data
 * - Receiving notifications
 */
function BleExample() {
  const [isBluetoothEnabled, setIsBluetoothEnabled] = useState(false);
  const [isScanning, setIsScanning] = useState(false);
  const [devices, setDevices] = useState([]);
  const [connectedDevice, setConnectedDevice] = useState(null);
  const [receivedData, setReceivedData] = useState([]);
  const [commandInput, setCommandInput] = useState('');
  const [status, setStatus] = useState('');

  useEffect(() => {
    // Check Bluetooth status on mount
    checkBluetoothStatus();

    // Set up event listeners
    const handleDeviceFound = (device) => {
      setDevices(prev => {
        // Avoid duplicates
        if (prev.find(d => d.address === device.address)) {
          return prev;
        }
        return [...prev, device];
      });
    };

    const handleDisconnected = () => {
      setConnectedDevice(null);
      setStatus('Device disconnected');
    };

    const handleDataReceived = (data) => {
      const timestamp = new Date().toLocaleTimeString();
      setReceivedData(prev => [...prev, { 
        time: timestamp, 
        value: data.value,
        uuid: data.characteristicUuid 
      }]);
    };

    bleService.on('deviceFound', handleDeviceFound);
    bleService.on('disconnected', handleDisconnected);
    bleService.on('dataReceived', handleDataReceived);

    // Cleanup
    return () => {
      bleService.off('deviceFound', handleDeviceFound);
      bleService.off('disconnected', handleDisconnected);
      bleService.off('dataReceived', handleDataReceived);
    };
  }, []);

  const checkBluetoothStatus = async () => {
    try {
      const enabled = await bleService.isBluetoothEnabled();
      setIsBluetoothEnabled(enabled);
      setStatus(enabled ? 'Bluetooth is enabled' : 'Bluetooth is disabled');
    } catch (error) {
      setStatus('Error checking Bluetooth: ' + error.message);
    }
  };

  const handleScan = async () => {
    try {
      setIsScanning(true);
      setDevices([]);
      setStatus('Scanning for devices...');
      
      const foundDevices = await bleService.scanForDevices(10000);
      
      setStatus(`Scan complete. Found ${foundDevices.length} devices`);
      setIsScanning(false);
    } catch (error) {
      setStatus('Scan error: ' + error.message);
      setIsScanning(false);
    }
  };

  const handleQuickConnect = async () => {
    try {
      setStatus('Quick connecting to ESP32...');
      const device = await bleService.quickConnectESP32();
      setConnectedDevice(device);
      setStatus(`Connected to ${device.name || device.address}`);
    } catch (error) {
      setStatus('Quick connect error: ' + error.message);
    }
  };

  const handleConnect = async (deviceAddress) => {
    try {
      setStatus('Connecting...');
      await bleService.connect(deviceAddress);
      
      // Enable notifications automatically
      await bleService.enableNotifications();
      
      setConnectedDevice(bleService.getConnectedDevice());
      setStatus('Connected successfully');
    } catch (error) {
      setStatus('Connection error: ' + error.message);
    }
  };

  const handleDisconnect = async () => {
    try {
      await bleService.disconnect();
      setConnectedDevice(null);
      setStatus('Disconnected');
    } catch (error) {
      setStatus('Disconnect error: ' + error.message);
    }
  };

  const handleSendCommand = async () => {
    try {
      if (!commandInput.trim()) {
        setStatus('Please enter a command');
        return;
      }
      
      setStatus('Sending command...');
      await bleService.sendCommand(commandInput);
      setStatus(`Command sent: ${commandInput}`);
      setCommandInput('');
    } catch (error) {
      setStatus('Send error: ' + error.message);
    }
  };

  const handleRead = async () => {
    try {
      setStatus('Reading data...');
      const data = await bleService.readData();
      setStatus(`Read: ${data}`);
    } catch (error) {
      setStatus('Read error: ' + error.message);
    }
  };

  const handleGetServices = async () => {
    try {
      setStatus('Fetching services...');
      const services = await bleService.getServices();
      console.log('Services:', services);
      setStatus(`Found ${services.length} services (check console)`);
    } catch (error) {
      setStatus('Error getting services: ' + error.message);
    }
  };

  return (
    <div className="ble-container">
      <h2>ESP32 BLE Controller</h2>
      
      {/* Status Bar */}
      <div className="status-bar">
        <div className={`status-indicator ${isBluetoothEnabled ? 'enabled' : 'disabled'}`}>
          {isBluetoothEnabled ? '● Bluetooth ON' : '○ Bluetooth OFF'}
        </div>
        <div className={`status-indicator ${connectedDevice ? 'connected' : 'disconnected'}`}>
          {connectedDevice ? `● Connected: ${connectedDevice.name || connectedDevice.address}` : '○ Not Connected'}
        </div>
      </div>
      
      {/* Status Message */}
      <div className="status-message">{status}</div>

      {/* Scan Section */}
      <div className="section">
        <h3>Device Discovery</h3>
        <div className="button-group">
          <button 
            onClick={handleScan} 
            disabled={isScanning || !isBluetoothEnabled}
            className="btn btn-primary"
          >
            {isScanning ? 'Scanning...' : 'Scan for Devices'}
          </button>
          <button 
            onClick={handleQuickConnect}
            disabled={!isBluetoothEnabled || connectedDevice}
            className="btn btn-success"
          >
            Quick Connect to ESP32
          </button>
          <button 
            onClick={checkBluetoothStatus}
            className="btn btn-secondary"
          >
            Check Bluetooth
          </button>
        </div>

        {/* Device List */}
        {devices.length > 0 && (
          <div className="device-list">
            <h4>Found Devices:</h4>
            {devices.map((device) => (
              <div key={device.address} className="device-item">
                <div className="device-info">
                  <strong>{device.name || 'Unknown Device'}</strong>
                  <br />
                  <small>{device.address}</small>
                  {device.rssi && <small> | Signal: {device.rssi} dBm</small>}
                </div>
                <button 
                  onClick={() => handleConnect(device.address)}
                  disabled={connectedDevice}
                  className="btn btn-small"
                >
                  Connect
                </button>
              </div>
            ))}
          </div>
        )}
      </div>

      {/* Control Section */}
      {connectedDevice && (
        <div className="section">
          <h3>Device Control</h3>
          
          <div className="button-group">
            <button onClick={handleRead} className="btn btn-info">
              Read Data
            </button>
            <button onClick={handleGetServices} className="btn btn-info">
              Get Services
            </button>
            <button onClick={handleDisconnect} className="btn btn-danger">
              Disconnect
            </button>
          </div>

          {/* Command Input */}
          <div className="command-section">
            <h4>Send Command</h4>
            <div className="input-group">
              <input
                type="text"
                value={commandInput}
                onChange={(e) => setCommandInput(e.target.value)}
                onKeyPress={(e) => e.key === 'Enter' && handleSendCommand()}
                placeholder="Enter command (e.g., LED_ON)"
                className="input-field"
              />
              <button onClick={handleSendCommand} className="btn btn-primary">
                Send
              </button>
            </div>
            
            {/* Quick Command Buttons */}
            <div className="quick-commands">
              <button onClick={() => { setCommandInput('LED_ON'); handleSendCommand(); }} className="btn btn-small">
                LED ON
              </button>
              <button onClick={() => { setCommandInput('LED_OFF'); handleSendCommand(); }} className="btn btn-small">
                LED OFF
              </button>
              <button onClick={() => { setCommandInput('STATUS'); handleSendCommand(); }} className="btn btn-small">
                Get Status
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Data Log */}
      {receivedData.length > 0 && (
        <div className="section">
          <h3>Received Data Log</h3>
          <button 
            onClick={() => setReceivedData([])} 
            className="btn btn-small btn-secondary"
          >
            Clear Log
          </button>
          <div className="data-log">
            {receivedData.slice().reverse().map((item, index) => (
              <div key={index} className="data-item">
                <span className="data-time">{item.time}</span>
                <span className="data-value">{item.value}</span>
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}

export default BleExample;
