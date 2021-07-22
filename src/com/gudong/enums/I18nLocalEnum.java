package com.gudong.enums;

/**
 * description i18n
 *
 * @author maggie
 * @date 2021-07-21 14:56
 */
public enum I18nLocalEnum {
    ZH_CN("cn","zh-CN","中文（简体）"),
    ZH_TW("tw","zh-TW","中文（繁体）"),
    EN("us","en","英语");

    private String fileSubFix;
    private String localKey;
    private String localName;

    I18nLocalEnum(String fileSubFix, String localKey, String localName) {
        this.fileSubFix = fileSubFix;
        this.localKey = localKey;
        this.localName = localName;
    }


    public static boolean isSupportFile(String path) {
        for (I18nLocalEnum support : I18nLocalEnum.values()) {
            if (path.contains(support.getFileSubFix())) {
                return true;
            }
        }
        return false;
    }

    public static I18nLocalEnum valueByFileName(String name) {
        for (I18nLocalEnum support : I18nLocalEnum.values()) {
            if (name.contains(support.getFileSubFix())) {
                return support;
            }
        }
        return null;
    }

    public String getFileSubFix() {
        return fileSubFix;
    }

    public String getLocalKey() {
        return localKey;
    }

    public String getLocalName() {
        return localName;
    }
}
