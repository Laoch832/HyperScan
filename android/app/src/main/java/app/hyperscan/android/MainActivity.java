package app.hyperscan.android;

import com.getcapacitor.BridgeActivity;
import android.os.Bundle;

public class MainActivity extends BridgeActivity {
    @Override public void onCreate(Bundle state) {
        registerPlugin(OfflineExportPlugin.class);
        super.onCreate(state);
    }
}
