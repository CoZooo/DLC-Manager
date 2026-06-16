package com.cozooo.dlc_manager.client.pack;

import com.cozooo.dlc_manager.core.DcConfigIO;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

final class DlcPackDcFiles {
    private DlcPackDcFiles() {
    }

    static boolean writeAtomic(Path path, JsonObject obj) {
        if (path == null || obj == null) {
            return false;
        }
        Path target = DcConfigIO.toDcPath(path);
        if (target == null) {
            return false;
        }
        Path parent = target.getParent();
        Path temp = null;
        try {
            if (parent != null) {
                Files.createDirectories(parent);
                temp = Files.createTempFile(parent, target.getFileName().toString() + ".", ".dc");
            } else {
                temp = Files.createTempFile(target.getFileName().toString() + ".", ".dc");
            }
            if (!DcConfigIO.write(temp, obj, DcConfigIO.Layout.GENERIC)) {
                return false;
            }
            moveReplacing(temp, target);
            temp = null;
            if (!target.equals(path)) {
                Files.deleteIfExists(path);
            }
            return true;
        } catch (IOException ex) {
            return false;
        } finally {
            if (temp != null) {
                try {
                    Files.deleteIfExists(temp);
                } catch (IOException ignored) {
                }
            }
        }
    }

    private static void moveReplacing(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ex) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
