package com.gudong.utils;

import com.gudong.exception.NetworkException;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;
import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.Map;

import static org.apache.commons.compress.utils.IOUtils.closeQuietly;

/**
 * description http
 *
 * @author maggie
 * @date 2021-07-21 15:31
 */
public class HttpUtils {
    private static Map<String, String> defaultHead = new HashMap<>();

    static {
        // 设置通用的请求属性
        defaultHead.put("accept", "*/*");
        defaultHead.put("connection", "Keep-Alive");
        defaultHead.put("user-agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/79.0.3945.79 Safari/537.36");
        defaultHead.put("Accept-Language", "en,zh-CN;q=0.9,zh;q=0.8");
    }


    public static String get(String surl) {
        BufferedReader in = null;
        try {
            URLConnection con = openConnect(surl);
            con.connect();

            in = new BufferedReader(new InputStreamReader(con.getInputStream(), "utf-8"));
            String line;
            String result = "";
            while ((line = in.readLine()) != null) {
                result += line;
            }
            return result;
        } catch (Exception e) {
            throw new NetworkException(e.getLocalizedMessage());
        } finally {
            closeQuietly(in);
        }
    }

    public static String post(String surl, String body) {
        BufferedReader in = null;
        try {
            URLConnection con = openConnect(surl);
            OutputStream out = con.getOutputStream();
            out.write(body.getBytes());
            out.flush();

            con.connect();
            in = new BufferedReader(new InputStreamReader(con.getInputStream(), "utf-8"));
            String line;
            String result = "";
            while ((line = in.readLine()) != null) {
                result += line;
            }
            return result;
        } catch (Exception e) {
            throw new NetworkException(e.getLocalizedMessage());
        } finally {
            closeQuietly(in);
        }
    }

    /**
     * 打开链接
     *
     * @param surl
     * @return
     */
    private static URLConnection openConnect(String surl) throws Exception {
        if (surl.startsWith("https://")) {
            HttpsURLConnection.setDefaultHostnameVerifier(defaultHostnameVerifier);
            HttpsURLConnection con = (HttpsURLConnection) new URL(surl).openConnection();
            // 设置默认的请求头
            for (Map.Entry<String, String> stringStringEntry : defaultHead.entrySet()) {
                con.setRequestProperty(stringStringEntry.getKey(), stringStringEntry.getValue());
            }
            con.setConnectTimeout(10000);
            con.setReadTimeout(50000);

            // Prepare SSL Context
            TrustManager[] tm = {defaultCertificationTrustManger};
            SSLContext sslContext = SSLContext.getInstance("SSL", "SunJSSE");
            sslContext.init(null, tm, new java.security.SecureRandom());
            con.setSSLSocketFactory(sslContext.getSocketFactory());
            return con;
        } else {
            return new URL(surl).openConnection();
        }
    }


    private static TrustManager defaultCertificationTrustManger = new X509TrustManager() {
        private X509Certificate[] certificates;

        @Override
        public void checkClientTrusted(X509Certificate certificates[], String authType) {
            if (this.certificates == null) {
                this.certificates = certificates;
            }

        }

        @Override
        public void checkServerTrusted(X509Certificate[] ax509certificate, String s) {
            if (this.certificates == null) {
                this.certificates = ax509certificate;
            }
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return null;
        }
    };
    /**
     * 忽视证书HostName
     */
    private static HostnameVerifier defaultHostnameVerifier = new HostnameVerifier() {
        @Override
        public boolean verify(String s, SSLSession sslsession) {
            return true;
        }
    };
}
