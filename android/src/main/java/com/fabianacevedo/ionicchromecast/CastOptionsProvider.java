package com.fabianacevedo.ionicchromecast;

import android.content.Context;
import android.content.SharedPreferences;
import com.google.android.gms.cast.framework.CastOptions;
import com.google.android.gms.cast.framework.OptionsProvider;
import com.google.android.gms.cast.framework.SessionProvider;
import com.google.android.gms.cast.CastMediaControlIntent;
import java.util.List;

/**
 * OptionsProvider for Google Cast SDK
 * This class is required by the Cast SDK to provide configuration options
 */
public class CastOptionsProvider implements OptionsProvider {
    
    // Default Media Receiver App ID (Google's default receiver)
    private static final String DEFAULT_RECEIVER_APP_ID = "CC1AD845";
    private static final String PREFS_NAME = "IonicChromecastPrefs";
    private static final String KEY_RECEIVER_APP_ID = "receiverApplicationId";
    
    // Static variable to hold the receiver app ID before CastContext is initialized
    public static String sReceiverApplicationId = null;
    
    @Override
    public CastOptions getCastOptions(Context context) {
        String receiverAppId = DEFAULT_RECEIVER_APP_ID;
        
        // Try to get from static variable first (set during initialize)
        if (sReceiverApplicationId != null && !sReceiverApplicationId.isEmpty()) {
            receiverAppId = sReceiverApplicationId;
        } else {
            // Fallback to SharedPreferences
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            String savedId = prefs.getString(KEY_RECEIVER_APP_ID, null);
            if (savedId != null && !savedId.isEmpty()) {
                receiverAppId = savedId;
            }
        }
        
        return new CastOptions.Builder()
            .setReceiverApplicationId(receiverAppId)
            .build();
    }
    
    @Override
    public List<SessionProvider> getAdditionalSessionProviders(Context context) {
        return null;
    }
}
