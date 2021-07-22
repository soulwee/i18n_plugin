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
        if (IS_TRANSLATEING.get()) {
            NotifyUtils.info("正在翻译其他文件，请稍后。。。", event.getProject());
            return;
        }
        IS_TRANSLATEING.set(true);
        VirtualFile file = CommonDataKeys.VIRTUAL_FILE.getData(event.getDataContext());
        final String path = file.getCanonicalPath();

        if (path.contains("cn") || path.contains("tw") || path.contains("us")) {
            NotifyUtils.info("正在开始翻译文件，请稍后。。。", event.getProject());
            final Project pj = event.getProject();
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        TranslateUtils.translatePropFile(path);
                        NotifyUtils.info("转换完成", pj);
                    } catch (Exception e) {
                        LogUtils.error("", e);
                        NotifyUtils.error("转换出错\r\n" + e.getLocalizedMessage(), pj);
                    }
                }
            }).start();
        } else {
            NotifyUtils.waring("暂不支持非Properties文件", event.getProject());
        }
        IS_TRANSLATEING.set(false);
    }
}
