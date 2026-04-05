package com.cozooo.dlc_fileops_helper;

import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class FileOpsHelperBootstrap {
    private static final Logger LOGGER = LoggerFactory.getLogger(FileOpsHelperBootstrap.class);
    private static final AtomicBoolean INITIALIZED = new AtomicBoolean(false);

    private FileOpsHelperBootstrap() {
    }

    public static void initialize() {
        if (!INITIALIZED.compareAndSet(false, true)) {
            return;
        }
        LOGGER.info("{} initialized. Reflective API class: {}",
            FileOpsHelperConstants.MOD_NAME,
            "com.cozooo.dlc_fileops_helper.api.FileOpsHelperApi");
    }
}
