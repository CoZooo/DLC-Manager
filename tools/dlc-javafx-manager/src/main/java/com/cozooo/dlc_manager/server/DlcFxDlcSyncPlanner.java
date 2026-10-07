package com.cozooo.dlc_manager.server;

import com.cozooo.dlc_manager.server.DlcFxDlcSyncSnapshot.FileState;
import com.cozooo.dlc_manager.server.DlcFxDlcSyncSnapshot.Item;
import com.cozooo.dlc_manager.server.DlcFxDlcSyncSnapshot.Kind;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** Pure planning over a {@link DlcFxDlcSyncSnapshot}: decides which DLC configs and files move in the chosen direction. */
public final class DlcFxDlcSyncPlanner {
    private DlcFxDlcSyncPlanner() {
    }

    public enum Direction {
        PUSH,
        PULL
    }

    public enum Status {
        TRANSFER,
        SOURCE_DATA_MISSING,
        TARGET_ONLY,
        SYNCED
    }

    public enum Change {
        NONE,
        MISSING,
        CHANGED,
        UNVERIFIED
    }

    public record Transfer(String rel, long size, String hash, boolean config) {
    }

    public record Row(Item item,
                      Status status,
                      Change configChange,
                      Change dataChange,
                      String dataRel,
                      List<Transfer> transfers) {
        public String key() {
            return item.key();
        }

        public Kind kind() {
            return item.kind();
        }

        public long transferBytes() {
            long total = 0L;
            for (Transfer transfer : transfers) {
                total += Math.max(0L, transfer.size());
            }
            return total;
        }
    }

    public record Plan(Direction direction, List<Row> rows) {
        public int count(Status status) {
            int count = 0;
            for (Row row : rows) {
                if (row.status() == status) {
                    count++;
                }
            }
            return count;
        }

        public int unverifiedCount() {
            int count = 0;
            for (Row row : rows) {
                if (row.dataChange() == Change.UNVERIFIED) {
                    count++;
                }
            }
            return count;
        }
    }

    public static Plan plan(DlcFxDlcSyncSnapshot snapshot, Direction direction) {
        Direction dir = direction == null ? Direction.PUSH : direction;
        List<Row> rows = new ArrayList<>();
        if (snapshot == null) {
            return new Plan(dir, rows);
        }
        boolean push = dir == Direction.PUSH;
        Map<String, FileState> sourceFiles = push ? snapshot.localFiles : snapshot.serverFiles;
        Map<String, FileState> targetFiles = push ? snapshot.serverFiles : snapshot.localFiles;
        for (Item item : snapshot.items) {
            FileState sourceConfig = push ? item.localConfig() : item.serverConfig();
            FileState targetConfig = push ? item.serverConfig() : item.localConfig();
            String dataRel = push ? item.localDataRel() : item.serverDataRel();
            if (sourceConfig == null) {
                rows.add(new Row(item, Status.TARGET_ONLY, Change.NONE, Change.NONE, dataRel, List.of()));
                continue;
            }
            List<Transfer> transfers = new ArrayList<>();
            Change dataChange = Change.NONE;
            FileState sourceData = dataRel == null ? null : sourceFiles.get(dataRel);
            if (dataRel != null && sourceData != null) {
                dataChange = compare(sourceData, targetFiles.get(dataRel));
                if (dataChange == Change.MISSING || dataChange == Change.CHANGED || dataChange == Change.UNVERIFIED) {
                    transfers.add(new Transfer(dataRel, sourceData.size(), sourceData.hash(), false));
                }
            }
            Change configChange = targetConfig == null
                    ? Change.MISSING
                    : Objects.equals(sourceConfig.hash(), targetConfig.hash()) ? Change.NONE : Change.CHANGED;
            if (configChange != Change.NONE) {
                transfers.add(new Transfer(item.key(), sourceConfig.size(), sourceConfig.hash(), true));
            }
            Status status;
            if (dataRel != null && sourceData == null) {
                status = Status.SOURCE_DATA_MISSING;
                transfers.clear();
            } else if (transfers.isEmpty()) {
                status = Status.SYNCED;
            } else {
                status = Status.TRANSFER;
            }
            rows.add(new Row(item, status, configChange, dataChange, dataRel, List.copyOf(transfers)));
        }
        rows.sort((left, right) -> {
            int order = Integer.compare(left.status().ordinal(), right.status().ordinal());
            return order != 0 ? order : left.key().compareToIgnoreCase(right.key());
        });
        return new Plan(dir, List.copyOf(rows));
    }

    public enum VerifyStatus {
        MISMATCH,
        MISSING,
        UNVERIFIED,
        OK
    }

    /** Hot-update style post-check: the target config must equal the source config and resolve to the same required file. */
    public record Verification(String key, String identifier, VerifyStatus status, String reason) {
    }

    public static List<Verification> verify(DlcFxDlcSyncSnapshot snapshot, Direction direction, Collection<String> keys) {
        List<Verification> out = new ArrayList<>();
        if (snapshot == null || keys == null) {
            return out;
        }
        boolean push = direction != Direction.PULL;
        Map<String, Item> index = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (Item item : snapshot.items) {
            index.put(item.key(), item);
        }
        Map<String, FileState> sourceFiles = push ? snapshot.localFiles : snapshot.serverFiles;
        Map<String, FileState> targetFiles = push ? snapshot.serverFiles : snapshot.localFiles;
        for (String key : keys) {
            Item item = index.get(key);
            if (item == null) {
                out.add(new Verification(key, key, VerifyStatus.MISSING, "config_missing"));
                continue;
            }
            FileState sourceConfig = push ? item.localConfig() : item.serverConfig();
            FileState targetConfig = push ? item.serverConfig() : item.localConfig();
            String sourceRel = push ? item.localDataRel() : item.serverDataRel();
            String targetRel = push ? item.serverDataRel() : item.localDataRel();
            VerifyStatus status;
            String reason;
            if (sourceConfig == null || targetConfig == null) {
                status = VerifyStatus.MISSING;
                reason = "config_missing";
            } else if (!Objects.equals(sourceConfig.hash(), targetConfig.hash())) {
                status = VerifyStatus.MISMATCH;
                reason = "config_changed";
            } else if (sourceRel == null && item.kind() == Kind.REQUIRED) {
                status = VerifyStatus.MISSING;
                reason = "data_missing";
            } else if (sourceRel == null) {
                status = VerifyStatus.OK;
                reason = "ok";
            } else if (targetRel == null) {
                status = VerifyStatus.MISSING;
                reason = "data_missing";
            } else if (!sourceRel.equalsIgnoreCase(targetRel)) {
                status = VerifyStatus.MISMATCH;
                reason = "data_redirected";
            } else {
                FileState sourceData = sourceFiles.get(sourceRel);
                FileState targetData = targetFiles.get(targetRel);
                Change change = sourceData == null ? Change.MISSING : compare(sourceData, targetData);
                status = switch (change) {
                    case NONE -> VerifyStatus.OK;
                    case UNVERIFIED -> VerifyStatus.UNVERIFIED;
                    case CHANGED -> VerifyStatus.MISMATCH;
                    case MISSING -> VerifyStatus.MISSING;
                };
                reason = switch (change) {
                    case NONE -> "ok";
                    case UNVERIFIED -> "data_unverified";
                    case CHANGED -> "data_changed";
                    case MISSING -> "data_missing";
                };
            }
            out.add(new Verification(item.key(), item.identifier(), status, reason));
        }
        out.sort((left, right) -> {
            int order = Integer.compare(left.status().ordinal(), right.status().ordinal());
            return order != 0 ? order : left.key().compareToIgnoreCase(right.key());
        });
        return out;
    }

    public static int countVerified(List<Verification> results, VerifyStatus status) {
        int count = 0;
        for (Verification result : results) {
            if (result.status() == status) {
                count++;
            }
        }
        return count;
    }

    /** Size first; the lightweight hash decides when both sides have one, otherwise equal size is reported as unverified. */
    static Change compare(FileState source, FileState target) {
        if (target == null) {
            return Change.MISSING;
        }
        if (source.size() != target.size()) {
            return Change.CHANGED;
        }
        if (source.hash() == null || target.hash() == null) {
            return Change.UNVERIFIED;
        }
        return source.hash().equals(target.hash()) ? Change.NONE : Change.CHANGED;
    }
}
