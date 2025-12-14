import React, { useState } from 'react';
import './App.css';
import { registerPlugin } from '@capacitor/core';

// Initialize the Bridge
const Payable = registerPlugin('Payable');

function App() {
  const [text, setText] = useState('');
  
  // New State: Store the details of the last successful transaction
  const [lastTransaction, setLastTransaction] = useState(null);

  const handleButtonClick = () => {
    console.log(text);
  };

  const handlePay = async () => {
    try {
      const amount = parseFloat(text);
      
      if (isNaN(amount) || amount <= 0) {
        alert('Please enter a valid amount greater than 0');
        return;
      }
      
      console.log("Starting Payment...");
      const result = await Payable.pay({ amount: amount });
      
      console.log("Payment Result:", result);
      
      // 1. SAVE the transaction details so we can void it later
      setLastTransaction({
        txId: result.txnId,
        cardType: result.cardType, // Important: 1=VISA, 3=MASTER, etc.
        amount: amount
      });

      alert(`Payment Approved! ID: ${result.txnId}`);
      
    } catch (error) {
      console.error(error);
      alert(`Payment Failed: ${error.message}`);
    }
  };

  // 2. Handle Voiding
  const handleVoid = async () => {
    if (!lastTransaction) return;

    try {
      console.log("Voiding Transaction:", lastTransaction.txId);

      const result = await Payable.voidTransaction({ 
        txId: lastTransaction.txId,
        cardType: lastTransaction.cardType 
      });

      console.log("Void Result:", result);

      if (result.status === 222 || result.status === "success") { // 222 is common success code
         alert(`Transaction ${lastTransaction.txId} Voided Successfully!`);
         setLastTransaction(null); // Clear the state so user can't void twice
      } else {
         alert("Void Status Unknown: " + result.status);
      }

    } catch (error) {
      console.error(error);
      alert(`Void Failed: ${error.message}`);
    }
  };

  return (
    <div className="App">
      <div className="container">
        <h1>POS Terminal</h1>
        
        {/* Input Section */}
        <input
          type="number"
          value={text}
          onChange={(e) => setText(e.target.value)}
          placeholder="Enter amount..."
          className="text-input"
        />
        
        <div style={{ display: 'flex', gap: '10px', marginTop: '20px', justifyContent: 'center' }}>
            <button onClick={handleButtonClick} className="submit-button">
            Log Text
            </button>
            
            <button onClick={handlePay} className="submit-button" style={{ backgroundColor: '#28a745' }}>
            Pay Amount
            </button>
        </div>

        {/* 3. Conditional Rendering: Only show VOID button if we have a transaction */}
        {lastTransaction && (
          <div style={{ marginTop: '30px', padding: '15px', border: '1px solid #ccc', borderRadius: '8px' }}>
            <h3>Last Transaction</h3>
            <p>ID: <strong>{lastTransaction.txId}</strong></p>
            <p>Amount: Rs. {lastTransaction.amount}</p>
            
            <button 
              onClick={handleVoid} 
              className="submit-button" 
              style={{ backgroundColor: '#dc3545', marginTop: '10px' }}
            >
              Void This Transaction
            </button>
          </div>
        )}

      </div>
    </div>
  );
}

export default App;