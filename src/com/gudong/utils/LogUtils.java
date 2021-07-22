package com.gudong.utils;

/**
 * description 日志
 *
 * @author maggie
 * @date 2021-07-21 14:52
 */
public class LogUtils {
    public static void info(String msg) {
        System.out.println("translate-properties : " + msg);
    }

    public static void error(String msg, Exception e) {
        System.err.println("translate-properties : " + msg);
        e.printStackTrace();
    }
}
