package com.fabianacevedo.ionicchromecast;

import android.content.Context;
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
    
    // Default Media Receiver App ID
    private static final String DEFAULT_RECEIVER_APP_ID = "CC1AD845";
    
    @Override
    public CastOptions getCastOptions(Context context) {
        // Use default receiver app ID initially
        // This will be overridden when initialize() is called
        return new CastOptions.Builder()
            .setReceiverApplicationId(DEFAULT_RECEIVER_APP_ID)
            .build();
    }
    
    @Override
    public List<SessionProvider> getAdditionalSessionProviders(Context context) {
        return null;
    }
}
