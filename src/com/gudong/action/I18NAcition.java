package com.gudong.action;

import com.gudong.utils.LogUtils;
import com.gudong.utils.NotifyUtils;
import com.gudong.utils.TranslateUtils;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * description
 *
 * @author maggie
 * @date 2021-07-21 13:43
 */
public class I18NAcition extends AnAction {
    private static final AtomicBoolean IS_TRANSLATEING = new AtomicBoolean(false);

    @Override
    public void actionPerformed(AnActionEvent event) {
        // 保证单次单个任务
        Project project = event.getProject();
        if (IS_TRANSLATEING.get()) {
            NotifyUtils.info("正在翻译其他文件，请稍后。。。",project);
            return;
        }
        IS_TRANSLATEING.set(true);
        VirtualFile file = CommonDataKeys.VIRTUAL_FILE.getData(event.getDataContext());
        final String path = file.getCanonicalPath();

        if (path.contains("cn") || path.contains("tw") || path.contains("us")) {
            NotifyUtils.info("开始翻译文件，请稍后。。。", project);
            final Project pj = project;
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        TranslateUtils.project = project;
                        TranslateUtils.translatePropFile(path);
                        NotifyUtils.info("翻译完成", pj);
                    } catch (Exception e) {
                        LogUtils.error("", e);
                        NotifyUtils.error("翻译出错\r\n" + e.getLocalizedMessage(), pj);
                    }
                }
            }).start();
        } else {
            NotifyUtils.waring("暂不支持非i18n文件", project);
        }
        IS_TRANSLATEING.set(false);
    }
}
