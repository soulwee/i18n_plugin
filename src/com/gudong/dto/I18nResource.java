package com.gudong.dto;

import com.gudong.enums.I18nLocalEnum;

import java.io.File;
import java.util.Map;

/**
 * description
 *
 * @author maggie
 * @date 2021-07-21 15:01
 */
public class I18nResource {

    private I18nLocalEnum i18nLocalEnum;

    private File file;

    private Map<String, String> props;

    public I18nLocalEnum getI18nLocalEnum() {
        return i18nLocalEnum;
    }

    public void setI18nLocalEnum(I18nLocalEnum i18nLocalEnum) {
        this.i18nLocalEnum = i18nLocalEnum;
    }

    public File getFile() {
        return file;
    }

    public void setFile(File file) {
        this.file = file;
    }

    public Map<String, String> getProps() {
        return props;
    }

    public void setProps(Map<String, String> props) {
        this.props = props;
    }
}
