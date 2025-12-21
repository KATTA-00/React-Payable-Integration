package com.attune.pos;

import android.content.Context;
import android.content.Intent;
import android.content.res.AssetManager;
import android.util.Log;
import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
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

    private static final String TAG = "PayablePlugin";
    private static final String LOG_FILE_NAME = "payable_logs.txt";
    
    private Payable payableClient;
    private PluginCall activeCall; // Tracks the current active request from React
    private File logFile;

    // --- STATIC INSTANCE FOR BRIDGING ---
    private static PayablePlugin instance;

    @Override
    public void load() {
        // Capture the instance when the plugin loads
        instance = this;
        
        // Initialize log file
        initLogFile();
        logInfo("Plugin loading...");

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
            logInfo("Configuration loaded successfully");
        } catch (IOException e) {
            String errorMsg = "Failed to load payable-config.properties: " + e.getMessage();
            logError(errorMsg, e);
            Log.e(TAG, errorMsg);
            Log.e(TAG, "Please ensure payable-config.properties exists in assets folder");
        } catch (Exception e) {
            String errorMsg = "Unexpected error loading configuration: " + e.getMessage();
            logError(errorMsg, e);
            Log.e(TAG, errorMsg);
        }

        // Initialize the SDK with credentials from config
        if (clientId != null && clientName != null && apiKey != null) {
            try {
                payableClient = Payable.createPayableClient(
                        getActivity(),
                        clientId,
                        clientName,
                        apiKey
                );

                // Register for advanced events (Void, Status, Profiles)
                if (payableClient != null) {
                    payableClient.registerEventListener(this);
                    logInfo("Payable SDK initialized successfully");
                }
            } catch (Exception e) {
                String errorMsg = "Failed to initialize Payable SDK: " + e.getMessage();
                logError(errorMsg, e);
                Log.e(TAG, errorMsg);
            }
        } else {
            String errorMsg = "Missing required configuration properties. SDK not initialized.";
            logError(errorMsg, null);
            Log.e(TAG, errorMsg);
        }
    }

    // =================================================================
    // LOGGING UTILITIES
    // =================================================================

    private void initLogFile() {
        try {
            Context context = getActivity();
            if (context != null) {
                File logsDir = new File(context.getFilesDir(), "logs");
                if (!logsDir.exists()) {
                    logsDir.mkdirs();
                }
                logFile = new File(logsDir, LOG_FILE_NAME);
                logInfo("Log file initialized at: " + logFile.getAbsolutePath());
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize log file: " + e.getMessage());
        }
    }

    private void writeToLogFile(String level, String message, Throwable throwable) {
        if (logFile == null) return;
        
        try {
            FileWriter fw = new FileWriter(logFile, true); // append mode
            PrintWriter pw = new PrintWriter(fw);
            
            String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(new Date());
            pw.println("[" + timestamp + "] [" + level + "] " + message);
            
            if (throwable != null) {
                pw.println("Exception: " + throwable.getClass().getName() + ": " + throwable.getMessage());
                for (StackTraceElement element : throwable.getStackTrace()) {
                    pw.println("    at " + element.toString());
                }
            }
            
            pw.close();
            fw.close();
        } catch (IOException e) {
            Log.e(TAG, "Failed to write to log file: " + e.getMessage());
        }
    }

    private void logInfo(String message) {
        Log.d(TAG, message);
        writeToLogFile("INFO", message, null);
    }

    private void logError(String message, Throwable throwable) {
        Log.e(TAG, message);
        writeToLogFile("ERROR", message, throwable);
    }

    private void logWarning(String message) {
        Log.w(TAG, message);
        writeToLogFile("WARN", message, null);
    }

    // =================================================================
    // STATIC BRIDGE METHOD (Called from MainActivity)
    // =================================================================

    public static void processActivityResult(int requestCode, int resultCode, Intent data) {
        if (instance != null && instance.payableClient != null) {
            try {
                instance.logInfo("Processing Activity Result - requestCode: " + requestCode + ", resultCode: " + resultCode);
                instance.payableClient.handleResponse(requestCode, data);
            } catch (Exception e) {
                instance.logError("Error processing activity result: " + e.getMessage(), e);
                if (instance.activeCall != null) {
                    instance.activeCall.reject("SDK Error: Failed to process payment response - " + e.getMessage());
                    instance.activeCall = null;
                }
            }
        } else {
            Log.e(TAG, "Instance or Client is null, cannot process result");
            if (instance != null) {
                instance.logError("Instance or Client is null, cannot process result", null);
            }
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

        try {
            Double amount = call.getDouble("amount");
            if (amount == null) {
                logWarning("Payment rejected: Amount is required");
                call.reject("Amount is required");
                activeCall = null;
                return;
            }

            logInfo("Received payment request for amount: " + amount);

            // Create Sale Object
            PayableSale payableSale = new PayableSale(amount, Payable.METHOD_CARD);

            // Optional: Add receipt details if provided
            String email = call.getString("receiptEmail");
            if (email != null) {
                payableSale.setReceiptEmail(email);
                logInfo("Receipt email set: " + email);
            }

            String sms = call.getString("receiptSMS");
            if (sms != null) {
                payableSale.setReceiptSMS(sms);
                logInfo("Receipt SMS set: " + sms);
            }

            // Start the Payment UI
            if (payableClient != null) {
                logInfo("Starting payment UI...");
                payableClient.startPayment(payableSale, this);
            } else {
                String errorMsg = "Payable Client not initialized. Please check configuration.";
                logError(errorMsg, null);
                call.reject(errorMsg);
                activeCall = null;
            }
        } catch (Exception e) {
            String errorMsg = "SDK Error during payment initiation: " + e.getMessage();
            logError(errorMsg, e);
            call.reject(errorMsg);
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

        try {
            String txId = call.getString("txId");
            Integer cardType = call.getInt("cardType");

            if (txId == null || cardType == null) {
                logWarning("Void rejected: txId and cardType are required");
                call.reject("txId and cardType are required");
                activeCall = null;
                return;
            }

            logInfo("Void request for txId: " + txId + ", cardType: " + cardType);

            if (payableClient == null) {
                String errorMsg = "Payable Client not initialized. Please check configuration.";
                logError(errorMsg, null);
                call.reject(errorMsg);
                activeCall = null;
                return;
            }

            // Trigger Void Request
            payableClient.requestVoid(txId, cardType);
        } catch (Exception e) {
            String errorMsg = "SDK Error during void request: " + e.getMessage();
            logError(errorMsg, e);
            call.reject(errorMsg);
            activeCall = null;
        }
    }

    /**
     * Check Transaction Status
     * Usage: Payable.getTransactionStatus({ txId: "12345", cardType: 1 })
     */
    @PluginMethod
    public void getTransactionStatus(PluginCall call) {
        if (!setCall(call)) return;

        try {
            String txId = call.getString("txId");
            Integer cardType = call.getInt("cardType");

            if (txId == null || cardType == null) {
                logWarning("Transaction status rejected: txId and cardType are required");
                call.reject("txId and cardType are required");
                activeCall = null;
                return;
            }

            logInfo("Transaction status request for txId: " + txId + ", cardType: " + cardType);

            if (payableClient == null) {
                String errorMsg = "Payable Client not initialized. Please check configuration.";
                logError(errorMsg, null);
                call.reject(errorMsg);
                activeCall = null;
                return;
            }

            // Trigger Status Request
            payableClient.requestTransactionStatus(txId, cardType);
        } catch (Exception e) {
            String errorMsg = "SDK Error during transaction status request: " + e.getMessage();
            logError(errorMsg, e);
            call.reject(errorMsg);
            activeCall = null;
        }
    }

    /**
     * Get List of Terminal Profiles
     * Usage: Payable.getProfileList()
     */
    @PluginMethod
    public void getProfileList(PluginCall call) {
        if (!setCall(call)) return;

        try {
            logInfo("Profile list request");

            if (payableClient == null) {
                String errorMsg = "Payable Client not initialized. Please check configuration.";
                logError(errorMsg, null);
                call.reject(errorMsg);
                activeCall = null;
                return;
            }

            payableClient.requestProfileList();
        } catch (Exception e) {
            String errorMsg = "SDK Error during profile list request: " + e.getMessage();
            logError(errorMsg, e);
            call.reject(errorMsg);
            activeCall = null;
        }
    }

    // =================================================================
    // CALLBACK HANDLERS (LISTENER IMPLEMENTATION)
    // =================================================================

    // --- 1. Payment Callbacks (PayableListener) ---

    @Override
    public boolean onPaymentStart(PayableSale payableSale) {
        logInfo("onPaymentStart - Payment process initiated");
        return true; // Allow payment to start
    }

    @Override
    public void onPaymentSuccess(PayableSale payableSale) {
        try {
            logInfo("onPaymentSuccess - txId: " + payableSale.getTxId() + ", amount: " + payableSale.getSaleAmount());
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
        } catch (Exception e) {
            logError("Error processing payment success callback: " + e.getMessage(), e);
            if (activeCall != null) {
                activeCall.reject("Error processing payment success: " + e.getMessage());
                activeCall = null;
            }
        }
    }

    @Override
    public void onPaymentFailure(PayableSale payableSale) {
        try {
            String message = payableSale.getMessage();
            int statusCode = payableSale.getStatusCode();
            logError("onPaymentFailure - message: " + message + ", statusCode: " + statusCode, null);
            
            if (activeCall != null) {
                activeCall.reject(message, "" + statusCode);
                activeCall = null;
            }
        } catch (Exception e) {
            logError("Error processing payment failure callback: " + e.getMessage(), e);
            if (activeCall != null) {
                activeCall.reject("Payment failed with unknown error");
                activeCall = null;
            }
        }
    }

    // --- 2. Advanced Action Callbacks (PayableEventListener) ---

    @Override
    public void onVoid(PayableResponse payableResponse) {
        try {
            logInfo("onVoid - status: " + payableResponse.status + ", txId: " + payableResponse.txId);
            
            if (activeCall != null) {
                JSObject ret = new JSObject();
                ret.put("status", payableResponse.status);
                ret.put("txId", payableResponse.txId);
                ret.put("error", payableResponse.error);

                if (payableResponse.status == 222) {
                    logInfo("Void successful for txId: " + payableResponse.txId);
                    activeCall.resolve(ret);
                } else {
                    String errorMsg = payableResponse.error != null ? payableResponse.error : "Void Failed";
                    logError("Void failed: " + errorMsg, null);
                    activeCall.reject(errorMsg);
                }
                activeCall = null;
            }
        } catch (Exception e) {
            logError("Error processing void callback: " + e.getMessage(), e);
            if (activeCall != null) {
                activeCall.reject("Error processing void response: " + e.getMessage());
                activeCall = null;
            }
        }
    }

    @Override
    public void onTransactionStatus(PayableTxStatusResponse payableResponse) {
        try {
            logInfo("onTransactionStatus - txId: " + payableResponse.txId + ", status: " + payableResponse.status);
            
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
        } catch (Exception e) {
            logError("Error processing transaction status callback: " + e.getMessage(), e);
            if (activeCall != null) {
                activeCall.reject("Error processing transaction status: " + e.getMessage());
                activeCall = null;
            }
        }
    }

    @Override
    public void onTransactionStatusV2(PayableTxStatusResponseV2 payableResponse) {
        // Optional V2 implementation if needed
        logInfo("onTransactionStatusV2 received");
    }

    @Override
    public void onProfileList(List<PayableProfile> payableProfiles) {
        try {
            logInfo("onProfileList - received " + payableProfiles.size() + " profiles");
            
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
        } catch (Exception e) {
            logError("Error processing profile list callback: " + e.getMessage(), e);
            if (activeCall != null) {
                activeCall.reject("Error processing profile list: " + e.getMessage());
                activeCall = null;
            }
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
        logInfo("Plugin destroying, cleaning up resources...");
        try {
            if (payableClient != null) {
                payableClient.unregisterEventListener();
                payableClient.unregisterProgressListener();
            }
        } catch (Exception e) {
            logError("Error during cleanup: " + e.getMessage(), e);
        }
        instance = null; // Clean up static reference
    }

    // Helper to ensure we don't overwrite an active call
    private boolean setCall(PluginCall call) {
        if (activeCall != null) {
            logWarning("Rejected call: Another operation is already in progress");
            call.reject("Another operation is already in progress");
            return false;
        }
        activeCall = call;
        return true;
    }

    /**
     * Get the log file path (exposed to React for debugging)
     * Usage: Payable.getLogFilePath()
     */
    @PluginMethod
    public void getLogFilePath(PluginCall call) {
        try {
            if (logFile != null && logFile.exists()) {
                JSObject ret = new JSObject();
                ret.put("path", logFile.getAbsolutePath());
                ret.put("exists", true);
                call.resolve(ret);
            } else {
                JSObject ret = new JSObject();
                ret.put("path", null);
                ret.put("exists", false);
                call.resolve(ret);
            }
        } catch (Exception e) {
            call.reject("Error getting log file path: " + e.getMessage());
        }
    }

    /**
     * Clear the log file (exposed to React for maintenance)
     * Usage: Payable.clearLogs()
     */
    @PluginMethod
    public void clearLogs(PluginCall call) {
        try {
            if (logFile != null && logFile.exists()) {
                FileWriter fw = new FileWriter(logFile, false); // overwrite mode
                fw.write("");
                fw.close();
                logInfo("Logs cleared");
                
                JSObject ret = new JSObject();
                ret.put("success", true);
                call.resolve(ret);
            } else {
                call.reject("Log file does not exist");
            }
        } catch (Exception e) {
            call.reject("Error clearing logs: " + e.getMessage());
        }
    }
}