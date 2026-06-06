package com.mowtiie.faithfully.helper;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Build;

public class NetworkHelper {

    public static boolean isOffline(Context context) {
        if (context == null) {
            return true;
        }

        ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);

        if (connectivityManager == null) {
            return true;
        }

        Network activeNetwork = connectivityManager.getActiveNetwork();
        if (activeNetwork == null) {
            return true;
        }

        NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(activeNetwork);
        if (capabilities == null) {
            return true;
        }

        return !(capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET));
    }
}