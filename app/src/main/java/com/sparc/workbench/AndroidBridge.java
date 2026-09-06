package org.sparcworkbench.app;

import android.app.Activity;
import android.os.Environment;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

public class AndroidBridge {

    private final Activity activity;
    private final WebView webView;

    private static final String META_URL =
            "https://astroweb.cwru.edu/SPARC/SPARC_Lelli2016c.mrt";

    private static final String MASS_URL =
            "https://astroweb.cwru.edu/SPARC/MassModels_Lelli2016c.mrt";

    public AndroidBridge(Activity activity, WebView webView) {
        this.activity = activity;
        this.webView = webView;
    }

    @JavascriptInterface
    public void loadSparcData() {
        new Thread(() -> {
            try {
                File dir = new File(activity.getFilesDir(), "sparc");

                if (!dir.exists() && !dir.mkdirs()) {
                    throw new Exception("Could not create SPARC data directory.");
                }

                File meta = new File(dir, "SPARC_Lelli2016c.mrt");
                File mass = new File(dir, "MassModels_Lelli2016c.mrt");

                downloadIfNeeded(META_URL, meta);
                downloadIfNeeded(MASS_URL, mass);

                JSONObject result = new JSONObject();
                result.put("ok", true);
                result.put("metadataBytes", meta.length());
                result.put("massModelBytes", mass.length());

                sendToJs("onSparcLoaded", result.toString());

            } catch (Exception e) {
                try {
                    JSONObject result = new JSONObject();
                    result.put("ok", false);
                    result.put("error", e.toString());

                    sendToJs("onSparcLoaded", result.toString());
                } catch (Exception ignored) {
                }
            }
        }).start();
    }

    private JSONObject parseMetadataRow(String line) {
        try {
            String[] p = line.trim().split("\\s+");

            if (p.length < 18) {
                return null;
            }

            String galaxy = p[0];
            int hubble = Integer.parseInt(p[1]);
            double distance = Double.parseDouble(p[2]);
            double distanceError = Double.parseDouble(p[3]);
            int distanceMethod = Integer.parseInt(p[4]);
            double inclination = Double.parseDouble(p[5]);
            double inclinationError = Double.parseDouble(p[6]);
            double luminosity36 = Double.parseDouble(p[7]);
            double luminosityError = Double.parseDouble(p[8]);
            double effectiveRadius = Double.parseDouble(p[9]);
            double effectiveSurfaceBrightness = Double.parseDouble(p[10]);
            double diskScaleLength = Double.parseDouble(p[11]);
            double diskCentralSurfaceBrightness = Double.parseDouble(p[12]);
            double mhi = Double.parseDouble(p[13]);
            double rhi = Double.parseDouble(p[14]);
            double vflat = Double.parseDouble(p[15]);
            double vflatError = Double.parseDouble(p[16]);
            int quality = Integer.parseInt(p[17]);

            if (galaxy.isEmpty()) return null;
            if (hubble < 0 || hubble > 11) return null;
            if (distance <= 0) return null;
            if (distanceError < 0) return null;
            if (distanceMethod < 1 || distanceMethod > 5) return null;
            if (inclination <= 0 || inclination > 90) return null;
            if (inclinationError < 0) return null;
            if (quality < 1 || quality > 3) return null;

            JSONObject obj = new JSONObject();

            obj.put("galaxy", galaxy);
            obj.put("hubble_type", hubble);
            obj.put("distance_mpc", distance);
            obj.put("distance_error_mpc", distanceError);
            obj.put("distance_method", distanceMethod);
            obj.put("inclination_deg", inclination);
            obj.put("inclination_error_deg", inclinationError);
            obj.put("luminosity_36", luminosity36);
            obj.put("luminosity_error", luminosityError);
            obj.put("effective_radius_kpc", effectiveRadius);
            obj.put("effective_surface_brightness", effectiveSurfaceBrightness);
            obj.put("disk_scale_length_kpc", diskScaleLength);
            obj.put("disk_central_surface_brightness", diskCentralSurfaceBrightness);
            obj.put("mhi", mhi);
            obj.put("rhi_kpc", rhi);
            obj.put("vflat_kms", vflat);
            obj.put("vflat_error_kms", vflatError);
            obj.put("quality", quality);

            return obj;

        } catch (Exception ignored) {
            return null;
        }
    }

    @JavascriptInterface
    public String getGalaxyCatalog() {
        JSONArray result = new JSONArray();

        try {
            File file = metadataFile();

            if (!file.exists()) {
                return result.toString();
            }

            LinkedHashSet<String> galaxies = new LinkedHashSet<>();

            try (BufferedReader reader = readerFor(file)) {
                String line;

                while ((line = reader.readLine()) != null) {
                    JSONObject meta = parseMetadataRow(line);

                    if (meta != null) {
                        galaxies.add(meta.getString("galaxy"));
                    }
                }
            }

            for (String galaxy : galaxies) {
                result.put(galaxy);
            }

        } catch (Exception ignored) {
        }

        return result.toString();
    }

    @JavascriptInterface
    public String getGalaxyMetadata(String galaxyName) {
        try {
            File file = metadataFile();

            if (!file.exists()) {
                return "{}";
            }

            try (BufferedReader reader = readerFor(file)) {
                String line;

                while ((line = reader.readLine()) != null) {
                    JSONObject meta = parseMetadataRow(line);

                    if (meta != null &&
                            galaxyName.equals(meta.optString("galaxy"))) {
                        return meta.toString();
                    }
                }
            }

        } catch (Exception ignored) {
        }

        return "{}";
    }

    @JavascriptInterface
    public String getRotationCurve(String galaxyName) {
        JSONArray rows = new JSONArray();

        try {
            File file = massModelFile();

            if (!file.exists()) {
                return rows.toString();
            }

            try (BufferedReader reader = readerFor(file)) {
                String line;

                while ((line = reader.readLine()) != null) {
                    line = line.trim();

                    if (line.isEmpty()) {
                        continue;
                    }

                    String[] p = line.split("\\s+");

                    if (p.length < 10) {
                        continue;
                    }

                    if (!p[0].equals(galaxyName)) {
                        continue;
                    }

                    try {
                        JSONObject row = new JSONObject();

                        row.put("galaxy", p[0]);
                        row.put("distance_mpc", Double.parseDouble(p[1]));
                        row.put("radius_kpc", Double.parseDouble(p[2]));
                        row.put("v_obs_kms", Double.parseDouble(p[3]));
                        row.put("v_err_kms", Double.parseDouble(p[4]));
                        row.put("v_gas_kms", Double.parseDouble(p[5]));
                        row.put("v_disk_kms", Double.parseDouble(p[6]));
                        row.put("v_bulge_kms", Double.parseDouble(p[7]));
                        row.put("sb_disk", Double.parseDouble(p[8]));
                        row.put("sb_bulge", Double.parseDouble(p[9]));

                        rows.put(row);

                    } catch (Exception ignored) {
                    }
                }
            }

        } catch (Exception ignored) {
        }

        return rows.toString();
    }

    @JavascriptInterface
    public String getDiagnostics(String galaxyName) {
        JSONObject out = new JSONObject();

        try {
            JSONArray curve =
                    new JSONArray(getRotationCurve(galaxyName));

            JSONObject meta =
                    new JSONObject(getGalaxyMetadata(galaxyName));

            int n = curve.length();

            out.put("galaxy", galaxyName);
            out.put("point_count", n);

            if (n == 0) {
                out.put("ok", false);
                return out.toString();
            }

            double rMin =
                    curve.getJSONObject(0)
                            .getDouble("radius_kpc");

            double rMax =
                    curve.getJSONObject(n - 1)
                            .getDouble("radius_kpc");

            double radialExtent =
                    rMax - rMin;

            double innerSlope =
                    segmentSlope(
                            curve,
                            0,
                            Math.max(1, n / 5)
                    );

            double outerSlope =
                    segmentSlope(
                            curve,
                            Math.max(
                                    0,
                                    n - Math.max(3, n / 4)
                            ),
                            n - 1
                    );

            List<Double> fracErrors =
                    new ArrayList<>();

            boolean bulgePresent = false;

            for (int i = 0; i < n; i++) {
                JSONObject row =
                        curve.getJSONObject(i);

                double v =
                        row.getDouble("v_obs_kms");

                double e =
                        row.getDouble("v_err_kms");

                double vb =
                        row.getDouble("v_bulge_kms");

                if (v > 0 && e >= 0) {
                    fracErrors.add(e / v);
                }

                if (Math.abs(vb) > 0.01) {
                    bulgePresent = true;
                }
            }

            double medianFracError =
                    median(fracErrors);

            JSONObject last =
                    curve.getJSONObject(n - 1);

            double vObsOuter =
                    last.getDouble("v_obs_kms");

            double vGas =
                    last.getDouble("v_gas_kms");

            double vDisk =
                    last.getDouble("v_disk_kms");

            double vBulge =
                    last.getDouble("v_bulge_kms");

            double vBarOuter =
                    Math.sqrt(
                            Math.max(
                                    0.0,
                                    signedSquare(vGas) +
                                            signedSquare(vDisk) +
                                            signedSquare(vBulge)
                            )
                    );

            double obsToBar =
                    vBarOuter > 0
                            ? vObsOuter / vBarOuter
                            : Double.NaN;

            String outerClass;

            if (outerSlope > 1.0) {
                outerClass = "rising";
            } else if (outerSlope < -1.0) {
                outerClass = "declining";
            } else {
                outerClass = "flat";
            }

            double vflat =
                    meta.optDouble(
                            "vflat_kms",
                            0.0
                    );

            double vflatResidual =
                    vflat > 0
                            ? vObsOuter - vflat
                            : Double.NaN;

            out.put("ok", true);
            out.put("r_min_kpc", rMin);
            out.put("r_max_kpc", rMax);
            out.put("radial_extent_kpc", radialExtent);
            out.put("inner_slope_kms_per_kpc", innerSlope);
            out.put("outer_slope_kms_per_kpc", outerSlope);
            out.put("outer_class", outerClass);
            out.put("median_fractional_velocity_error", medianFracError);
            out.put("bulge_present", bulgePresent);
            out.put("v_outer_kms", vObsOuter);
            out.put("v_baryonic_proxy_outer_kms", vBarOuter);

            if (Double.isFinite(obsToBar)) {
                out.put(
                        "obs_to_baryonic_outer_ratio",
                        obsToBar
                );
            }

            if (Double.isFinite(vflatResidual)) {
                out.put(
                        "vflat_residual_kms",
                        vflatResidual
                );
            }

        } catch (Exception e) {
            try {
                out.put("ok", false);
                out.put("error", e.toString());
            } catch (Exception ignored) {
            }
        }

        return out.toString();
    }

    @JavascriptInterface
    public String getCohort(String cohortName) {
        JSONArray result = new JSONArray();

        try {
            File file = metadataFile();

            if (!file.exists()) {
                return result.toString();
            }

            try (BufferedReader reader = readerFor(file)) {
                String line;

                while ((line = reader.readLine()) != null) {
                    JSONObject meta =
                            parseMetadataRow(line);

                    if (meta == null) {
                        continue;
                    }

                    String galaxy =
                            meta.getString("galaxy");

                    int quality =
                            meta.getInt("quality");

                    double inclination =
                            meta.getDouble("inclination_deg");

                    double vflat =
                            meta.getDouble("vflat_kms");

                    JSONObject diag =
                            new JSONObject(
                                    getDiagnostics(galaxy)
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
                            include = false;
                    }

                    if (include) {
                        result.put(galaxy);
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

    @JavascriptInterface
    public String exportGalaxyCsv(
            String galaxyName
    ) {

        try {
            JSONArray rows =
                    new JSONArray(
                            getRotationCurve(galaxyName)
                    );

            File downloads =
                    Environment
                            .getExternalStoragePublicDirectory(
                                    Environment
                                            .DIRECTORY_DOWNLOADS
                            );

            File outDir =
                    new File(
                            downloads,
                            "SPARC_Workbench"
                    );

            if (!outDir.exists() &&
                    !outDir.mkdirs()) {

                return
                        "ERROR: Could not create export directory.";
            }

            File out =
                    new File(
                            outDir,
                            galaxyName +
                                    "_sparc.csv"
                    );

            StringBuilder sb =
                    new StringBuilder();

            sb.append(
                    "radius_kpc," +
                            "v_obs_kms," +
                            "v_err_kms," +
                            "v_gas_kms," +
                            "v_disk_kms," +
                            "v_bulge_kms," +
                            "sb_disk," +
                            "sb_bulge\n"
            );

            for (int i = 0; i < rows.length(); i++) {

                JSONObject r =
                        rows.getJSONObject(i);

                sb.append(
                        r.getDouble(
                                "radius_kpc"
                        )
                ).append(",");

                sb.append(
                        r.getDouble(
                                "v_obs_kms"
                        )
                ).append(",");

                sb.append(
                        r.getDouble(
                                "v_err_kms"
                        )
                ).append(",");

                sb.append(
                        r.getDouble(
                                "v_gas_kms"
                        )
                ).append(",");

                sb.append(
                        r.getDouble(
                                "v_disk_kms"
                        )
                ).append(",");

                sb.append(
                        r.getDouble(
                                "v_bulge_kms"
                        )
                ).append(",");

                sb.append(
                        r.getDouble(
                                "sb_disk"
                        )
                ).append(",");

                sb.append(
                        r.getDouble(
                                "sb_bulge"
                        )
                ).append("\n");
            }

            try (
                    FileOutputStream fos =
                            new FileOutputStream(out)
            ) {

                fos.write(
                        sb.toString()
                                .getBytes(
                                        StandardCharsets.UTF_8
                                )
                );
            }

            return
                    out.getAbsolutePath();

        } catch (Exception e) {

            return
                    "ERROR: " +
                            e.toString();
        }
    }

    private File metadataFile() {
        return new File(
                new File(
                        activity.getFilesDir(),
                        "sparc"
                ),
                "SPARC_Lelli2016c.mrt"
        );
    }

    private File massModelFile() {
        return new File(
                new File(
                        activity.getFilesDir(),
                        "sparc"
                ),
                "MassModels_Lelli2016c.mrt"
        );
    }

    private BufferedReader readerFor(
            File file
    ) throws Exception {

        return new BufferedReader(
                new InputStreamReader(
                        new FileInputStream(file),
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
                curve.getJSONObject(start);

        JSONObject b =
                curve.getJSONObject(end);

        double dr =
                b.getDouble("radius_kpc") -
                        a.getDouble("radius_kpc");

        if (Math.abs(dr) < 1e-12) {
            return 0.0;
        }

        return (
                b.getDouble("v_obs_kms") -
                        a.getDouble("v_obs_kms")
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
                new ArrayList<>(values);

        Collections.sort(copy);

        int n =
                copy.size();

        if (n % 2 == 1) {
            return copy.get(n / 2);
        }

        return 0.5 *
                (
                        copy.get(n / 2 - 1) +
                                copy.get(n / 2)
                );
    }

    private double signedSquare(
            double value
    ) {

        if (value < 0) {
            return -(value * value);
        }

        return value * value;
    }

    private void downloadIfNeeded(
            String urlString,
            File target
    ) throws Exception {

        if (target.exists() &&
                target.length() > 1000) {

            return;
        }

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
                "SPARC-Workbench-Android/1.2"
        );

        connection.connect();

        int response =
                connection.getResponseCode();

        if (response !=
                HttpURLConnection.HTTP_OK) {

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
                                target
                        )
        ) {

            byte[] buffer =
                    new byte[8192];

            int count;

            while (
                    (count =
                            input.read(
                                    buffer
                            ))
                            != -1
            ) {

                output.write(
                        buffer,
                        0,
                        count
                );
            }

        } finally {

            connection.disconnect();
        }
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
