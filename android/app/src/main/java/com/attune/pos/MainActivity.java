package com.attune.pos;
import android.os.Bundle;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(PayablePlugin.class);
        super.onCreate(savedInstanceState);
    }
}