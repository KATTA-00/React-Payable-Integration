package com.attune.pos;

import android.content.Intent;
import android.content.res.AssetManager;
import android.util.Log;
import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import com.payable.sdk.Payable;
import com.payable.sdk.PayableEventListener;
import com.payable.sdk.PayableListener;
import com.payable.sdk.PayableProfile;
import com.payable.sdk.PayableResponse;
import com.payable.sdk.PayableSale;
import com.payable.sdk.PayableTxStatusResponse;
import com.payable.sdk.PayableTxStatusResponseV2;

import java.util.List;

@CapacitorPlugin(name = "Payable")
public class PayablePlugin extends Plugin implements PayableListener, PayableEventListener {

    private Payable payableClient;
    private PluginCall activeCall; // Tracks the current active request from React

    // --- STATIC INSTANCE FOR BRIDGING ---
    private static PayablePlugin instance;

    @Override
    public void load() {
        // Capture the instance when the plugin loads
        instance = this;

        // Load credentials from config file
        Properties config = new Properties();
        String clientId = null;
        String clientName = null;
        String apiKey = null;

        try {
            AssetManager assetManager = getActivity().getAssets();
            InputStream inputStream = assetManager.open("payable-config.properties");
            config.load(inputStream);

            clientId = config.getProperty("payable.client.id");
            clientName = config.getProperty("payable.client.name");
            apiKey = config.getProperty("payable.api.key");

            inputStream.close();
        } catch (IOException e) {
            Log.e("PayablePlugin", "Failed to load payable-config.properties: " + e.getMessage());
            Log.e("PayablePlugin", "Please ensure payable-config.properties exists in assets folder");
        }

        // Initialize the SDK with credentials from config
        if (clientId != null && clientName != null && apiKey != null) {
            payableClient = Payable.createPayableClient(
                    getActivity(),
                    clientId,
                    clientName,
                    apiKey
            );

            // Register for advanced events (Void, Status, Profiles)
            if (payableClient != null) {
                payableClient.registerEventListener(this);
            }
        } else {
            Log.e("PayablePlugin", "Missing required configuration properties. SDK not initialized.");
        }
    }

    // =================================================================
    // STATIC BRIDGE METHOD (Called from MainActivity)
    // =================================================================

    public static void processActivityResult(int requestCode, int resultCode, Intent data) {
        if (instance != null && instance.payableClient != null) {
            Log.d("PayablePlugin", "Processing Activity Result via Static Bridge");
            instance.payableClient.handleResponse(requestCode, data);
        } else {
            Log.e("PayablePlugin", "Instance or Client is null, cannot process result");
        }
    }

    // =================================================================
    // REACT EXPOSED METHODS (API)
    // =================================================================

    /**
     * Start a Payment
     * Usage: Payable.pay({ amount: 100.00, receiptEmail: "test@abc.com" })
     */
    @PluginMethod
    public void pay(PluginCall call) {
        if (!setCall(call)) return;

        Double amount = call.getDouble("amount");
        if (amount == null) {
            call.reject("Amount is required");
            return;
        }

        Log.d("PayablePlugin", "Recieved payment request for: " + amount);

        // Create Sale Object
        PayableSale payableSale = new PayableSale(amount, Payable.METHOD_CARD);

        // Optional: Add receipt details if provided
        String email = call.getString("receiptEmail");
        if (email != null) payableSale.setReceiptEmail(email);

        String sms = call.getString("receiptSMS");
        if (sms != null) payableSale.setReceiptSMS(sms);

        // Start the Payment UI
        if (payableClient != null) {
            payableClient.startPayment(payableSale, this);
        } else {
            call.reject("Payable Client not initialized");
            activeCall = null;
        }
    }

    /**
     * Void a Transaction
     * Usage: Payable.voidTransaction({ txId: "12345", cardType: 1 })
     * Card Types: VISA=1, MASTER=3 (See SDK Docs)
     */
    @PluginMethod
    public void voidTransaction(PluginCall call) {
        if (!setCall(call)) return;

        String txId = call.getString("txId");
        Integer cardType = call.getInt("cardType");

        if (txId == null || cardType == null) {
            call.reject("txId and cardType are required");
            return;
        }

        // Trigger Void Request
        payableClient.requestVoid(txId, cardType);
    }

    /**
     * Check Transaction Status
     * Usage: Payable.getTransactionStatus({ txId: "12345", cardType: 1 })
     */
    @PluginMethod
    public void getTransactionStatus(PluginCall call) {
        if (!setCall(call)) return;

        String txId = call.getString("txId");
        Integer cardType = call.getInt("cardType");

        if (txId == null || cardType == null) {
            call.reject("txId and cardType are required");
            return;
        }

        // Trigger Status Request
        payableClient.requestTransactionStatus(txId, cardType);
    }

    /**
     * Get List of Terminal Profiles
     * Usage: Payable.getProfileList()
     */
    @PluginMethod
    public void getProfileList(PluginCall call) {
        if (!setCall(call)) return;
        payableClient.requestProfileList();
    }

    // =================================================================
    // CALLBACK HANDLERS (LISTENER IMPLEMENTATION)
    // =================================================================

    // --- 1. Payment Callbacks (PayableListener) ---

    @Override
    public boolean onPaymentStart(PayableSale payableSale) {
        Log.d("PayablePlugin", "onPaymentStart");
        return true; // Allow payment to start
    }

    @Override
    public void onPaymentSuccess(PayableSale payableSale) {
        Log.d("PayablePlugin", "onPaymentSuccess: " + payableSale.getTxId());
        if (activeCall != null) {
            JSObject ret = new JSObject();
            ret.put("status", "success");
            ret.put("txnId", payableSale.getTxId());
            ret.put("cardNo", payableSale.getCardNo());
            ret.put("cardType", payableSale.getCardType());
            ret.put("amount", payableSale.getSaleAmount());
            activeCall.resolve(ret);
            activeCall = null; // Reset
        }
    }

    @Override
    public void onPaymentFailure(PayableSale payableSale) {
        Log.d("PayablePlugin", "onPaymentFailure: " + payableSale.getMessage());
        if (activeCall != null) {
            activeCall.reject(payableSale.getMessage(), "" + payableSale.getStatusCode());
            activeCall = null;
        }
    }

    // --- 2. Advanced Action Callbacks (PayableEventListener) ---

    @Override
    public void onVoid(PayableResponse payableResponse) {
        if (activeCall != null) {
            JSObject ret = new JSObject();
            ret.put("status", payableResponse.status);
            ret.put("txId", payableResponse.txId);
            ret.put("error", payableResponse.error);

            if (payableResponse.status == 222) {
                activeCall.resolve(ret);
            } else {
                activeCall.reject(payableResponse.error != null ? payableResponse.error : "Void Failed");
            }
            activeCall = null;
        }
    }

    @Override
    public void onTransactionStatus(PayableTxStatusResponse payableResponse) {
        if (activeCall != null) {
            JSObject ret = new JSObject();
            ret.put("txId", payableResponse.txId);
            ret.put("status", payableResponse.status);
            ret.put("amount", payableResponse.amount);
            ret.put("cardType", payableResponse.cardType);
            ret.put("error", payableResponse.error);

            activeCall.resolve(ret);
            activeCall = null;
        }
    }

    @Override
    public void onTransactionStatusV2(PayableTxStatusResponseV2 payableResponse) {
        // Optional V2 implementation if needed
    }

    @Override
    public void onProfileList(List<PayableProfile> payableProfiles) {
        if (activeCall != null) {
            JSArray profilesArray = new JSArray();
            for (PayableProfile p : payableProfiles) {
                JSObject obj = new JSObject();
                obj.put("tid", p.tid);
                obj.put("name", p.name);
                obj.put("currency", p.currency);
                profilesArray.put(obj);
            }

            JSObject ret = new JSObject();
            ret.put("profiles", profilesArray);
            activeCall.resolve(ret);
            activeCall = null;
        }
    }

    // =================================================================
    // SYSTEM HOOKS
    // =================================================================

    // Note: removed the @Override handleOnActivityResult here because
    // it's now handled by the static processActivityResult called from MainActivity.

    // Cleanup listeners when app closes
    @Override
    protected void handleOnDestroy() {
        if (payableClient != null) {
            payableClient.unregisterEventListener();
            payableClient.unregisterProgressListener();
        }
        instance = null; // Clean up static reference
    }

    // Helper to ensure we don't overwrite an active call
    private boolean setCall(PluginCall call) {
        if (activeCall != null) {
            call.reject("Another operation is already in progress");
            return false;
        }
        activeCall = call;
        return true;
    }
}