import React, { useState, useEffect } from 'react';
import { bleService } from './bleService';
import './BleExample.css';

/**
 * Generic BLE Communication App
 * 
 * This demonstrates:
 * - Scanning for any BLE device
 * - Connecting and browsing services
 * - Sending/Receiving text messages
 * - Real-time notifications
 */
function BleExample() {
  const [isBluetoothEnabled, setIsBluetoothEnabled] = useState(false);
  const [isScanning, setIsScanning] = useState(false);
  const [devices, setDevices] = useState([]);
  const [connectedDevice, setConnectedDevice] = useState(null);
  const [services, setServices] = useState([]);
  const [selectedService, setSelectedService] = useState('');
  const [selectedCharacteristic, setSelectedCharacteristic] = useState('');
  const [receivedData, setReceivedData] = useState([]);
  const [messageInput, setMessageInput] = useState('');
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
      console.error('Bluetooth check error:', error);
      setStatus(error.message || 'Error checking Bluetooth');
      setIsBluetoothEnabled(false);
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

  const handleConnect = async (deviceAddress) => {
    try {
      setStatus('Connecting...');
      await bleService.connect(deviceAddress);
      setConnectedDevice(bleService.getConnectedDevice());
      
      // Wait a bit for all services to be discovered
      await new Promise(resolve => setTimeout(resolve, 2000));
      
      // Automatically fetch services after connection
      setStatus('Fetching services...');
      const deviceServices = await bleService.getServices();
      setServices(deviceServices);
      
      setStatus(`Connected successfully. Found ${deviceServices.length} services`);
    } catch (error) {
      setStatus('Connection error: ' + error.message);
    }
  };

  const handleDisconnect = async () => {
    try {
      await bleService.disconnect();
      setConnectedDevice(null);
      setServices([]);
      setSelectedService('');
      setSelectedCharacteristic('');
      setStatus('Disconnected');
    } catch (error) {
      setStatus('Disconnect error: ' + error.message);
    }
  };

  const handleSendMessage = async () => {
    try {
      if (!messageInput.trim()) {
        setStatus('Please enter a message');
        return;
      }
      
      if (!selectedService || !selectedCharacteristic) {
        setStatus('Please select a service and characteristic first');
        return;
      }
      
      setStatus('Sending message...');
      await bleService.sendMessage(messageInput);
      setStatus(`Message sent: ${messageInput}`);
      setMessageInput('');
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

  const handleSelectCharacteristic = (serviceUuid, charUuid) => {
    setSelectedService(serviceUuid);
    setSelectedCharacteristic(charUuid);
    bleService.setActiveCharacteristic(serviceUuid, charUuid);
    setStatus(`Selected characteristic: ${charUuid.substring(0, 8)}...`);
  };

  const handleEnableNotifications = async () => {
    try {
      if (!selectedService || !selectedCharacteristic) {
        setStatus('Please select a service and characteristic first');
        return;
      }
      
      setStatus('Enabling notifications...');
      await bleService.enableNotifications();
      setStatus('Notifications enabled successfully');
    } catch (error) {
      setStatus('Notification error: ' + error.message);
    }
  };

  const handleRefreshServices = async () => {
    try {
      setStatus('Refreshing services...');
      const deviceServices = await bleService.getServices();
      setServices(deviceServices);
      console.log('All discovered services:', deviceServices);
      setStatus(`Refreshed. Found ${deviceServices.length} services`);
    } catch (error) {
      setStatus('Refresh error: ' + error.message);
    }
  };

  return (
    <div className="ble-container">
      <h2>BLE Device Controller</h2>
      
      {/* Status Bar */}
      <div className="status-bar">
        <div className={`status-indicator ${isBluetoothEnabled ? 'enabled' : 'disabled'}`}>
          {isBluetoothEnabled ? '● Bluetooth ON' : '○ Bluetooth OFF'}
        </div>
        <div className={`status-indicator ${connectedDevice ? 'connected' : 'disconnected'}`}>
          {connectedDevice ? (
            <>
              ● Connected<br/>
              <small>{connectedDevice.name || 'Unknown'}</small><br/>
              <small style={{fontFamily: 'monospace', fontSize: '10px'}}>{connectedDevice.address}</small>
            </>
          ) : '○ Not Connected'}
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

      {/* Services Section - Show after connection */}
      {connectedDevice && services.length > 0 && (
        <div className="section">
          <h3>Available Services
            <button 
              onClick={handleRefreshServices}
              className="btn btn-small btn-secondary"
              style={{marginLeft: '10px', fontSize: '12px'}}
            >
              🔄 Refresh
            </button>
          </h3>
          <div className="services-list">
            {services.map((service) => (
              <div key={service.uuid} className="service-item">
                <div className="service-header">
                  <strong>Service:</strong> {service.uuid} ({service.type})
                </div>
                <div className="characteristics-list">
                  {service.characteristics.map((char) => (
                    <div 
                      key={char.uuid} 
                      className={`characteristic-item ${
                        selectedCharacteristic === char.uuid ? 'selected' : ''
                      }`}
                      onClick={() => handleSelectCharacteristic(service.uuid, char.uuid)}
                    >
                      <div className="char-uuid">
                        {char.uuid}
                      </div>
                      <div className="char-properties">
                        {char.properties & 0x02 ? '📖 ' : ''}
                        {(char.properties & 0x08 || char.properties & 0x04) ? '✏️ ' : ''}
                        {(char.properties & 0x10 || char.properties & 0x20) ? '🔔 ' : ''}
                      </div>
                    </div>
                  ))}
                </div>
              </div>
            ))}
          </div>
          <div className="legend">
            <small>📖 = Read | ✏️ = Write | 🔔 = Notify</small>
          </div>
        </div>
      )}

      {/* Control Section */}
      {connectedDevice && (
        <div className="section">
          <h3>Device Control</h3>
          
          <div className="button-group">
            <button 
              onClick={handleRead} 
              disabled={!selectedCharacteristic}
              className="btn btn-info"
            >
              Read Data
            </button>
            <button 
              onClick={handleEnableNotifications}
              disabled={!selectedCharacteristic}
              className="btn btn-success"
            >
              Enable Notifications
            </button>
            <button onClick={handleDisconnect} className="btn btn-danger">
              Disconnect
            </button>
          </div>

          {/* Message Input */}
          <div className="command-section">
            <h4>Send Message</h4>
            {selectedCharacteristic ? (
              <>
                <div className="input-group">
                  <input
                    type="text"
                    value={messageInput}
                    onChange={(e) => setMessageInput(e.target.value)}
                    onKeyPress={(e) => e.key === 'Enter' && handleSendMessage()}
                    placeholder="Enter message to send..."
                    className="input-field"
                  />
                  <button onClick={handleSendMessage} className="btn btn-primary">
                    Send
                  </button>
                </div>
              </>
            ) : (
              <p className="warning">⚠️ Please select a characteristic above first</p>
            )}
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
