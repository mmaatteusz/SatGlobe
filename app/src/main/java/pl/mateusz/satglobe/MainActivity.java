package pl.mateusz.satglobe;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Single-screen, foreground-only satellite dashboard.
 */
public final class MainActivity extends Activity implements GnssController.Listener {
    private static final int LOCATION_PERMISSION_REQUEST = 3201;
    private static final String STATE_USER_STARTED = "user_started";

    private GlobeSurfaceView globeView;
    private View permissionPanel;
    private View dataPanel;
    private TextView permissionTitle;
    private TextView permissionMessage;
    private Button startButton;
    private TextView liveBadge;
    private TextView topStatus;
    private TextView visibleCount;
    private TextView usedCount;
    private TextView averageSignal;
    private TextView locationText;
    private TextView gnssStateText;
    private LinearLayout satelliteChips;
    private GnssController gnssController;

    private boolean userStarted;
    private boolean settingsButtonMode;
    private String selectedSatelliteKey;
    private List<SatelliteInfo> latestSatellites = new ArrayList<>();
    private int timeToFirstFixMillis = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        configureWindow();
        setContentView(R.layout.activity_main);

        globeView = findViewById(R.id.globeView);
        permissionPanel = findViewById(R.id.permissionPanel);
        dataPanel = findViewById(R.id.dataPanel);
        permissionTitle = findViewById(R.id.permissionTitle);
        permissionMessage = findViewById(R.id.permissionMessage);
        startButton = findViewById(R.id.startButton);
        liveBadge = findViewById(R.id.liveBadge);
        topStatus = findViewById(R.id.topStatus);
        visibleCount = findViewById(R.id.visibleCount);
        usedCount = findViewById(R.id.usedCount);
        averageSignal = findViewById(R.id.averageSignal);
        locationText = findViewById(R.id.locationText);
        gnssStateText = findViewById(R.id.gnssStateText);
        satelliteChips = findViewById(R.id.satelliteChips);
        gnssController = new GnssController(this, this);

        if (savedInstanceState != null) {
            userStarted = savedInstanceState.getBoolean(STATE_USER_STARTED, false);
        }
        startButton.setOnClickListener(view -> {
            if (settingsButtonMode) {
                openApplicationSettings();
            } else {
                userStarted = true;
                requestPermissionOrStart();
            }
        });
        gnssStateText.setOnClickListener(view -> {
            if (view.getTag() == GnssController.State.PROVIDER_DISABLED) {
                startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
            }
        });
    }

    private void configureWindow() {
        Window window = getWindow();
        window.setStatusBarColor(Color.rgb(5, 8, 18));
        window.setNavigationBarColor(Color.rgb(5, 8, 18));
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(STATE_USER_STARTED, userStarted);
    }

    @Override
    protected void onResume() {
        super.onResume();
        globeView.onResume();
        if (userStarted && hasPreciseLocationPermission()) {
            showDataInterface();
            gnssController.start();
        }
    }

    @Override
    protected void onPause() {
        gnssController.stop();
        liveBadge.setVisibility(View.GONE);
        globeView.onPause();
        super.onPause();
    }

    private void requestPermissionOrStart() {
        if (hasPreciseLocationPermission()) {
            showDataInterface();
            gnssController.start();
            return;
        }
        settingsButtonMode = false;
        requestPermissions(
                new String[]{
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                },
                LOCATION_PERMISSION_REQUEST
        );
    }

    private boolean hasPreciseLocationPermission() {
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            String[] permissions,
            int[] grantResults
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != LOCATION_PERMISSION_REQUEST) {
            return;
        }
        if (hasPreciseLocationPermission()) {
            settingsButtonMode = false;
            showDataInterface();
            gnssController.start();
            return;
        }

        boolean canAskAgain =
                shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION);
        showPermissionFailure(!canAskAgain);
    }

    private void showPermissionFailure(boolean permanentlyDenied) {
        permissionPanel.setVisibility(View.VISIBLE);
        dataPanel.setVisibility(View.GONE);
        liveBadge.setVisibility(View.GONE);
        permissionTitle.setText("Potrzebna jest dokładna lokalizacja");
        permissionMessage.setText(
                permanentlyDenied
                        ? "Android zablokował kolejne pytania. W ustawieniach aplikacji wybierz "
                        + "„Lokalizacja” i włącz dokładną pozycję podczas używania."
                        : getString(R.string.permission_required)
                        + " Bez niej Android nie udostępnia statusu GNSS."
        );
        settingsButtonMode = permanentlyDenied;
        startButton.setText(
                permanentlyDenied ? R.string.open_settings : R.string.start_button
        );
    }

    private void showDataInterface() {
        permissionPanel.setVisibility(View.GONE);
        dataPanel.setVisibility(View.VISIBLE);
        liveBadge.setVisibility(View.VISIBLE);
        settingsButtonMode = false;
        topStatus.setText("Obróć glob palcem · podwójne dotknięcie resetuje widok");
    }

    private void openApplicationSettings() {
        Intent intent = new Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", getPackageName(), null)
        );
        startActivity(intent);
    }

    @Override
    public void onSatellitesChanged(List<SatelliteInfo> satellites) {
        latestSatellites = new ArrayList<>(satellites);
        globeView.setSatellites(latestSatellites);

        int used = 0;
        float signalSum = 0f;
        int signalCount = 0;
        Set<Integer> constellations = new HashSet<>();
        for (SatelliteInfo satellite : latestSatellites) {
            if (satellite.usedInFix) {
                used++;
            }
            if (satellite.cn0DbHz > 0f) {
                signalSum += satellite.cn0DbHz;
                signalCount++;
            }
            constellations.add(satellite.constellation);
        }

        visibleCount.setText(String.valueOf(latestSatellites.size()));
        usedCount.setText(String.valueOf(used));
        averageSignal.setText(
                signalCount == 0
                        ? "—"
                        : String.format(Locale.getDefault(), "%.0f", signalSum / signalCount)
        );
        if (selectedSatelliteKey == null) {
            String ttff = timeToFirstFixMillis >= 0
                    ? String.format(
                            Locale.getDefault(),
                            " · pierwszy fix %.1f s",
                            timeToFirstFixMillis / 1000f
                    )
                    : "";
            gnssStateText.setText(
                    latestSatellites.size()
                            + " satelitów · "
                            + constellations.size()
                            + " konstelacji"
                            + ttff
            );
        }
        renderSatelliteChips();
    }

    @Override
    public void onLocationChanged(Location location) {
        globeView.setReceiverLocation(location.getLatitude(), location.getLongitude());
        String accuracy = location.hasAccuracy()
                ? String.format(Locale.getDefault(), " · ±%.0f m", location.getAccuracy())
                : "";
        locationText.setText(
                String.format(
                        Locale.getDefault(),
                        "Pozycja %.5f°, %.5f°%s",
                        location.getLatitude(),
                        location.getLongitude(),
                        accuracy
                )
        );
    }

    @Override
    public void onGnssStateChanged(GnssController.State state, String detail) {
        gnssStateText.setTag(state);
        if (state == GnssController.State.PROVIDER_DISABLED) {
            gnssStateText.setText(detail + " · dotknij, aby otworzyć ustawienia");
            gnssStateText.setTextColor(getColor(R.color.warning));
            liveBadge.setVisibility(View.GONE);
        } else {
            gnssStateText.setText(detail);
            gnssStateText.setTextColor(getColor(R.color.text_secondary));
            liveBadge.setVisibility(
                    state == GnssController.State.ACTIVE
                            || state == GnssController.State.STARTING
                            ? View.VISIBLE
                            : View.GONE
            );
        }
    }

    @Override
    public void onFirstFix(int timeToFirstFixMillis) {
        this.timeToFirstFixMillis = timeToFirstFixMillis;
        if (selectedSatelliteKey == null) {
            gnssStateText.setText(
                    String.format(
                            Locale.getDefault(),
                            "Pozycja ustalona po %.1f s",
                            timeToFirstFixMillis / 1000f
                    )
            );
        }
    }

    private void renderSatelliteChips() {
        satelliteChips.removeAllViews();
        if (latestSatellites.isEmpty()) {
            TextView empty = createChipTextView();
            empty.setText("Czekam na satelity…");
            empty.setTextColor(getColor(R.color.text_secondary));
            empty.setBackground(chipBackground(0xFF5B7088, false));
            satelliteChips.addView(empty);
            return;
        }

        int limit = Math.min(18, latestSatellites.size());
        for (int index = 0; index < limit; index++) {
            SatelliteInfo satellite = latestSatellites.get(index);
            boolean selected = satellite.key().equals(selectedSatelliteKey);
            TextView chip = createChipTextView();
            String fixSuffix = satellite.usedInFix ? " · FIX" : "";
            chip.setText(
                    satellite.shortLabel()
                            + "  "
                            + satellite.constellationName()
                            + "\n"
                            + String.format(
                            Locale.getDefault(),
                            "%.0f dB-Hz · %.0f°%s",
                            satellite.cn0DbHz,
                            satellite.elevationDegrees,
                            fixSuffix
                    )
            );
            chip.setTextColor(selected ? Color.WHITE : satellite.colorArgb());
            chip.setBackground(chipBackground(satellite.colorArgb(), selected));
            chip.setContentDescription(
                    satellite.constellationName()
                            + " "
                            + satellite.svid
                            + ", sygnał "
                            + Math.round(satellite.cn0DbHz)
                            + " decybeli herców"
            );
            chip.setOnClickListener(view -> selectSatellite(satellite));
            satelliteChips.addView(chip);
        }
    }

    private TextView createChipTextView() {
        TextView chip = new TextView(this);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMarginEnd(dp(8));
        chip.setLayoutParams(params);
        chip.setPadding(dp(12), dp(9), dp(12), dp(9));
        chip.setTextSize(11f);
        chip.setLineSpacing(0f, 1.08f);
        chip.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        chip.setFocusable(true);
        chip.setClickable(true);
        return chip;
    }

    private GradientDrawable chipBackground(int color, boolean selected) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setCornerRadius(dp(14));
        int red = Color.red(color);
        int green = Color.green(color);
        int blue = Color.blue(color);
        drawable.setColor(Color.argb(selected ? 62 : 28, red, green, blue));
        drawable.setStroke(dp(selected ? 2 : 1), Color.argb(150, red, green, blue));
        return drawable;
    }

    private void selectSatellite(SatelliteInfo satellite) {
        selectedSatelliteKey = satellite.key();
        globeView.setSelectedSatellite(selectedSatelliteKey);
        gnssStateText.setText(
                String.format(
                        Locale.getDefault(),
                        "%s %d · azymut %.0f° · elewacja %.0f° · %.1f dB-Hz%s",
                        satellite.constellationName(),
                        satellite.svid,
                        satellite.azimuthDegrees,
                        satellite.elevationDegrees,
                        satellite.cn0DbHz,
                        satellite.usedInFix ? " · używany w pozycji" : ""
                )
        );
        renderSatelliteChips();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
