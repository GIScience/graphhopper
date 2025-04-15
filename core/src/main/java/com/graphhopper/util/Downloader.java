/*
 *  Licensed to GraphHopper GmbH under one or more contributor
 *  license agreements. See the NOTICE file distributed with this work for
 *  additional information regarding copyright ownership.
 *
 *  GraphHopper GmbH licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except in
 *  compliance with the License. You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.graphhopper.util;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.zip.GZIPInputStream;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.Header;

/**
 * @author Peter Karich
 */
public class Downloader {
    private final String userAgent;
    private String referrer = "http://graphhopper.com";
    private String acceptEncoding = "gzip, deflate";
    private int timeout = 4000;
    private final ProgressListener progressListener;

    public Downloader(String userAgent, ProgressListener progressListener) {
        this.userAgent = userAgent;
        this.progressListener = progressListener;
    }

    public Downloader(String userAgent) {
        this(userAgent, null);
    }

    public static void main(String[] args) throws IOException {
        new Downloader("GraphHopper Downloader").downloadAndUnzip("http://graphhopper.com/public/maps/0.1/europe_germany_berlin.ghz", "somefolder",
                new ProgressListener() {
                    @Override
                    public void update(long val) {
                        System.out.println("progress:" + val);
                    }
                });
    }

    public Downloader setTimeout(int timeout) {
        this.timeout = timeout;
        return this;
    }

    public Downloader setReferrer(String referrer) {
        this.referrer = referrer;
        return this;
    }

    /**
     * This method initiates a connect call of the provided connection and returns the response
     * stream. It only returns the error stream if it is available and readErrorStreamNoException is
     * true otherwise it throws an IOException if an error happens. Furthermore it wraps the stream
     * to decompress it if the connection content encoding is specified.
     */
    public InputStream fetch(HttpURLConnection connection, boolean readErrorStreamNoException) throws IOException {
        // create connection but before reading get the correct inputstream based on the compression and if error
        connection.connect();

        InputStream is;
        if (readErrorStreamNoException && connection.getResponseCode() >= 400 && connection.getErrorStream() != null)
            is = connection.getErrorStream();
        else
            is = connection.getInputStream();

        if (is == null)
            throw new IOException("Stream is null. Message:" + connection.getResponseMessage());

        // wrap
        try {
            String encoding = connection.getContentEncoding();
            if (encoding != null && encoding.equalsIgnoreCase("gzip"))
                is = new GZIPInputStream(is);
            else if (encoding != null && encoding.equalsIgnoreCase("deflate"))
                is = new InflaterInputStream(is, new Inflater(true));
        } catch (IOException ex) {
        }

        return is;
    }

    public InputStream fetch(String url) throws IOException {
        return fetch((HttpURLConnection) createConnection(url), false);
    }

    public HttpURLConnection createConnection(String urlStr) throws IOException {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        // Will yield in a POST request: conn.setDoOutput(true);
        conn.setDoInput(true);
        conn.setUseCaches(true);
        conn.setRequestProperty("Referrer", referrer);
        conn.setRequestProperty("User-Agent", userAgent);
        // suggest respond to be gzipped or deflated (which is just another compression)
        // http://stackoverflow.com/q/3932117
        conn.setRequestProperty("Accept-Encoding", acceptEncoding);
        conn.setReadTimeout(timeout);
        conn.setConnectTimeout(timeout);
        return conn;
    }

    public void downloadFile(String url, String toFile) throws IOException {
        final int bufferSize = 4096; // 4kB buffer
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpGet httpGet = new HttpGet(url);

            try (CloseableHttpResponse response = httpClient.execute(httpGet)) {
                int statusCode = response.getCode();
                if (statusCode < 200 || statusCode >= 300) {
                    throw new IOException("Server returned HTTP response code: " + statusCode + " for URL: " + url);
                }

                HttpEntity entity = response.getEntity();
                if (entity == null) {
                    throw new IOException("No entity found in response for URL: " + url);
                }

                long contentLength = entity.getContentLength(); // Länge von der Entity holen
                if (contentLength <= 0) {
                    Header lengthHeader = response.getFirstHeader("Content-Length");
                    if(lengthHeader != null) {
                        try {
                            contentLength = Long.parseLong(lengthHeader.getValue());
                        } catch (NumberFormatException e) {
                            contentLength = -1;
                        }
                    }
                }

                try (InputStream inputStream = entity.getContent(); // InputStream ist AutoCloseable
                     BufferedOutputStream outputStream = new BufferedOutputStream(new FileOutputStream(toFile))) {

                    byte[] buffer = new byte[bufferSize];
                    int numRead;
                    long totalBytesRead = 0;
                    int lastPercentage = -1;

                    while ((numRead = inputStream.read(buffer)) != -1) {
                        outputStream.write(buffer, 0, numRead);
                        totalBytesRead += numRead;

                        if (progressListener != null && contentLength > 0) {
                            int currentPercentage = (int) (100 * totalBytesRead / (double) contentLength);
                            if (currentPercentage > lastPercentage) {
                                progressListener.update(currentPercentage);
                                lastPercentage = currentPercentage;
                            }
                        }
                    }
                    if (progressListener != null && contentLength > 0 && lastPercentage < 100) {
                        progressListener.update(100);
                    }
                } finally {
                    EntityUtils.consume(entity);
                }
            }
        }
    }

    public void downloadAndUnzip(String url, String toFolder, final ProgressListener progressListener) throws IOException {
        HttpURLConnection conn = createConnection(url);
        final int length = conn.getContentLength();
        InputStream iStream = fetch(conn, false);

        new Unzipper().unzip(iStream, new File(toFolder), new ProgressListener() {
            @Override
            public void update(long sumBytes) {
                progressListener.update((int) (100 * sumBytes / length));
            }
        });
    }

    public String downloadAsString(String url, boolean readErrorStreamNoException) throws IOException {
        return Helper.isToString(fetch((HttpURLConnection) createConnection(url), readErrorStreamNoException));
    }
}
