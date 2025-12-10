import React, { useState } from 'react';
import './App.css';
import { registerPlugin } from '@capacitor/core';

// Initialize the Bridge
const Payable = registerPlugin('Payable');

function App() {
  const [text, setText] = useState('');

  const handleButtonClick = () => {
    console.log(text);
  };

  const handlePay = async () => {
    try {
      // Parse the amount from text input
      const amount = parseFloat(text);
      
      // Validate amount
      if (isNaN(amount) || amount <= 0) {
        alert('Please enter a valid amount greater than 0');
        return;
      }
      
      // Call the Native Function
      const result = await Payable.pay({ amount: amount });
      
      alert(`Payment Approved! ID: ${result.txnId}, Amount: Rs. ${amount}`);
      // Send result to your backend here...
      
    } catch (error) {
      alert(`Payment Failed: ${error.message}`);
    }
  };

  return (
    <div className="App">
      <div className="container">
        <h1>Simple React App</h1>
        <input
          type="number"
          value={text}
          onChange={(e) => setText(e.target.value)}
          placeholder="Enter amount..."
          className="text-input"
        />
        <button onClick={handleButtonClick} className="submit-button">
          Print Text
        </button>
        
        <div style={{ marginTop: '20px' }}>
          <button onClick={handlePay} className="submit-button">
            Pay Amount
          </button>
        </div>
      </div>
    </div>
  );
}

export default App;
