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

                if (!dir.exists()) {
                    dir.mkdirs();
                }

                File meta =
                        new File(dir, "SPARC_Lelli2016c.mrt");

                File mass =
                        new File(dir, "MassModels_Lelli2016c.mrt");

                downloadIfNeeded(META_URL, meta);
                downloadIfNeeded(MASS_URL, mass);

                JSONObject result = new JSONObject();
                result.put("ok", true);

                sendToJs(
                        "onSparcLoaded",
                        result.toString()
                );

            } catch (Exception e) {

                try {
                    JSONObject result = new JSONObject();

                    result.put("ok", false);
                    result.put("error", e.toString());

                    sendToJs(
                            "onSparcLoaded",
                            result.toString()
                    );

                } catch (Exception ignored) {
                }
            }
        }).start();
    }

    @JavascriptInterface
    public String getGalaxyCatalog() {

        JSONArray arr = new JSONArray();

        try {

            File file = new File(
                    new File(activity.getFilesDir(), "sparc"),
                    "SPARC_Lelli2016c.mrt"
            );

            if (!file.exists()) {
                return arr.toString();
            }

            try (
                    BufferedReader reader =
                            new BufferedReader(
                                    new InputStreamReader(
                                            new FileInputStream(file),
                                            StandardCharsets.UTF_8
                                    )
                            )
            ) {

                String line;

                while ((line = reader.readLine()) != null) {

                    if (line.length() < 100) {
                        continue;
                    }

                    try {

                        String galaxy =
                                line.substring(0, 11).trim();

                        String hubbleText =
                                line.substring(11, 13).trim();

                        String distanceText =
                                line.substring(13, 19).trim();

                        String inclinationText =
                                line.substring(26, 30).trim();

                        String qualityText =
                                line.substring(96, 99).trim();

                        if (galaxy.isEmpty()) {
                            continue;
                        }

                        int hubble =
                                Integer.parseInt(hubbleText);

                        double distance =
                                Double.parseDouble(distanceText);

                        double inclination =
                                Double.parseDouble(inclinationText);

                        int quality =
                                Integer.parseInt(qualityText);

                        if (hubble < 0 || hubble > 11) {
                            continue;
                        }

                        if (distance <= 0) {
                            continue;
                        }

                        if (inclination <= 0 ||
                                inclination > 90) {
                            continue;
                        }

                        if (quality < 1 ||
                                quality > 3) {
                            continue;
                        }

                        arr.put(galaxy);

                    } catch (Exception ignored) {
                    }
                }
            }

        } catch (Exception ignored) {
        }

        return arr.toString();
    }

    @JavascriptInterface
    public String getRotationCurve(String galaxyName) {

        JSONArray rows = new JSONArray();

        try {

            File file = new File(
                    new File(activity.getFilesDir(), "sparc"),
                    "MassModels_Lelli2016c.mrt"
            );

            if (!file.exists()) {
                return rows.toString();
            }

            try (
                    BufferedReader reader =
                            new BufferedReader(
                                    new InputStreamReader(
                                            new FileInputStream(file),
                                            StandardCharsets.UTF_8
                                    )
                            )
            ) {

                String line;

                while ((line = reader.readLine()) != null) {

                    line = line.trim();

                    if (line.isEmpty()) {
                        continue;
                    }

                    String[] p =
                            line.split("\\s+");

                    if (p.length < 10) {
                        continue;
                    }

                    if (!p[0].equals(galaxyName)) {
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
                                Double.parseDouble(p[1])
                        );

                        row.put(
                                "radius_kpc",
                                Double.parseDouble(p[2])
                        );

                        row.put(
                                "v_obs_kms",
                                Double.parseDouble(p[3])
                        );

                        row.put(
                                "v_err_kms",
                                Double.parseDouble(p[4])
                        );

                        row.put(
                                "v_gas_kms",
                                Double.parseDouble(p[5])
                        );

                        row.put(
                                "v_disk_kms",
                                Double.parseDouble(p[6])
                        );

                        row.put(
                                "v_bulge_kms",
                                Double.parseDouble(p[7])
                        );

                        row.put(
                                "sb_disk",
                                Double.parseDouble(p[8])
                        );

                        row.put(
                                "sb_bulge",
                                Double.parseDouble(p[9])
                        );

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
    public String exportGalaxyCsv(
            String galaxyName
    ) {

        try {

            JSONArray rows =
                    new JSONArray(
                            getRotationCurve(
                                    galaxyName
                            )
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

            if (!outDir.exists()) {
                outDir.mkdirs();
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

            for (
                    int i = 0;
                    i < rows.length();
                    i++
            ) {

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

            return out.getAbsolutePath();

        } catch (Exception e) {

            return "ERROR: " + e;
        }
    }

    private void downloadIfNeeded(
            String urlString,
            File target
    ) throws Exception {

        if (
                target.exists() &&
                target.length() > 1000
        ) {
            return;
        }

        HttpURLConnection conn =
                (HttpURLConnection)
                        new URL(
                                urlString
                        ).openConnection();

        conn.setConnectTimeout(15000);
        conn.setReadTimeout(30000);

        conn.setRequestProperty(
                "User-Agent",
                "SPARC-Workbench-Android/1.1"
        );

        conn.connect();

        int response =
                conn.getResponseCode();

        if (response != 200) {
            throw new Exception(
                    "HTTP " + response
            );
        }

        try (
                java.io.InputStream in =
                        conn.getInputStream();

                FileOutputStream out =
                        new FileOutputStream(
                                target
                        )
        ) {

            byte[] buffer =
                    new byte[8192];

            int count;

            while (
                    (count =
                            in.read(buffer))
                            != -1
            ) {

                out.write(
                        buffer,
                        0,
                        count
                );
            }

        } finally {

            conn.disconnect();
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
                        webView
                                .evaluateJavascript(
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
