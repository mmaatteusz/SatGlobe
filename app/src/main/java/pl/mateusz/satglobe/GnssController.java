package pl.mateusz.satglobe;

import android.annotation.SuppressLint;
import android.content.Context;
import android.location.GnssStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Owns foreground-only Android GNSS subscriptions.
 */
public final class GnssController {
    public interface Listener {
        void onSatellitesChanged(List<SatelliteInfo> satellites);

        void onLocationChanged(Location location);

        void onGnssStateChanged(State state, String detail);

        void onFirstFix(int timeToFirstFixMillis);
    }

    public enum State {
        STARTING,
        ACTIVE,
        STOPPED,
        PROVIDER_DISABLED,
        UNSUPPORTED,
        ERROR
    }

    private final LocationManager locationManager;
    private final Listener listener;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private boolean registered;

    public GnssController(Context context, Listener listener) {
        this.locationManager =
                (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        this.listener = listener;
    }

    private final GnssStatus.Callback gnssCallback = new GnssStatus.Callback() {
        @Override
        public void onStarted() {
            listener.onGnssStateChanged(State.ACTIVE, "Odbiornik GNSS aktywny");
        }

        @Override
        public void onStopped() {
            listener.onGnssStateChanged(State.STOPPED, "Odbiornik GNSS zatrzymany");
        }

        @Override
        public void onFirstFix(int ttffMillis) {
            listener.onFirstFix(ttffMillis);
        }

        @Override
        public void onSatelliteStatusChanged(GnssStatus status) {
            ArrayList<SatelliteInfo> satellites = new ArrayList<>(status.getSatelliteCount());
            for (int index = 0; index < status.getSatelliteCount(); index++) {
                Float carrier = null;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                        && status.hasCarrierFrequencyHz(index)) {
                    carrier = status.getCarrierFrequencyHz(index);
                }
                satellites.add(new SatelliteInfo(
                        status.getConstellationType(index),
                        status.getSvid(index),
                        status.getCn0DbHz(index),
                        status.getElevationDegrees(index),
                        status.getAzimuthDegrees(index),
                        status.usedInFix(index),
                        status.hasAlmanacData(index),
                        status.hasEphemerisData(index),
                        carrier
                ));
            }

            satellites.sort(
                    Comparator.comparing((SatelliteInfo item) -> !item.usedInFix)
                            .thenComparing((SatelliteInfo item) -> -item.cn0DbHz)
                            .thenComparingInt(item -> item.constellation)
                            .thenComparingInt(item -> item.svid)
            );
            listener.onSatellitesChanged(
                    Collections.unmodifiableList(new ArrayList<>(satellites))
            );
        }
    };

    private final LocationListener locationListener = new LocationListener() {
        @Override
        public void onLocationChanged(Location location) {
            listener.onLocationChanged(location);
        }

        @Override
        public void onProviderEnabled(String provider) {
            listener.onGnssStateChanged(State.STARTING, "Oczekiwanie na sygnał satelitów");
        }

        @Override
        public void onProviderDisabled(String provider) {
            listener.onGnssStateChanged(
                    State.PROVIDER_DISABLED,
                    "Lokalizacja systemowa jest wyłączona"
            );
        }

        @Deprecated
        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {
            // Required only by old Android releases.
        }
    };

    @SuppressLint("MissingPermission")
    public void start() {
        if (registered) {
            return;
        }
        if (locationManager == null) {
            listener.onGnssStateChanged(State.UNSUPPORTED, "Brak usługi lokalizacji");
            return;
        }
        if (!locationManager.getAllProviders().contains(LocationManager.GPS_PROVIDER)) {
            listener.onGnssStateChanged(State.UNSUPPORTED, "Ten telefon nie ma odbiornika GPS");
            return;
        }
        if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            listener.onGnssStateChanged(
                    State.PROVIDER_DISABLED,
                    "Włącz lokalizację w ustawieniach telefonu"
            );
            return;
        }

        try {
            listener.onGnssStateChanged(State.STARTING, "Uruchamiam odbiornik GNSS…");
            boolean callbackRegistered =
                    locationManager.registerGnssStatusCallback(gnssCallback, mainHandler);
            if (!callbackRegistered) {
                listener.onGnssStateChanged(
                        State.ERROR,
                        "System nie uruchomił nasłuchu satelitów"
                );
                return;
            }
            locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    1_000L,
                    0f,
                    locationListener,
                    Looper.getMainLooper()
            );
            Location lastLocation =
                    locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if (lastLocation != null
                    && System.currentTimeMillis() - lastLocation.getTime() < 10 * 60_000L) {
                listener.onLocationChanged(lastLocation);
            }
            registered = true;
        } catch (SecurityException securityException) {
            listener.onGnssStateChanged(
                    State.ERROR,
                    "Brak zgody na dokładną lokalizację"
            );
        } catch (RuntimeException runtimeException) {
            listener.onGnssStateChanged(
                    State.ERROR,
                    "Błąd odbiornika: " + runtimeException.getClass().getSimpleName()
            );
        }
    }

    public void stop() {
        if (!registered || locationManager == null) {
            return;
        }
        try {
            locationManager.unregisterGnssStatusCallback(gnssCallback);
            locationManager.removeUpdates(locationListener);
        } catch (RuntimeException ignored) {
            // The platform may already have removed callbacks during process teardown.
        } finally {
            registered = false;
        }
    }
}
