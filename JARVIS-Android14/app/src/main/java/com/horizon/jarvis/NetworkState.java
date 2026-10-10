package com.horizon.jarvis;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;

/** Android-validated default network, independent of Wi-Fi versus cellular. */
final class NetworkState {
    private NetworkState() {}
    static boolean hasValidatedInternet(Context context) {
        try {
            ConnectivityManager manager=(ConnectivityManager)
                context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (manager==null) return false;
            Network active=manager.getActiveNetwork();
            if (active==null) return false;
            NetworkCapabilities caps=manager.getNetworkCapabilities(active);
            return caps!=null
                && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
        } catch (SecurityException ignored) {
            return false;
        }
    }
}
