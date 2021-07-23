package com.gudong.utils;

import com.gudong.dto.I18nResource;
import com.gudong.enums.I18nLocalEnum;
import com.gudong.exception.TranslateException;
import com.intellij.openapi.project.Project;
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
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * description 翻译工具
 *
 * @author maggie
 * @date 2021-07-21 14:51
 */
public class TranslateUtils {

    public static Project project;

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
        final CountDownLatch countDownLatch = new CountDownLatch(I18nLocalEnum.values().length - 1);
        AtomicReference<String> errMsg = new AtomicReference<>("");
        final AtomicBoolean allHasError = new AtomicBoolean(false);
        for (final I18nLocalEnum i18nLocalEnum : I18nLocalEnum.values()) {
            //中文不需要再翻译
            if (i18nLocalEnum == I18nLocalEnum.ZH_CN) {
                continue;
            }
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
                    // 如果 size 没变说明文件还没保存，所以读取的还是之前的数据
//                    System.out.println(allI18nKeys.size());
//                    System.out.println(finalProp.size());
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
                                    //  System.out.println(len);
                                    if (len == '}') {
                                        //回到原来的位置
                                        raf.seek(pos - 1);
                                        //ascii 44(逗号) , 13(CR carriage return)回车 10(LF line feed)换行 125(大括号) }
                                        raf.write(new byte[]{44, 13, 10});
                                        flag = false;
                                        break;
                                    }
                                    raf.seek(pos);
                                }
                            }

                            if (StringUtils.isNotBlank(val)) {
                                //  String afterText = doTranslate(val, I18nLocalEnum.ZH_CN, i18nLocalEnum);
                                String afterText = translate(I18nLocalEnum.ZH_CN.getLocalKey(), i18nLocalEnum.getLocalKey(), val, 1);
                                //  writer.write(stringToUnicode(afterText));
                                String s = key + ": " + afterText;
                                raf.write(s.getBytes());
                                raf.write(new byte[]{13, 10});
                                LogUtils.info(val + " ====> " + afterText);
                            }
                        } catch (Exception e) {
                            errMsg.set(e.getMessage());
                            LogUtils.error("翻译出错啦!", e);
                            hasError.set(true);
                        }
                    }
                    if (!flag) {
                        raf.write(new byte[]{125, 13, 10});
                    }
                } catch (Exception e) {
                    LogUtils.error(e.getMessage(), e);
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
            throw new TranslateException("" + errMsg);
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

    private static String translate(String langFrom, String langTo, String word, int type) throws Exception {
        String s = type == 1 ? "https://translate.googleapis.com/translate_a/single?" +
                "client=gtx&" +
                "sl=" + langFrom +
                "&tl=" + langTo +
                "&dt=t&q=" : "http://translate.google.cn/translate_a/single?" +
                "client=gtx&" +
                "sl=" + langFrom +
                "&tl=" + langTo +
                "&dt=t&q=";
        String url = s + URLEncoder.encode(word, "UTF-8");
        URL obj = new URL(url);
        HttpURLConnection con = (HttpURLConnection) obj.openConnection();
        con.setRequestProperty("User-Agent", "Mozilla/5.0");
        BufferedReader in = null;
        try {
            in = new BufferedReader(new InputStreamReader(con.getInputStream()));
        } catch (Exception e) {
            LogUtils.error(e.getMessage(), e);
            if (type == 1) {
                NotifyUtils.waring("请求频繁，咕噜咕噜api拒绝了服务!" + e.getMessage(), project);
                NotifyUtils.info("正在使用咕噜咕噜cn请求...", project);
            } else {
                throw new TranslateException("请求频繁，咕噜咕噜cn也拒绝了服务!" + e.getMessage());
            }
            return translate(langFrom, langTo, word, 2);
        }
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

    private static I18nResource loadI18nResource(File file) throws Exception {
        I18nResource i18n = new I18nResource();
        BufferedReader br = new BufferedReader(new FileReader(file));
        String contentLine;
        Map<String, String> props = new LinkedHashMap<>();
        while ((contentLine = br.readLine()) != null) {
            //读取每一行，并解析 xxx : 'Text to be translated',
            //    System.out.println(contentLine);
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
