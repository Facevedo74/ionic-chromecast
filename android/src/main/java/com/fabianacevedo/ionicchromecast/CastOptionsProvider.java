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
 */
public class CastOptionsProvider implements OptionsProvider {

    private static final String DEFAULT_RECEIVER_APP_ID = "CC1AD845";
    private static final String PREFS_NAME = "IonicChromecastPrefs";
    private static final String KEY_RECEIVER_APP_ID = "receiverApplicationId";

    public static String sReceiverApplicationId = null;

    @Override
    public CastOptions getCastOptions(Context context) {
        String receiverAppId = DEFAULT_RECEIVER_APP_ID;

        // Prefer static variable set by initialize()
        if (sReceiverApplicationId != null && !sReceiverApplicationId.isEmpty()) {
            receiverAppId = sReceiverApplicationId;
        } else {
            // Try from SharedPreferences
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            String savedId = prefs.getString(KEY_RECEIVER_APP_ID, null);
            if (savedId != null && !savedId.isEmpty()) {
                receiverAppId = savedId;
            }
        }

        return new CastOptions.Builder()
            .setReceiverApplicationId(receiverAppId)
            .setStopReceiverApplicationWhenEndingSession(true)
            .setResumeSavedSession(false)
            .build();
    }

    @Override
    public List<SessionProvider> getAdditionalSessionProviders(Context context) {
        return null;
    }
}
