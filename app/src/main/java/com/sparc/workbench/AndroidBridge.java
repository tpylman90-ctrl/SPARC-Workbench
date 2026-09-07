package org.sparcworkbench.app;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

public class AndroidBridge {

    private final Activity activity;
    private final WebView webView;
    private final SharedPreferences prefs;

    private static final String PREFS_NAME =
            "sparc_workbench_preferences";

    private static final String KEY_SESSION =
            "session_json";

    private static final String KEY_LAST_UPDATE_CHECK =
            "last_update_check_ms";

    private static final String KEY_META_REMOTE_LENGTH =
            "meta_remote_length";

    private static final String KEY_MASS_REMOTE_LENGTH =
            "mass_remote_length";

    private static final String KEY_META_REMOTE_MODIFIED =
            "meta_remote_modified";

    private static final String KEY_MASS_REMOTE_MODIFIED =
            "mass_remote_modified";

    private static final long UPDATE_CHECK_INTERVAL_MS =
            24L * 60L * 60L * 1000L;

    private static final String META_URL =
            "https://astroweb.cwru.edu/SPARC/SPARC_Lelli2016c.mrt";

    private static final String MASS_URL =
            "https://astroweb.cwru.edu/SPARC/MassModels_Lelli2016c.mrt";

    public AndroidBridge(Activity activity, WebView webView) {
        this.activity = activity;
        this.webView = webView;

        this.prefs =
                activity.getSharedPreferences(
                        PREFS_NAME,
                        Activity.MODE_PRIVATE
                );
    }

    // ============================================================
    // STARTUP BOOTSTRAP
    // ============================================================

    @JavascriptInterface
    public void bootstrapApp() {

        new Thread(() -> {

            JSONObject result =
                    new JSONObject();

            try {

                result.put(
                        "ok",
                        true
                );

                result.put(
                        "stage",
                        "initializing"
                );

                File dir =
                        sparcDirectory();

                if (!dir.exists() &&
                        !dir.mkdirs()) {

                    throw new Exception(
                            "Could not create SPARC data directory."
                    );
                }

                File meta =
                        metadataFile();

                File mass =
                        massModelFile();

                boolean hadMeta =
                        validCachedFile(meta);

                boolean hadMass =
                        validCachedFile(mass);

                boolean downloaded =
                        false;

                boolean updated =
                        false;

                boolean checkedRemote =
                        false;

                boolean updateCheckFailed =
                        false;

                // ------------------------------------------------
                // First launch / missing cache
                // ------------------------------------------------

                if (!hadMeta) {

                    downloadFile(
                            META_URL,
                            meta
                    );

                    downloaded = true;
                }

                if (!hadMass) {

                    downloadFile(
                            MASS_URL,
                            mass
                    );

                    downloaded = true;
                }

                // ------------------------------------------------
                // Periodic remote update check
                // ------------------------------------------------

                long now =
                        System.currentTimeMillis();

                long lastCheck =
                        prefs.getLong(
                                KEY_LAST_UPDATE_CHECK,
                                0L
                        );

                boolean updateCheckDue =
                        now - lastCheck >=
                                UPDATE_CHECK_INTERVAL_MS;

                if (updateCheckDue &&
                        validCachedFile(meta) &&
                        validCachedFile(mass)) {

                    checkedRemote = true;

                    try {

                        boolean metaUpdated =
                                updateIfRemoteChanged(
                                        META_URL,
                                        meta,
                                        KEY_META_REMOTE_LENGTH,
                                        KEY_META_REMOTE_MODIFIED
                                );

                        boolean massUpdated =
                                updateIfRemoteChanged(
                                        MASS_URL,
                                        mass,
                                        KEY_MASS_REMOTE_LENGTH,
                                        KEY_MASS_REMOTE_MODIFIED
                                );

                        updated =
                                metaUpdated ||
                                massUpdated;

                        prefs.edit()
                                .putLong(
                                        KEY_LAST_UPDATE_CHECK,
                                        now
                                )
                                .apply();

                    } catch (Exception ignored) {

                        /*
                         * Startup should still succeed when offline
                         * as long as valid cached data exists.
                         */

                        updateCheckFailed =
                                true;
                    }
                }

                if (!validCachedFile(meta) ||
                        !validCachedFile(mass)) {

                    throw new Exception(
                            "SPARC cache is incomplete."
                    );
                }

                JSONArray catalog =
                        new JSONArray(
                                getGalaxyCatalog()
                        );

                JSONObject session =
                        getStoredSessionObject();

                String dataState;

                if (updated) {

                    dataState =
                            "updated";

                } else if (downloaded) {

                    dataState =
                            "downloaded";

                } else {

                    dataState =
                            "cached";
                }

                result.put(
                        "stage",
                        "ready"
                );

                result.put(
                        "data_state",
                        dataState
                );

                result.put(
                        "checked_remote",
                        checkedRemote
                );

                result.put(
                        "update_check_failed",
                        updateCheckFailed
                );

                result.put(
                        "catalog_count",
                        catalog.length()
                );

                result.put(
                        "metadata_bytes",
                        meta.length()
                );

                result.put(
                        "mass_model_bytes",
                        mass.length()
                );

                result.put(
                        "session",
                        session
                );

                result.put(
                        "last_update_check_ms",
                        prefs.getLong(
                                KEY_LAST_UPDATE_CHECK,
                                0L
                        )
                );

            } catch (Exception e) {

                try {

                    result.put(
                            "ok",
                            false
                    );

                    result.put(
                            "stage",
                            "error"
                    );

                    result.put(
                            "error",
                            e.toString()
                    );

                } catch (Exception ignored) {
                }
            }

            sendToJs(
                    "onBootstrapComplete",
                    result.toString()
            );
        }).start();
    }

    // ============================================================
    // MANUAL DATA REFRESH
    // ============================================================

    @JavascriptInterface
    public void loadSparcData() {

        new Thread(() -> {

            try {

                File dir =
                        sparcDirectory();

                if (!dir.exists() &&
                        !dir.mkdirs()) {

                    throw new Exception(
                            "Could not create SPARC data directory."
                    );
                }

                File meta =
                        metadataFile();

                File mass =
                        massModelFile();

                downloadIfNeeded(
                        META_URL,
                        meta
                );

                downloadIfNeeded(
                        MASS_URL,
                        mass
                );

                JSONObject result =
                        new JSONObject();

                result.put(
                        "ok",
                        true
                );

                result.put(
                        "metadataBytes",
                        meta.length()
                );

                result.put(
                        "massModelBytes",
                        mass.length()
                );

                sendToJs(
                        "onSparcLoaded",
                        result.toString()
                );

            } catch (Exception e) {

                try {

                    JSONObject result =
                            new JSONObject();

                    result.put(
                            "ok",
                            false
                    );

                    result.put(
                            "error",
                            e.toString()
                    );

                    sendToJs(
                            "onSparcLoaded",
                            result.toString()
                    );

                } catch (Exception ignored) {
                }
            }
        }).start();
    }

    // ============================================================
    // SESSION / USER SETTINGS
    // ============================================================

    @JavascriptInterface
    public String saveSessionState(
            String json
    ) {

        try {

            JSONObject state =
                    new JSONObject(
                            json
                    );

            prefs.edit()
                    .putString(
                            KEY_SESSION,
                            state.toString()
                    )
                    .apply();

            return "OK";

        } catch (Exception e) {

            return "ERROR: " + e;
        }
    }

    @JavascriptInterface
    public String getSessionState() {

        return getStoredSessionObject()
                .toString();
    }

    @JavascriptInterface
    public String clearSessionState() {

        prefs.edit()
                .remove(
                        KEY_SESSION
                )
                .apply();

        return "OK";
    }

    private JSONObject getStoredSessionObject() {

        try {

            String stored =
                    prefs.getString(
                            KEY_SESSION,
                            "{}"
                    );

            if (stored == null ||
                    stored.trim().isEmpty()) {

                return new JSONObject();
            }

            return new JSONObject(
                    stored
            );

        } catch (Exception ignored) {

            return new JSONObject();
        }
    }

    // ============================================================
    // SAVED EXPORT PRESET STORAGE FOUNDATION
    // ============================================================

    @JavascriptInterface
    public String saveExportPreset(
            String presetName,
            String styleJson
    ) {

        try {

            String safeName =
                    presetName == null
                            ? ""
                            : presetName.trim();

            if (safeName.isEmpty()) {

                return "ERROR: Preset name is required.";
            }

            JSONObject style =
                    new JSONObject(
                            styleJson
                    );

            prefs.edit()
                    .putString(
                            "export_preset_" +
                                    safeName,
                            style.toString()
                    )
                    .apply();

            return "OK";

        } catch (Exception e) {

            return "ERROR: " + e;
        }
    }

    @JavascriptInterface
    public String getExportPreset(
            String presetName
    ) {

        String stored =
                prefs.getString(
                        "export_preset_" +
                                presetName,
                        ""
                );

        return stored == null
                ? ""
                : stored;
    }

    @JavascriptInterface
    public String deleteExportPreset(
            String presetName
    ) {

        prefs.edit()
                .remove(
                        "export_preset_" +
                                presetName
                )
                .apply();

        return "OK";
    }

    @JavascriptInterface
    public String listSavedExportPresets() {

        JSONArray arr =
                new JSONArray();

        try {

            for (String key :
                    prefs.getAll()
                            .keySet()) {

                if (key.startsWith(
                        "export_preset_"
                )) {

                    arr.put(
                            key.substring(
                                    "export_preset_"
                                            .length()
                            )
                    );
                }
            }

        } catch (Exception ignored) {
        }

        return arr.toString();
    }

    // ============================================================
    // METADATA
    // ============================================================

    private JSONObject parseMetadataRow(
            String line
    ) {

        try {

            String[] p =
                    line.trim()
                            .split("\\s+");

            if (p.length < 18) {
                return null;
            }

            String galaxy =
                    p[0];

            int hubble =
                    Integer.parseInt(
                            p[1]
                    );

            double distance =
                    Double.parseDouble(
                            p[2]
                    );

            double distanceError =
                    Double.parseDouble(
                            p[3]
                    );

            int distanceMethod =
                    Integer.parseInt(
                            p[4]
                    );

            double inclination =
                    Double.parseDouble(
                            p[5]
                    );

            double inclinationError =
                    Double.parseDouble(
                            p[6]
                    );

            double luminosity36 =
                    Double.parseDouble(
                            p[7]
                    );

            double luminosityError =
                    Double.parseDouble(
                            p[8]
                    );

            double effectiveRadius =
                    Double.parseDouble(
                            p[9]
                    );

            double effectiveSurfaceBrightness =
                    Double.parseDouble(
                            p[10]
                    );

            double diskScaleLength =
                    Double.parseDouble(
                            p[11]
                    );

            double diskCentralSurfaceBrightness =
                    Double.parseDouble(
                            p[12]
                    );

            double mhi =
                    Double.parseDouble(
                            p[13]
                    );

            double rhi =
                    Double.parseDouble(
                            p[14]
                    );

            double vflat =
                    Double.parseDouble(
                            p[15]
                    );

            double vflatError =
                    Double.parseDouble(
                            p[16]
                    );

            int quality =
                    Integer.parseInt(
                            p[17]
                    );

            if (galaxy.isEmpty()) return null;
            if (hubble < 0 || hubble > 11) return null;
            if (distance <= 0) return null;
            if (distanceError < 0) return null;
            if (distanceMethod < 1 || distanceMethod > 5) return null;
            if (inclination <= 0 || inclination > 90) return null;
            if (inclinationError < 0) return null;
            if (quality < 1 || quality > 3) return null;

            JSONObject obj =
                    new JSONObject();

            obj.put(
                    "galaxy",
                    galaxy
            );

            obj.put(
                    "hubble_type",
                    hubble
            );

            obj.put(
                    "distance_mpc",
                    distance
            );

            obj.put(
                    "distance_error_mpc",
                    distanceError
            );

            obj.put(
                    "distance_method",
                    distanceMethod
            );

            obj.put(
                    "inclination_deg",
                    inclination
            );

            obj.put(
                    "inclination_error_deg",
                    inclinationError
            );

            obj.put(
                    "luminosity_36",
                    luminosity36
            );

            obj.put(
                    "luminosity_error",
                    luminosityError
            );

            obj.put(
                    "effective_radius_kpc",
                    effectiveRadius
            );

            obj.put(
                    "effective_surface_brightness",
                    effectiveSurfaceBrightness
            );

            obj.put(
                    "disk_scale_length_kpc",
                    diskScaleLength
            );

            obj.put(
                    "disk_central_surface_brightness",
                    diskCentralSurfaceBrightness
            );

            obj.put(
                    "mhi",
                    mhi
            );

            obj.put(
                    "rhi_kpc",
                    rhi
            );

            obj.put(
                    "vflat_kms",
                    vflat
            );

            obj.put(
                    "vflat_error_kms",
                    vflatError
            );

            obj.put(
                    "quality",
                    quality
            );

            return obj;

        } catch (Exception ignored) {

            return null;
        }
    }

    @JavascriptInterface
    public String getGalaxyCatalog() {

        JSONArray result =
                new JSONArray();

        try {

            File file =
                    metadataFile();

            if (!file.exists()) {

                return result.toString();
            }

            LinkedHashSet<String> galaxies =
                    new LinkedHashSet<>();

            try (
                    BufferedReader reader =
                            readerFor(file)
            ) {

                String line;

                while ((line =
                        reader.readLine()) != null) {

                    JSONObject meta =
                            parseMetadataRow(
                                    line
                            );

                    if (meta != null) {

                        galaxies.add(
                                meta.getString(
                                        "galaxy"
                                )
                        );
                    }
                }
            }

            for (String galaxy :
                    galaxies) {

                result.put(
                        galaxy
                );
            }

        } catch (Exception ignored) {
        }

        return result.toString();
    }

    @JavascriptInterface
    public String getGalaxyMetadata(
            String galaxyName
    ) {

        try {

            File file =
                    metadataFile();

            if (!file.exists()) {

                return "{}";
            }

            try (
                    BufferedReader reader =
                            readerFor(file)
            ) {

                String line;

                while ((line =
                        reader.readLine()) != null) {

                    JSONObject meta =
                            parseMetadataRow(
                                    line
                            );

                    if (meta != null &&
                            galaxyName.equals(
                                    meta.optString(
                                            "galaxy"
                                    )
                            )) {

                        return meta.toString();
                    }
                }
            }

        } catch (Exception ignored) {
        }

        return "{}";
    }

    // ============================================================
    // ROTATION CURVES
    // ============================================================

    @JavascriptInterface
    public String getRotationCurve(
            String galaxyName
    ) {

        JSONArray rows =
                new JSONArray();

        try {

            File file =
                    massModelFile();

            if (!file.exists()) {

                return rows.toString();
            }

            try (
                    BufferedReader reader =
                            readerFor(file)
            ) {

                String line;

                while ((line =
                        reader.readLine()) != null) {

                    line =
                            line.trim();

                    if (line.isEmpty()) {
                        continue;
                    }

                    String[] p =
                            line.split("\\s+");

                    if (p.length < 10) {
                        continue;
                    }

                    if (!p[0].equals(
                            galaxyName
                    )) {

                        continue;
                    }

                    try {

                        JSONObject row =
                                new JSONObject();

                        row.put(
                                "galaxy",
                                p[0]
                        );

                        row.put(
                                "distance_mpc",
                                Double.parseDouble(
                                        p[1]
                                )
                        );

                        row.put(
                                "radius_kpc",
                                Double.parseDouble(
                                        p[2]
                                )
                        );

                        row.put(
                                "v_obs_kms",
                                Double.parseDouble(
                                        p[3]
                                )
                        );

                        row.put(
                                "v_err_kms",
                                Double.parseDouble(
                                        p[4]
                                )
                        );

                        row.put(
                                "v_gas_kms",
                                Double.parseDouble(
                                        p[5]
                                )
                        );

                        row.put(
                                "v_disk_kms",
                                Double.parseDouble(
                                        p[6]
                                )
                        );

                        row.put(
                                "v_bulge_kms",
                                Double.parseDouble(
                                        p[7]
                                )
                        );

                        row.put(
                                "sb_disk",
                                Double.parseDouble(
                                        p[8]
                                )
                        );

                        row.put(
                                "sb_bulge",
                                Double.parseDouble(
                                        p[9]
                                )
                        );

                        rows.put(
                                row
                        );

                    } catch (Exception ignored) {
                    }
                }
            }

        } catch (Exception ignored) {
        }

        return rows.toString();
    }

    // ============================================================
    // DIAGNOSTICS
    // ============================================================

    @JavascriptInterface
    public String getDiagnostics(
            String galaxyName
    ) {

        JSONObject out =
                new JSONObject();

        try {

            JSONArray curve =
                    new JSONArray(
                            getRotationCurve(
                                    galaxyName
                            )
                    );

            JSONObject meta =
                    new JSONObject(
                            getGalaxyMetadata(
                                    galaxyName
                            )
                    );

            int n =
                    curve.length();

            out.put(
                    "galaxy",
                    galaxyName
            );

            out.put(
                    "point_count",
                    n
            );

            if (n == 0) {

                out.put(
                        "ok",
                        false
                );

                return out.toString();
            }

            double rMin =
                    curve.getJSONObject(0)
                            .getDouble(
                                    "radius_kpc"
                            );

            double rMax =
                    curve.getJSONObject(
                                    n - 1
                            )
                            .getDouble(
                                    "radius_kpc"
                            );

            double radialExtent =
                    rMax - rMin;

            double innerSlope =
                    segmentSlope(
                            curve,
                            0,
                            Math.max(
                                    1,
                                    n / 5
                            )
                    );

            double outerSlope =
                    segmentSlope(
                            curve,
                            Math.max(
                                    0,
                                    n -
                                            Math.max(
                                                    3,
                                                    n / 4
                                            )
                            ),
                            n - 1
                    );

            List<Double> fracErrors =
                    new ArrayList<>();

            boolean bulgePresent =
                    false;

            for (int i = 0;
                 i < n;
                 i++) {

                JSONObject row =
                        curve.getJSONObject(i);

                double v =
                        row.getDouble(
                                "v_obs_kms"
                        );

                double e =
                        row.getDouble(
                                "v_err_kms"
                        );

                double vb =
                        row.getDouble(
                                "v_bulge_kms"
                        );

                if (v > 0 &&
                        e >= 0) {

                    fracErrors.add(
                            e / v
                    );
                }

                if (Math.abs(vb) >
                        0.01) {

                    bulgePresent =
                            true;
                }
            }

            double medianFracError =
                    median(
                            fracErrors
                    );

            JSONObject last =
                    curve.getJSONObject(
                            n - 1
                    );

            double vObsOuter =
                    last.getDouble(
                            "v_obs_kms"
                    );

            double vGas =
                    last.getDouble(
                            "v_gas_kms"
                    );

            double vDisk =
                    last.getDouble(
                            "v_disk_kms"
                    );

            double vBulge =
                    last.getDouble(
                            "v_bulge_kms"
                    );

            double vBarOuter =
                    Math.sqrt(
                            Math.max(
                                    0.0,
                                    signedSquare(
                                            vGas
                                    ) +
                                            signedSquare(
                                                    vDisk
                                            ) +
                                            signedSquare(
                                                    vBulge
                                            )
                            )
                    );

            double obsToBar =
                    vBarOuter > 0
                            ? vObsOuter /
                            vBarOuter
                            : Double.NaN;

            String outerClass;

            if (outerSlope > 1.0) {

                outerClass =
                        "rising";

            } else if (outerSlope < -1.0) {

                outerClass =
                        "declining";

            } else {

                outerClass =
                        "flat";
            }

            double vflat =
                    meta.optDouble(
                            "vflat_kms",
                            0.0
                    );

            double vflatResidual =
                    vflat > 0
                            ? vObsOuter -
                            vflat
                            : Double.NaN;

            out.put(
                    "ok",
                    true
            );

            out.put(
                    "r_min_kpc",
                    rMin
            );

            out.put(
                    "r_max_kpc",
                    rMax
            );

            out.put(
                    "radial_extent_kpc",
                    radialExtent
            );

            out.put(
                    "inner_slope_kms_per_kpc",
                    innerSlope
            );

            out.put(
                    "outer_slope_kms_per_kpc",
                    outerSlope
            );

            out.put(
                    "outer_class",
                    outerClass
            );

            out.put(
                    "median_fractional_velocity_error",
                    medianFracError
            );

            out.put(
                    "bulge_present",
                    bulgePresent
            );

            out.put(
                    "v_outer_kms",
                    vObsOuter
            );

            out.put(
                    "v_baryonic_proxy_outer_kms",
                    vBarOuter
            );

            if (Double.isFinite(
                    obsToBar
            )) {

                out.put(
                        "obs_to_baryonic_outer_ratio",
                        obsToBar
                );
            }

            if (Double.isFinite(
                    vflatResidual
            )) {

                out.put(
                        "vflat_residual_kms",
                        vflatResidual
                );
            }

        } catch (Exception e) {

            try {

                out.put(
                        "ok",
                        false
                );

                out.put(
                        "error",
                        e.toString()
                );

            } catch (Exception ignored) {
            }
        }

        return out.toString();
    }

    // ============================================================
    // COHORTS
    // ============================================================

    @JavascriptInterface
    public String getCohort(
            String cohortName
    ) {

        JSONArray result =
                new JSONArray();

        try {

            File file =
                    metadataFile();

            if (!file.exists()) {

                return result.toString();
            }

            try (
                    BufferedReader reader =
                            readerFor(file)
            ) {

                String line;

                while ((line =
                        reader.readLine()) != null) {

                    JSONObject meta =
                            parseMetadataRow(
                                    line
                            );

                    if (meta == null) {
                        continue;
                    }

                    String galaxy =
                            meta.getString(
                                    "galaxy"
                            );

                    int quality =
                            meta.getInt(
                                    "quality"
                            );

                    double inclination =
                            meta.getDouble(
                                    "inclination_deg"
                            );

                    double vflat =
                            meta.getDouble(
                                    "vflat_kms"
                            );

                    JSONObject diag =
                            new JSONObject(
                                    getDiagnostics(
                                            galaxy
                                    )
                            );

                    int points =
                            diag.optInt(
                                    "point_count",
                                    0
                            );

                    double rMax =
                            diag.optDouble(
                                    "r_max_kpc",
                                    0.0
                            );

                    double extent =
                            diag.optDouble(
                                    "radial_extent_kpc",
                                    0.0
                            );

                    boolean bulge =
                            diag.optBoolean(
                                    "bulge_present",
                                    false
                            );

                    boolean include;

                    switch (cohortName) {

                        case "quality_1":

                            include =
                                    quality == 1;

                            break;

                        case "btfr_ready":

                            include =
                                    quality <= 2 &&
                                    vflat > 0 &&
                                    points >= 10;

                            break;

                        case "bulgeless":

                            include =
                                    points > 0 &&
                                    !bulge;

                            break;

                        case "extended_rotation_curve":

                            include =
                                    points >= 15 &&
                                    rMax >= 15.0;

                            break;

                        case "inclination_safe":

                            include =
                                    inclination >= 30.0 &&
                                    inclination <= 85.0;

                            break;

                        case "uig_benchmark_v1":

                            include =
                                    quality == 1 &&
                                    points >= 15 &&
                                    inclination >= 30.0 &&
                                    inclination <= 85.0 &&
                                    vflat > 0 &&
                                    rMax >= 8.0 &&
                                    extent >= 6.0;

                            break;

                        default:

                            include =
                                    false;
                    }

                    if (include) {

                        result.put(
                                galaxy
                        );
                    }
                }
            }

        } catch (Exception ignored) {
        }

        return result.toString();
    }

    @JavascriptInterface
    public String listCohorts() {

        JSONArray arr =
                new JSONArray();

        arr.put("quality_1");
        arr.put("btfr_ready");
        arr.put("bulgeless");
        arr.put("extended_rotation_curve");
        arr.put("inclination_safe");
        arr.put("uig_benchmark_v1");

        return arr.toString();
    }

    // ============================================================
    // EXPORT DEFINITIONS
    // ============================================================

    @JavascriptInterface
    public String listExportFields() {

        JSONArray arr =
                new JSONArray();

        arr.put("radius_kpc");
        arr.put("v_obs_kms");
        arr.put("v_err_kms");
        arr.put("v_gas_kms");
        arr.put("v_disk_kms");
        arr.put("v_bulge_kms");
        arr.put("sb_disk");
        arr.put("sb_bulge");

        return arr.toString();
    }

    @JavascriptInterface
    public String listExportStyles() {

        JSONArray arr =
                new JSONArray();

        arr.put("canonical_csv");
        arr.put("minimal_csv");
        arr.put("uig");
        arr.put("whitespace");

        return arr.toString();
    }

    private JSONObject builtInExportStyle(
            String styleName
    ) throws Exception {

        JSONObject style =
                new JSONObject();

        JSONArray columns =
                new JSONArray();

        if ("minimal_csv".equals(
                styleName
        )) {

            style.put(
                    "name",
                    "minimal_csv"
            );

            style.put(
                    "delimiter",
                    ","
            );

            style.put(
                    "include_header",
                    true
            );

            style.put(
                    "extension",
                    "csv"
            );

            columns.put(
                    column(
                            "radius_kpc",
                            "radius_kpc",
                            1.0,
                            0.0
                    )
            );

            columns.put(
                    column(
                            "v_obs_kms",
                            "v_obs_kms",
                            1.0,
                            0.0
                    )
            );

            columns.put(
                    column(
                            "v_err_kms",
                            "v_err_kms",
                            1.0,
                            0.0
                    )
            );

        } else if ("uig".equals(
                styleName
        )) {

            style.put(
                    "name",
                    "uig"
            );

            style.put(
                    "delimiter",
                    ","
            );

            style.put(
                    "include_header",
                    true
            );

            style.put(
                    "extension",
                    "txt"
            );

            style.put(
                    "galaxy_header_template",
                    "Galaxy name: {galaxy}"
            );

            columns.put(
                    column(
                            "radius_kpc",
                            "radius_kpc",
                            1.0,
                            0.0
                    )
            );

            columns.put(
                    column(
                            "v_obs_kms",
                            "v_obs",
                            1.0,
                            0.0
                    )
            );

            columns.put(
                    column(
                            "v_err_kms",
                            "v_err",
                            1.0,
                            0.0
                    )
            );

            columns.put(
                    column(
                            "v_gas_kms",
                            "v_gas",
                            1.0,
                            0.0
                    )
            );

            columns.put(
                    column(
                            "v_disk_kms",
                            "v_disk",
                            1.0,
                            0.0
                    )
            );

            columns.put(
                    column(
                            "v_bulge_kms",
                            "v_bulge",
                            1.0,
                            0.0
                    )
            );

            columns.put(
                    column(
                            "sb_disk",
                            "sb_disk",
                            1.0,
                            0.0
                    )
            );

            columns.put(
                    column(
                            "sb_bulge",
                            "sb_bulge",
                            1.0,
                            0.0
                    )
            );

        } else if ("whitespace".equals(
                styleName
        )) {

            style.put(
                    "name",
                    "whitespace"
            );

            style.put(
                    "delimiter",
                    " "
            );

            style.put(
                    "include_header",
                    true
            );

            style.put(
                    "extension",
                    "txt"
            );

            columns.put(
                    column(
                            "radius_kpc",
                            "r",
                            1.0,
                            0.0
                    )
            );

            columns.put(
                    column(
                            "v_obs_kms",
                            "vobs",
                            1.0,
                            0.0
                    )
            );

            columns.put(
                    column(
                            "v_err_kms",
                            "verr",
                            1.0,
                            0.0
                    )
            );

            columns.put(
                    column(
                            "v_gas_kms",
                            "vgas",
                            1.0,
                            0.0
                    )
            );

            columns.put(
                    column(
                            "v_disk_kms",
                            "vdisk",
                            1.0,
                            0.0
                    )
            );

            columns.put(
                    column(
                            "v_bulge_kms",
                            "vbulge",
                            1.0,
                            0.0
                    )
            );

        } else {

            style.put(
                    "name",
                    "canonical_csv"
            );

            style.put(
                    "delimiter",
                    ","
            );

            style.put(
                    "include_header",
                    true
            );

            style.put(
                    "extension",
                    "csv"
            );

            style.put(
                    "flat_cohort",
                    true
            );

            style.put(
                    "include_galaxy_column",
                    true
            );

            columns.put(
                    column(
                            "radius_kpc",
                            "radius_kpc",
                            1.0,
                            0.0
                    )
            );

            columns.put(
                    column(
                            "v_obs_kms",
                            "v_obs_kms",
                            1.0,
                            0.0
                    )
            );

            columns.put(
                    column(
                            "v_err_kms",
                            "v_err_kms",
                            1.0,
                            0.0
                    )
            );

            columns.put(
                    column(
                            "v_gas_kms",
                            "v_gas_kms",
                            1.0,
                            0.0
                    )
            );

            columns.put(
                    column(
                            "v_disk_kms",
                            "v_disk_kms",
                            1.0,
                            0.0
                    )
            );

            columns.put(
                    column(
                            "v_bulge_kms",
                            "v_bulge_kms",
                            1.0,
                            0.0
                    )
            );

            columns.put(
                    column(
                            "sb_disk",
                            "sb_disk",
                            1.0,
                            0.0
                    )
            );

            columns.put(
                    column(
                            "sb_bulge",
                            "sb_bulge",
                            1.0,
                            0.0
                    )
            );
        }

        style.put(
                "columns",
                columns
        );

        return style;
    }

    private JSONObject column(
            String source,
            String name,
            double scale,
            double offset
    ) throws Exception {

        JSONObject c =
                new JSONObject();

        c.put(
                "source",
                source
        );

        c.put(
                "name",
                name
        );

        c.put(
                "scale",
                scale
        );

        c.put(
                "offset",
                offset
        );

        return c;
    }

    // ============================================================
    // EXPORT API
    // ============================================================

    @JavascriptInterface
    public String exportGalaxyWithStyle(
            String galaxyName,
            String styleJson
    ) {

        try {

            JSONObject style;

            if (styleJson == null ||
                    styleJson.trim()
                            .isEmpty()) {

                style =
                        builtInExportStyle(
                                "canonical_csv"
                        );

            } else {

                style =
                        new JSONObject(
                                styleJson
                        );
            }

            return exportGalaxyUsingStyle(
                    galaxyName,
                    style
            );

        } catch (Exception e) {

            return "ERROR: " + e;
        }
    }

    @JavascriptInterface
    public String exportGalaxyPreset(
            String galaxyName,
            String presetName
    ) {

        try {

            return exportGalaxyUsingStyle(
                    galaxyName,
                    builtInExportStyle(
                            presetName
                    )
            );

        } catch (Exception e) {

            return "ERROR: " + e;
        }
    }

    @JavascriptInterface
    public String exportCohortPreset(
            String cohortName,
            String presetName
    ) {

        try {

            JSONArray galaxies =
                    new JSONArray(
                            getCohort(
                                    cohortName
                            )
                    );

            JSONObject style =
                    builtInExportStyle(
                            presetName
                    );

            return exportMultipleGalaxies(
                    galaxies,
                    cohortName,
                    style
            );

        } catch (Exception e) {

            return "ERROR: " + e;
        }
    }

    @JavascriptInterface
    public String exportCohortWithStyle(
            String cohortName,
            String styleJson
    ) {

        try {

            JSONArray galaxies =
                    new JSONArray(
                            getCohort(
                                    cohortName
                            )
                    );

            JSONObject style =
                    new JSONObject(
                            styleJson
                    );

            return exportMultipleGalaxies(
                    galaxies,
                    cohortName,
                    style
            );

        } catch (Exception e) {

            return "ERROR: " + e;
        }
    }

    // ============================================================
    // SINGLE GALAXY EXPORT
    // ============================================================

    private String exportGalaxyUsingStyle(
            String galaxyName,
            JSONObject style
    ) throws Exception {

        JSONArray rows =
                new JSONArray(
                        getRotationCurve(
                                galaxyName
                        )
                );

        String extension =
                style.optString(
                        "extension",
                        "txt"
                );

        String styleName =
                sanitizeFilename(
                        style.optString(
                                "name",
                                "custom"
                        )
                );

        String filename =
                galaxyName +
                        "_" +
                        styleName +
                        "." +
                        extension;

        String content =
                renderGalaxy(
                        galaxyName,
                        rows,
                        style
                );

        return saveExportToDownloads(
                filename,
                content,
                mimeTypeForExtension(
                        extension
                )
        );
    }

    // ============================================================
    // COHORT EXPORT
    // ============================================================

    private String exportMultipleGalaxies(
            JSONArray galaxies,
            String groupName,
            JSONObject style
    ) throws Exception {

        String extension =
                style.optString(
                        "extension",
                        "txt"
                );

        String styleName =
                sanitizeFilename(
                        style.optString(
                                "name",
                                "custom"
                        )
                );

        String filename =
                sanitizeFilename(
                        groupName
                ) +
                        "_" +
                        styleName +
                        "." +
                        extension;

        String content;

        if (style.optBoolean(
                "flat_cohort",
                false
        )) {

            content =
                    renderFlatCohort(
                            galaxies,
                            style
                    );

        } else {

            content =
                    renderBlockCohort(
                            galaxies,
                            style
                    );
        }

        return saveExportToDownloads(
                filename,
                content,
                mimeTypeForExtension(
                        extension
                )
        );
    }

    // ============================================================
    // COHORT RENDERING
    // ============================================================

    private String renderFlatCohort(
            JSONArray galaxies,
            JSONObject style
    ) throws Exception {

        String delimiter =
                decodeDelimiter(
                        style.optString(
                                "delimiter",
                                ","
                        )
                );

        boolean includeHeader =
                style.optBoolean(
                        "include_header",
                        true
                );

        boolean includeGalaxyColumn =
                style.optBoolean(
                        "include_galaxy_column",
                        true
                );

        JSONArray columns =
                style.getJSONArray(
                        "columns"
                );

        StringBuilder sb =
                new StringBuilder();

        String preamble =
                style.optString(
                        "preamble",
                        ""
                );

        if (!preamble.isEmpty()) {

            sb.append(
                    preamble
            );

            if (!preamble.endsWith(
                    "\n"
            )) {

                sb.append(
                        "\n"
                );
            }
        }

        if (includeHeader) {

            if (includeGalaxyColumn) {

                sb.append(
                        "galaxy_name"
                );

                if (columns.length() >
                        0) {

                    sb.append(
                            delimiter
                    );
                }
            }

            appendColumnHeader(
                    sb,
                    columns,
                    delimiter
            );

            sb.append(
                    "\n"
            );
        }

        for (int g = 0;
             g < galaxies.length();
             g++) {

            String galaxy =
                    galaxies.getString(
                            g
                    );

            JSONArray rows =
                    new JSONArray(
                            getRotationCurve(
                                    galaxy
                            )
                    );

            for (int r = 0;
                 r < rows.length();
                 r++) {

                JSONObject row =
                        rows.getJSONObject(
                                r
                        );

                if (includeGalaxyColumn) {

                    sb.append(
                            csvSafe(
                                    galaxy,
                                    delimiter
                            )
                    );

                    if (columns.length() >
                            0) {

                        sb.append(
                                delimiter
                        );
                    }
                }

                appendDataRow(
                        sb,
                        row,
                        columns,
                        delimiter
                );

                sb.append(
                        "\n"
                );
            }
        }

        return sb.toString();
    }

    private String renderBlockCohort(
            JSONArray galaxies,
            JSONObject style
    ) throws Exception {

        String separator =
                style.optString(
                        "galaxy_separator",
                        "\n"
                );

        StringBuilder all =
                new StringBuilder();

        String preamble =
                style.optString(
                        "preamble",
                        ""
                );

        if (!preamble.isEmpty()) {

            all.append(
                    preamble
            );

            if (!preamble.endsWith(
                    "\n"
            )) {

                all.append(
                        "\n"
                );
            }
        }

        for (int i = 0;
             i < galaxies.length();
             i++) {

            String galaxy =
                    galaxies.getString(
                            i
                    );

            JSONArray rows =
                    new JSONArray(
                            getRotationCurve(
                                    galaxy
                            )
                    );

            all.append(
                    renderGalaxy(
                            galaxy,
                            rows,
                            style
                    )
            );

            if (i <
                    galaxies.length() -
                            1) {

                all.append(
                        separator
                );
            }
        }

        return all.toString();
    }

    // ============================================================
    // FORMAT RENDERING
    // ============================================================

    private String renderGalaxy(
            String galaxyName,
            JSONArray rows,
            JSONObject style
    ) throws Exception {

        String delimiter =
                decodeDelimiter(
                        style.optString(
                                "delimiter",
                                ","
                        )
                );

        boolean includeHeader =
                style.optBoolean(
                        "include_header",
                        true
                );

        JSONArray columns =
                style.getJSONArray(
                        "columns"
                );

        StringBuilder sb =
                new StringBuilder();

        String galaxyHeader =
                style.optString(
                        "galaxy_header_template",
                        ""
                );

        if (!galaxyHeader.isEmpty()) {

            sb.append(
                    galaxyHeader.replace(
                            "{galaxy}",
                            galaxyName
                    )
            );

            sb.append(
                    "\n"
            );
        }

        if (includeHeader) {

            appendColumnHeader(
                    sb,
                    columns,
                    delimiter
            );

            sb.append(
                    "\n"
            );
        }

        for (int i = 0;
             i < rows.length();
             i++) {

            appendDataRow(
                    sb,
                    rows.getJSONObject(
                            i
                    ),
                    columns,
                    delimiter
            );

            sb.append(
                    "\n"
            );
        }

        return sb.toString();
    }

    private void appendColumnHeader(
            StringBuilder sb,
            JSONArray columns,
            String delimiter
    ) throws Exception {

        for (int c = 0;
             c < columns.length();
             c++) {

            JSONObject col =
                    columns.getJSONObject(
                            c
                    );

            if (c > 0) {

                sb.append(
                        delimiter
                );
            }

            sb.append(
                    col.optString(
                            "name",
                            col.getString(
                                    "source"
                            )
                    )
            );
        }
    }

    private void appendDataRow(
            StringBuilder sb,
            JSONObject row,
            JSONArray columns,
            String delimiter
    ) throws Exception {

        for (int c = 0;
             c < columns.length();
             c++) {

            JSONObject col =
                    columns.getJSONObject(
                            c
                    );

            if (c > 0) {

                sb.append(
                        delimiter
                );
            }

            String source =
                    col.getString(
                            "source"
                    );

            double value =
                    row.getDouble(
                            source
                    );

            double scale =
                    col.optDouble(
                            "scale",
                            1.0
                    );

            double offset =
                    col.optDouble(
                            "offset",
                            0.0
                    );

            int decimals =
                    col.optInt(
                            "decimals",
                            -1
                    );

            value =
                    value *
                            scale +
                            offset;

            if (decimals >= 0) {

                sb.append(
                        String.format(
                                Locale.US,
                                "%." +
                                        decimals +
                                        "f",
                                value
                        )
                );

            } else {

                sb.append(
                        Double.toString(
                                value
                        )
                );
            }
        }
    }

    private String decodeDelimiter(
            String delimiter
    ) {

        if ("\\t".equals(
                delimiter
        )) {

            return "\t";
        }

        if ("\\s".equals(
                delimiter
        )) {

            return " ";
        }

        return delimiter;
    }

    private String csvSafe(
            String value,
            String delimiter
    ) {

        if (!",".equals(
                delimiter
        )) {

            return value;
        }

        if (value.contains(",") ||
                value.contains("\"") ||
                value.contains("\n")) {

            return "\"" +
                    value.replace(
                            "\"",
                            "\"\""
                    ) +
                    "\"";
        }

        return value;
    }

    // ============================================================
    // LEGACY QUICK EXPORT
    // ============================================================

    @JavascriptInterface
    public String exportGalaxyCsv(
            String galaxyName
    ) {

        return exportGalaxyPreset(
                galaxyName,
                "canonical_csv"
        );
    }

    // ============================================================
    // MODERN DOWNLOAD STORAGE
    // ============================================================

    private String saveExportToDownloads(
            String filename,
            String content,
            String mimeType
    ) throws Exception {

        if (Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.Q) {

            return saveWithMediaStore(
                    filename,
                    content,
                    mimeType
            );
        }

        return saveLegacyDownloads(
                filename,
                content
        );
    }

    private String saveWithMediaStore(
            String filename,
            String content,
            String mimeType
    ) throws Exception {

        ContentResolver resolver =
                activity.getContentResolver();

        ContentValues values =
                new ContentValues();

        values.put(
                MediaStore.Downloads.DISPLAY_NAME,
                filename
        );

        values.put(
                MediaStore.Downloads.MIME_TYPE,
                mimeType
        );

        values.put(
                MediaStore.Downloads.RELATIVE_PATH,
                Environment.DIRECTORY_DOWNLOADS +
                        "/SPARC_Workbench"
        );

        values.put(
                MediaStore.Downloads.IS_PENDING,
                1
        );

        Uri uri =
                resolver.insert(
                        MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                        values
                );

        if (uri == null) {

            throw new Exception(
                    "MediaStore could not create export file."
            );
        }

        boolean success =
                false;

        try (
                OutputStream output =
                        resolver.openOutputStream(
                                uri
                        )
        ) {

            if (output == null) {

                throw new Exception(
                        "Could not open export output stream."
                );
            }

            output.write(
                    content.getBytes(
                            StandardCharsets.UTF_8
                    )
            );

            output.flush();

            success =
                    true;

        } finally {

            if (success) {

                ContentValues done =
                        new ContentValues();

                done.put(
                        MediaStore.Downloads.IS_PENDING,
                        0
                );

                resolver.update(
                        uri,
                        done,
                        null,
                        null
                );

            } else {

                resolver.delete(
                        uri,
                        null,
                        null
                );
            }
        }

        return "Downloads/SPARC_Workbench/" +
                filename;
    }

    private String saveLegacyDownloads(
            String filename,
            String content
    ) throws Exception {

        File downloads =
                Environment
                        .getExternalStoragePublicDirectory(
                                Environment.DIRECTORY_DOWNLOADS
                        );

        File outDir =
                new File(
                        downloads,
                        "SPARC_Workbench"
                );

        if (!outDir.exists() &&
                !outDir.mkdirs()) {

            throw new Exception(
                    "Could not create export directory."
            );
        }

        File out =
                new File(
                        outDir,
                        filename
                );

        try (
                FileOutputStream fos =
                        new FileOutputStream(
                                out
                        )
        ) {

            fos.write(
                    content.getBytes(
                            StandardCharsets.UTF_8
                    )
            );
        }

        return out.getAbsolutePath();
    }

    private String mimeTypeForExtension(
            String extension
    ) {

        if (extension == null) {

            return "text/plain";
        }

        String ext =
                extension.trim()
                        .toLowerCase(
                                Locale.US
                        );

        if ("csv".equals(
                ext
        )) {

            return "text/csv";
        }

        if ("tsv".equals(
                ext
        )) {

            return "text/tab-separated-values";
        }

        return "text/plain";
    }

    // ============================================================
    // DATA UPDATE HELPERS
    // ============================================================

    private boolean updateIfRemoteChanged(
            String urlString,
            File target,
            String lengthPreferenceKey,
            String modifiedPreferenceKey
    ) throws Exception {

        HttpURLConnection connection =
                (HttpURLConnection)
                        new URL(
                                urlString
                        )
                                .openConnection();

        connection.setRequestMethod(
                "HEAD"
        );

        connection.setConnectTimeout(
                7000
        );

        connection.setReadTimeout(
                7000
        );

        connection.setRequestProperty(
                "User-Agent",
                "SPARC-Workbench-Android/1.4"
        );

        connection.connect();

        int code =
                connection.getResponseCode();

        if (code < 200 ||
                code >= 400) {

            connection.disconnect();

            return false;
        }

        long remoteLength =
                connection.getContentLengthLong();

        long remoteModified =
                connection.getLastModified();

        connection.disconnect();

        long storedLength =
                prefs.getLong(
                        lengthPreferenceKey,
                        -1L
                );

        long storedModified =
                prefs.getLong(
                        modifiedPreferenceKey,
                        -1L
                );

        boolean firstObservation =
                storedLength < 0 &&
                        storedModified < 0;

        boolean changed =
                false;

        if (!firstObservation) {

            if (remoteLength > 0 &&
                    storedLength > 0 &&
                    remoteLength !=
                            storedLength) {

                changed =
                        true;
            }

            if (remoteModified > 0 &&
                    storedModified > 0 &&
                    remoteModified >
                            storedModified) {

                changed =
                        true;
            }
        }

        if (firstObservation) {

            if (remoteLength > 0 &&
                    target.length() > 0 &&
                    remoteLength !=
                            target.length()) {

                changed =
                        true;
            }
        }

        if (changed) {

            downloadFile(
                    urlString,
                    target
            );
        }

        SharedPreferences.Editor editor =
                prefs.edit();

        if (remoteLength > 0) {

            editor.putLong(
                    lengthPreferenceKey,
                    remoteLength
            );
        }

        if (remoteModified > 0) {

            editor.putLong(
                    modifiedPreferenceKey,
                    remoteModified
            );
        }

        editor.apply();

        return changed;
    }

    private void downloadIfNeeded(
            String urlString,
            File target
    ) throws Exception {

        if (validCachedFile(
                target
        )) {

            return;
        }

        downloadFile(
                urlString,
                target
        );
    }

    private void downloadFile(
            String urlString,
            File target
    ) throws Exception {

        File temp =
                new File(
                        target.getParentFile(),
                        target.getName() +
                                ".download"
                );

        HttpURLConnection connection =
                (HttpURLConnection)
                        new URL(
                                urlString
                        )
                                .openConnection();

        connection.setConnectTimeout(
                15000
        );

        connection.setReadTimeout(
                30000
        );

        connection.setRequestProperty(
                "User-Agent",
                "SPARC-Workbench-Android/1.4"
        );

        connection.connect();

        int response =
                connection.getResponseCode();

        if (response !=
                HttpURLConnection.HTTP_OK) {

            connection.disconnect();

            throw new Exception(
                    "HTTP " +
                            response
            );
        }

        try (
                java.io.InputStream input =
                        connection.getInputStream();

                FileOutputStream output =
                        new FileOutputStream(
                                temp
                        )
        ) {

            byte[] buffer =
                    new byte[8192];

            int count;

            while ((count =
                    input.read(
                            buffer
                    )) != -1) {

                output.write(
                        buffer,
                        0,
                        count
                );
            }

            output.flush();

        } finally {

            connection.disconnect();
        }

        if (temp.length() <= 1000) {

            temp.delete();

            throw new Exception(
                    "Downloaded SPARC file is unexpectedly small."
            );
        }

        if (target.exists() &&
                !target.delete()) {

            temp.delete();

            throw new Exception(
                    "Could not replace cached SPARC file."
            );
        }

        if (!temp.renameTo(
                target
        )) {

            copyFile(
                    temp,
                    target
            );

            temp.delete();
        }
    }

    private void copyFile(
            File source,
            File target
    ) throws Exception {

        try (
                FileInputStream input =
                        new FileInputStream(
                                source
                        );

                FileOutputStream output =
                        new FileOutputStream(
                                target
                        )
        ) {

            byte[] buffer =
                    new byte[8192];

            int count;

            while ((count =
                    input.read(
                            buffer
                    )) != -1) {

                output.write(
                        buffer,
                        0,
                        count
                );
            }
        }
    }

    private boolean validCachedFile(
            File file
    ) {

        return file.exists() &&
                file.length() >
                        1000;
    }

    // ============================================================
    // GENERAL HELPERS
    // ============================================================

    private File sparcDirectory() {

        return new File(
                activity.getFilesDir(),
                "sparc"
        );
    }

    private File metadataFile() {

        return new File(
                sparcDirectory(),
                "SPARC_Lelli2016c.mrt"
        );
    }

    private File massModelFile() {

        return new File(
                sparcDirectory(),
                "MassModels_Lelli2016c.mrt"
        );
    }

    private BufferedReader readerFor(
            File file
    ) throws Exception {

        return new BufferedReader(
                new InputStreamReader(
                        new FileInputStream(
                                file
                        ),
                        StandardCharsets.UTF_8
                )
        );
    }

    private double segmentSlope(
            JSONArray curve,
            int start,
            int end
    ) throws Exception {

        if (curve.length() < 2 ||
                end <= start) {

            return 0.0;
        }

        JSONObject a =
                curve.getJSONObject(
                        start
                );

        JSONObject b =
                curve.getJSONObject(
                        end
                );

        double dr =
                b.getDouble(
                        "radius_kpc"
                ) -
                        a.getDouble(
                                "radius_kpc"
                        );

        if (Math.abs(dr) <
                1e-12) {

            return 0.0;
        }

        return (
                b.getDouble(
                        "v_obs_kms"
                ) -
                        a.getDouble(
                                "v_obs_kms"
                        )
        ) / dr;
    }

    private double median(
            List<Double> values
    ) {

        if (values == null ||
                values.isEmpty()) {

            return Double.NaN;
        }

        List<Double> copy =
                new ArrayList<>(
                        values
                );

        Collections.sort(
                copy
        );

        int n =
                copy.size();

        if (n % 2 == 1) {

            return copy.get(
                    n / 2
            );
        }

        return 0.5 *
                (
                        copy.get(
                                n / 2 -
                                        1
                        ) +
                                copy.get(
                                        n / 2
                                )
                );
    }

    private double signedSquare(
            double value
    ) {

        if (value < 0) {

            return -(
                    value *
                            value
            );
        }

        return value *
                value;
    }

    private String sanitizeFilename(
            String value
    ) {

        return value.replaceAll(
                "[^A-Za-z0-9._-]+",
                "_"
        );
    }

    private void sendToJs(
            String function,
            String json
    ) {

        String escaped =
                json
                        .replace(
                                "\\",
                                "\\\\"
                        )
                        .replace(
                                "'",
                                "\\'"
                        );

        activity.runOnUiThread(
                () ->
                        webView.evaluateJavascript(
                                "window." +
                                        function +
                                        "('" +
                                        escaped +
                                        "')",
                                null
                        )
        );
    }
        }
