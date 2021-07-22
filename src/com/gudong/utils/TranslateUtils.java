package com.gudong.utils;

import com.gudong.dto.I18nResource;
import com.gudong.enums.I18nLocalEnum;
import com.gudong.exception.TranslateException;
import org.apache.commons.lang.StringUtils;
import org.codehaus.jettison.json.JSONArray;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * description 翻译工具
 *
 * @author maggie
 * @date 2021-07-21 14:51
 */
public class TranslateUtils {
    private static AtomicBoolean stopGoogleTranslate = new AtomicBoolean(false);
    private static Long period = 5 * 60 * 1000L;// 5分钟后再重试
    private static Timer changeStopGoogleTranslateTimer = null;

    private static void startTimer() {
        if (changeStopGoogleTranslateTimer == null) {
            changeStopGoogleTranslateTimer = new Timer();
            changeStopGoogleTranslateTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    stopGoogleTranslate.set(false);
                    changeStopGoogleTranslateTimer.cancel();
                    changeStopGoogleTranslateTimer = null;
                }
            }, period);
        }
    }

    /**
     * 翻译国际化资源文件
     *
     * @param path
     */
    public static void translatePropFile(String path) throws Exception {
        if (!I18nLocalEnum.isSupportFile(path)) {
            throw new TranslateException("未知国际化资源文件!");
        }

        File targetFile = new File(path);
        //locale
        //  xxx-cn/component.js
        //  xxx-tw/component.js
        //  xxx-us/component.js

        //component
        final String moduleName = getModuleNameByFileName(targetFile.getName());
        //locale
        File parentPath = targetFile.getParentFile().getParentFile();

        Map<I18nLocalEnum, I18nResource> res = new HashMap<>(I18nLocalEnum.values().length);

        File[] dirs = parentPath.listFiles((f, path1) -> path1.contains("cn") || path1.contains("tw") || path1.contains("us"));
        if (dirs == null) {
            throw new TranslateException("未发现国际化资源!");
        }
        List<File> files = new ArrayList<>();
        Arrays.stream(dirs).forEach(dir ->
                files.add(Arrays.stream(dir.listFiles(f -> f.getName().contains(moduleName))).collect(Collectors.toList()).get(0))
        );

        for (File file : files) {
            I18nResource i = loadI18nResource(file);
            res.put(i.getI18nLocalEnum(), i);
        }

        final Map<String, String> allI18nKeys = new LinkedHashMap<>();
        I18nResource zhcnI18n = res.get(I18nLocalEnum.ZH_CN);
        if (zhcnI18n == null) {
            throw new TranslateException("缺少中文资源无法翻译!");
        }
        for (Map.Entry<String, String> o : res.get(I18nLocalEnum.ZH_CN).getProps().entrySet()) {
            allI18nKeys.put(o.getKey(), o.getValue());
        }

        final CountDownLatch countDownLatch = new CountDownLatch(I18nLocalEnum.values().length);

        final AtomicBoolean allHasError = new AtomicBoolean(false);
        for (final I18nLocalEnum i18nLocalEnum : I18nLocalEnum.values()) {
            I18nResource i18nResource = res.get(i18nLocalEnum);
            File file = null;
            Map<String, String> prop = null;
            if (i18nResource != null) {
                file = i18nResource.getFile();
                prop = i18nResource.getProps();
            } else {
                file = new File(parentPath, moduleName + ".js");
                prop = new HashMap<>();
            }

            final Map<String, String> finalProp = prop;
            final File finalFile = file;
            // 多个线程去翻译
            new Thread(() -> {
                AtomicBoolean hasError = new AtomicBoolean(false);
                try (RandomAccessFile raf = new RandomAccessFile(finalFile, "rw");) {
                    boolean flag = true;
                    for (Map.Entry<String, String> entry : allI18nKeys.entrySet()) {
                        String key = entry.getKey(), val = entry.getValue();
                        if (finalProp.containsKey(key)) {
                            continue;
                        }
                        try {
                            if (flag) {
                                long pos = finalFile.length() - 2;
                                raf.seek(pos);
                                int len = 0;
                                while (--pos >= 0 && (len = raf.read()) != -1) {
                                    if (len == '}') {
                                        //回到原来的位置
                                        raf.seek(pos - 1);
                                        raf.write(",\n".getBytes());
                                        flag = false;
                                        break;
                                    }
                                    raf.seek(pos);
                                }
                            }

                            if (StringUtils.isNotBlank(val)) {
                                //  String afterText = doTranslate(val, I18nLocalEnum.ZH_CN, i18nLocalEnum);
                                String afterText = translate(I18nLocalEnum.ZH_CN.getLocalKey(), i18nLocalEnum.getLocalKey(), val);
                                //  writer.write(stringToUnicode(afterText));
                                String s = key + ": " + afterText + "\n";
                                raf.write(s.getBytes());
                                LogUtils.info(val + " ====> " + afterText);
                            }
                        } catch (Exception e) {
                            LogUtils.error("翻译出错啦!", e);
                            hasError.set(true);
                        }
                    }
                    raf.write("}".getBytes());
                } catch (Exception e) {
                    LogUtils.error("", e);
                    hasError.set(true);
                } finally {
                    countDownLatch.countDown();
                    if (hasError.get()) {
                        allHasError.set(true);
                    }
                }
            }).start();
        }
        countDownLatch.await();
        if (allHasError.get()) {
            throw new TranslateException("已经翻译完成，但翻译过程出现了错误。请检查网络或重新翻译一次！");
        }
    }

    /**
     * 字符串转unicode
     *
     * @param str
     * @return
     */
    public static String stringToUnicode(String str) {
        StringBuffer sb = new StringBuffer();
        char[] c = str.toCharArray();
        for (int i = 0; i < c.length; i++) {
            char current = c[i];
            if (current <= 0x7f) {// 英文不转
                sb.append(current);
                continue;
            }
            sb.append("\\u" + Integer.toHexString(current).toUpperCase());
        }
        return sb.toString();
    }

    public static void main(String[] args) {
        final String s = unicodeToString("u003e");
        System.out.println(s);
    }

    /**
     * unicode转字符串
     *
     * @param unicode
     * @return
     */
    public static String unicodeToString(String unicode) {
        StringBuffer sb = new StringBuffer();
        String[] hex = unicode.split("\\\\u");
        for (int i = 1; i < hex.length; i++) {
            int index = Integer.parseInt(hex[i], 16);
            sb.append((char) index);
        }
        return sb.toString();
    }


    private static String doTranslate(String znText, I18nLocalEnum zhCn, I18nLocalEnum target) throws Exception {
        /**
         [[["Wo ist meine Sonnenbrille","where are my sunglasses",null,null,1]]\n,null,\"en\",null,null,[[\"where are my sunglasses\",null,[[\"Wo ist meine Sonnenbrille\",1000,true,false]\n,[\"wo sind meine Sonnenbrille\",0,true,false]\n]\n,[[0,23]\n]\n,\"where are my sunglasses\",0,0]\n]\n,1.0,[]\n,[[\"en\"]\n,null,[1.0]\n,[\"en\"]\n]\n]\n\"
         */
        String reRaw = "";
        try {
            if (!stopGoogleTranslate.get()) {//如果google服务被封了  使用代理
                reRaw = googleTranslate(znText, zhCn, target);
            } else {
                reRaw = proxyTranslate(znText, zhCn, target);
            }
        } catch (Exception e) {// 多试一次
            if (!stopGoogleTranslate.get()) {//如果google服务被封了  使用代理
                reRaw = googleTranslate(znText, zhCn, target);
            } else {
                reRaw = proxyTranslate(znText, zhCn, target);
            }
        }
        String[] strings = reRaw.split("\"");
        if (strings.length > 0) {
            return strings[1];
        }
        return "";
    }
//https://translate.googleapis.com/translate_a/single?
// client=gtx&sl=zh_CN&tl=zh_TW&dt=t&q=
// +%27%E6%B5%8B%E8%AF%95%E7%AC%AC%E4%B8%80%E6%AC%A1%E5%91%98%E5%B7%A5%E5%AF%BC%E5%85%A5%27%2C

    //https://translate.google.cn/?sl=auto&tl=en&text=%E5%85%B1%E6%9C%89rrr%0A%0A&op=translate
    private static String gooleTranslateApiCNUrl = "https://translate.google.cn/?" +
            "sl=%s" +// 当前的语言
            "&tl=%s" +// 目标语言
            "&text=%s&op=translate";// 要翻译的文字
    /**
     * google 原生翻译接口
     */
    private static String gooleTranslateApiUrl = "https://translate.googleapis.com/translate_a/single?client=gtx" +
            "&sl=%s" +// 当前的语言
            "&tl=%s&dt=t" +// 目标语言
            "&q=%s";// 要翻译的文字

    private static String googleTranslate(String znText, I18nLocalEnum zhCn, I18nLocalEnum target) {
        String reRaw;
        try {
            String url = String.format(gooleTranslateApiCNUrl, zhCn.getLocalKey(), target.getLocalKey(), URLEncoder.encode(znText, "utf-8"));
            reRaw = HttpUtils.get(url);
        } catch (Exception e) {
            LogUtils.error("", e);
            stopGoogleTranslate.set(true);
            startTimer();
            reRaw = proxyTranslate(znText, zhCn, target);
        }
        return reRaw;
    }

    public static String translate(String langFrom, String langTo, String word) throws Exception {
        String url = "https://translate.googleapis.com/translate_a/single?" +
                "client=gtx&" +
                "sl=" + langFrom +
                "&tl=" + langTo +
                "&dt=t&q=" + URLEncoder.encode(word, "UTF-8");
        URL obj = new URL(url);
        HttpURLConnection con = (HttpURLConnection) obj.openConnection();
        con.setRequestProperty("User-Agent", "Mozilla/5.0");
        BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
        String inputLine;
        StringBuilder response = new StringBuilder();
        while ((inputLine = in.readLine()) != null) {
            response.append(inputLine);
            //System.out.println(inputLine);
        }
        in.close();
        return parseResult(response.toString());
    }

    private static String parseResult(String inputJson) throws Exception {
        /*
         * inputJson for word 'hello' translated to language Hindi from English-
         * [[["你好，世界","hello world",null,null,1]],null,"en",null,null,null,null,[]]
           [[["你好時間","你好时间",null,null,0]],null,"zh-CN",null,null,null,null,[]]
         * We have to get 'नमस्ते ' from this json.
         */
        JSONArray jsonArray = new JSONArray(inputJson);
        JSONArray jsonArray2 = (JSONArray) jsonArray.get(0);
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < jsonArray2.length(); i++) {
            result.append(((JSONArray) jsonArray2.get(i)).get(0).toString());
        }
        return result.toString();
    }

    /**
     * 代理接口
     * https://github.com/guyrotem/google-translate-server
     */
    private static String gooleTranslateProxyApiUrl = "https://google-translate-proxy.herokuapp.com/api/translate?query=%s&targetLang=%s&sourceLang=%s";

    private static String proxyTranslate(String znText, I18nLocalEnum zhCn, I18nLocalEnum target) {
        try {
            String url = String.format(gooleTranslateProxyApiUrl, URLEncoder.encode(znText, "utf-8"), target.getLocalKey(), zhCn.getLocalKey());
            String reRaw = HttpUtils.get(url);
            Map data = JSONUtils.parseJson(reRaw, Map.class);
            String originalResponse = ((String) data.get("originalResponse"));
            if (originalResponse.startsWith("\"")) {
                originalResponse = originalResponse.substring(1, originalResponse.length() - 1);
            }
            return originalResponse;
        } catch (Exception e) {
            LogUtils.error("", e);
            throw new TranslateException("你操作太过频繁，导致googgle翻译拒绝了服务，请稍后再试!");
        }
    }

    private static I18nResource loadI18nResource(File file) throws Exception {
        I18nResource i18n = new I18nResource();
        BufferedReader br = new BufferedReader(new FileReader(file));
        String contentLine;
        Map<String, String> props = new LinkedHashMap<>();
        while ((contentLine = br.readLine()) != null) {
            //读取每一行，并解析 xxx : 'Text to be translated',
            System.out.println(contentLine);
            //将每一行追加到arr1
            if (contentLine.contains(":")) {
                final String[] split = contentLine.split(":", 2);
                props.put(split[0], split[1]);
            }

        }
        br.close();
        i18n.setProps(props);
        i18n.setFile(file);
        i18n.setI18nLocalEnum(I18nLocalEnum.valueByFileName(file.getParent()));
        return i18n;
    }

    private static String getModuleNameByFileName(String name) {
        return name.substring(0, name.indexOf("."));
    }
}
