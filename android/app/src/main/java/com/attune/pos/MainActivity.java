package com.attune.pos;

import android.content.Intent; // Import needed for Intent data
import android.os.Bundle;
import com.getcapacitor.BridgeActivity;

// Import your plugin class so we can call the static method
import com.attune.pos.PayablePlugin;

public class MainActivity extends BridgeActivity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        // Register the plugins so Capacitor knows they exist
        registerPlugin(PayablePlugin.class);
        registerPlugin(BlePlugin.class);
        super.onCreate(savedInstanceState);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // 1. Let Capacitor handle its internal stuff (camera, file picker, etc.)
        super.onActivityResult(requestCode, resultCode, data);

        // 2. Manually pass the result to your Payable Plugin
        // This ensures 'payableClient.handleResponse' gets called
        PayablePlugin.processActivityResult(requestCode, resultCode, data);
    }
}