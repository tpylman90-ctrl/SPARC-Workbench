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
import java.util.LinkedHashSet;

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

    // ------------------------------------------------------------
    // LOAD / DOWNLOAD SPARC
    // ------------------------------------------------------------

    @JavascriptInterface
    public void loadSparcData() {

        new Thread(() -> {

            try {

                File dir =
                        new File(
                                activity.getFilesDir(),
                                "sparc"
                        );

                if (!dir.exists()) {
                    dir.mkdirs();
                }

                File meta =
                        new File(
                                dir,
                                "SPARC_Lelli2016c.mrt"
                        );

                File mass =
                        new File(
                                dir,
                                "MassModels_Lelli2016c.mrt"
                        );

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


    // ------------------------------------------------------------
    // GALAXY CATALOG
    // ------------------------------------------------------------

    @JavascriptInterface
    public String getGalaxyCatalog() {

        JSONArray result =
                new JSONArray();

        try {

            File file =
                    new File(
                            new File(
                                    activity.getFilesDir(),
                                    "sparc"
                            ),
                            "SPARC_Lelli2016c.mrt"
                    );

            if (!file.exists()) {
                return result.toString();
            }

            LinkedHashSet<String> galaxies =
                    new LinkedHashSet<>();

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

                while (
                        (line = reader.readLine())
                                != null
                ) {

                    line =
                            line.trim();

                    if (line.isEmpty()) {
                        continue;
                    }

                    String[] p =
                            line.split("\\s+");

                    /*
                     * Genuine SPARC metadata rows contain
                     * the complete structured numeric record.
                     *
                     * Header material such as:
                     *
                     * Spitzer
                     * Bytes
                     * 1-
                     * 12-
                     *
                     * fails these checks automatically.
                     */

                    if (p.length < 18) {
                        continue;
                    }

                    try {

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

                        // Remaining SPARC numerical fields

                        Double.parseDouble(p[7]);
                        Double.parseDouble(p[8]);
                        Double.parseDouble(p[9]);
                        Double.parseDouble(p[10]);
                        Double.parseDouble(p[11]);
                        Double.parseDouble(p[12]);
                        Double.parseDouble(p[13]);
                        Double.parseDouble(p[14]);
                        Double.parseDouble(p[15]);
                        Double.parseDouble(p[16]);

                        int quality =
                                Integer.parseInt(
                                        p[17]
                                );

                        // ----------------------------
                        // Scientific sanity checks
                        // ----------------------------

                        if (
                                galaxy == null ||
                                galaxy.isEmpty()
                        ) {
                            continue;
                        }

                        if (
                                hubble < 0 ||
                                hubble > 11
                        ) {
                            continue;
                        }

                        if (
                                distance <= 0
                        ) {
                            continue;
                        }

                        if (
                                distanceError < 0
                        ) {
                            continue;
                        }

                        if (
                                distanceMethod < 1 ||
                                distanceMethod > 5
                        ) {
                            continue;
                        }

                        if (
                                inclination <= 0 ||
                                inclination > 90
                        ) {
                            continue;
                        }

                        if (
                                inclinationError < 0
                        ) {
                            continue;
                        }

                        if (
                                quality < 1 ||
                                quality > 3
                        ) {
                            continue;
                        }

                        galaxies.add(
                                galaxy
                        );

                    } catch (Exception ignored) {

                        /*
                         * Anything that does not behave like
                         * a complete SPARC data row is ignored.
                         */
                    }
                }
            }

            for (
                    String galaxy :
                    galaxies
            ) {

                result.put(
                        galaxy
                );
            }

        } catch (Exception ignored) {
        }

        return result.toString();
    }


    // ------------------------------------------------------------
    // ROTATION CURVES
    // ------------------------------------------------------------

    @JavascriptInterface
    public String getRotationCurve(
            String galaxyName
    ) {

        JSONArray rows =
                new JSONArray();

        try {

            File file =
                    new File(
                            new File(
                                    activity.getFilesDir(),
                                    "sparc"
                            ),
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

                while (
                        (line = reader.readLine())
                                != null
                ) {

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

                    if (
                            !p[0].equals(
                                    galaxyName
                            )
                    ) {
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


    // ------------------------------------------------------------
    // CSV EXPORT
    // ------------------------------------------------------------

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

                if (!outDir.mkdirs()) {

                    return
                            "ERROR: Could not create export directory.";
                }
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
                            new FileOutputStream(
                                    out
                            )
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


    // ------------------------------------------------------------
    // DOWNLOAD UTILITY
    // ------------------------------------------------------------

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
                "SPARC-Workbench-Android/1.1.1"
        );

        connection.connect();

        int response =
                connection
                        .getResponseCode();

        if (
                response !=
                HttpURLConnection.HTTP_OK
        ) {

            throw new Exception(
                    "HTTP " +
                    response
            );
        }

        try (
                java.io.InputStream input =
                        connection
                                .getInputStream();

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


    // ------------------------------------------------------------
    // JAVASCRIPT CALLBACK UTILITY
    // ------------------------------------------------------------

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