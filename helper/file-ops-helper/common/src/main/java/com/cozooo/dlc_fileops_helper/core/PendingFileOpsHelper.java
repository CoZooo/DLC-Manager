package com.cozooo.dlc_fileops_helper.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class PendingFileOpsHelper {
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
    private static final Object INDEX_MUTEX = new Object();
    private static final String SCRIPT_NAME = "dlc-pending-file-ops.ps1";
    private static final String INDEX_FILE_NAME = "index.json";
    private static final String LOCK_FILE_NAME = "index.lock";
    private static final int INDEX_VERSION = 1;

    private PendingFileOpsHelper() {
    }

    public static boolean isSupported() {
        String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        return osName.contains("win");
    }

    public static ScheduleResult scheduleDelete(List<Path> rawPaths) {
        if (rawPaths == null || rawPaths.isEmpty()) {
            return ScheduleResult.error("No paths provided.");
        }
        List<PendingFileOp> operations = new ArrayList<>();
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (Path path : rawPaths) {
            if (path == null) {
                continue;
            }
            normalized.add(path.toString());
        }
        for (String path : normalized) {
            PendingFileOp op = new PendingFileOp();
            op.type = "delete";
            op.path = path;
            operations.add(op);
        }
        return schedule(operations);
    }

    public static ScheduleResult scheduleMove(Path source, Path target, boolean replaceExisting) {
        if (source == null || target == null) {
            return ScheduleResult.error("Source and target are required.");
        }
        PendingFileOp op = new PendingFileOp();
        op.type = "move";
        op.source = source.toString();
        op.target = target.toString();
        op.replaceExisting = replaceExisting;
        return schedule(List.of(op));
    }

    public static ScheduleResult scheduleCopy(Path source, Path target, boolean replaceExisting) {
        if (source == null || target == null) {
            return ScheduleResult.error("Source and target are required.");
        }
        PendingFileOp op = new PendingFileOp();
        op.type = "copy";
        op.source = source.toString();
        op.target = target.toString();
        op.replaceExisting = replaceExisting;
        return schedule(List.of(op));
    }

    public static ScheduleResult scheduleWriteString(Path target, String content) {
        if (target == null) {
            return ScheduleResult.error("Target is required.");
        }
        PendingFileOp op = new PendingFileOp();
        op.type = "write_text";
        op.target = target.toString();
        op.contentBase64 = Base64.getEncoder().encodeToString(
            (content == null ? "" : content).getBytes(StandardCharsets.UTF_8));
        return schedule(List.of(op));
    }

    public static ScheduleResult scheduleWriteBytes(Path target, byte[] content) {
        if (target == null) {
            return ScheduleResult.error("Target is required.");
        }
        PendingFileOp op = new PendingFileOp();
        op.type = "write_bytes";
        op.target = target.toString();
        op.contentBase64 = Base64.getEncoder().encodeToString(content == null ? new byte[0] : content);
        return schedule(List.of(op));
    }

    public static ScheduleResult schedule(List<PendingFileOp> operations) {
        if (!isSupported()) {
            return ScheduleResult.error("Pending file operations are only supported on Windows.");
        }
        List<PendingFileOp> normalizedOps = normalize(operations);
        if (normalizedOps.isEmpty()) {
            return ScheduleResult.error("No valid operations to schedule.");
        }
        Path taskRoot = HelperPaths.pendingOpsRoot();
        Path indexFile = taskRoot.resolve(INDEX_FILE_NAME);
        try {
            Files.createDirectories(taskRoot);
            Path scriptFile = taskRoot.resolve(SCRIPT_NAME);
            if (!Files.exists(scriptFile)) {
                Files.writeString(scriptFile, buildPowerShellScript(), StandardCharsets.UTF_8);
            }

            synchronized (INDEX_MUTEX) {
                PendingFileOpsIndex index = readIndex(indexFile);
                long now = System.currentTimeMillis();
                index.version = INDEX_VERSION;
                index.pid = ProcessHandle.current().pid();
                if (index.operations == null) {
                    index.operations = new ArrayList<>();
                }
                for (PendingFileOp op : normalizedOps) {
                    mergeOperation(index.operations, op, now);
                }
                index.updatedAt = now;
                writeIndex(indexFile, index);
            }

            startWorker(scriptFile, indexFile);
            return ScheduleResult.success(indexFile, normalizedOps.size());
        } catch (IOException e) {
            return ScheduleResult.error("Failed to schedule pending file operations: " + safeMessage(e));
        }
    }

    public static PendingIndexResult getPendingOperations() {
        Path indexFile = HelperPaths.pendingOpsRoot().resolve(INDEX_FILE_NAME);
        synchronized (INDEX_MUTEX) {
            PendingFileOpsIndex index = readIndex(indexFile);
            List<PendingFileOp> operations = new ArrayList<>();
            if (index.operations != null) {
                for (PendingFileOp op : index.operations) {
                    operations.add(copyOperation(op));
                }
            }
            return new PendingIndexResult(indexFile, operations);
        }
    }

    public static CancelResult cancelOperationsByTarget(List<Path> rawPaths) {
        if (!isSupported()) {
            return CancelResult.error("Pending file operations are only supported on Windows.");
        }
        LinkedHashSet<String> targetKeys = new LinkedHashSet<>();
        if (rawPaths != null) {
            for (Path rawPath : rawPaths) {
                if (rawPath == null) {
                    continue;
                }
                Path normalized = resolvePath(rawPath.toString());
                if (normalized != null) {
                    targetKeys.add(normalized.toString());
                }
            }
        }
        if (targetKeys.isEmpty()) {
            return CancelResult.success(HelperPaths.pendingOpsRoot().resolve(INDEX_FILE_NAME), 0);
        }
        Path taskRoot = HelperPaths.pendingOpsRoot();
        Path indexFile = taskRoot.resolve(INDEX_FILE_NAME);
        try {
            Files.createDirectories(taskRoot);
            synchronized (INDEX_MUTEX) {
                PendingFileOpsIndex index = readIndex(indexFile);
                int removed = removeMatchingOperations(index.operations, targetKeys);
                index.updatedAt = System.currentTimeMillis();
                if (index.operations == null || index.operations.isEmpty()) {
                    Files.deleteIfExists(indexFile);
                } else {
                    writeIndex(indexFile, index);
                }
                return CancelResult.success(indexFile, removed);
            }
        } catch (IOException e) {
            return CancelResult.error("Failed to cancel pending file operations: " + safeMessage(e));
        }
    }

    public static List<String> supportedTypes() {
        return List.of("delete", "move", "copy", "write_text", "write_bytes", "mkdir");
    }

    private static void startWorker(Path script, Path indexFile) throws IOException {
        new ProcessBuilder(
            "powershell.exe",
            "-NoProfile",
            "-NonInteractive",
            "-ExecutionPolicy", "Bypass",
            "-File", script.toString(),
            "-IndexFile", indexFile.toString(),
            "-LockFile", indexFile.resolveSibling(LOCK_FILE_NAME).toString())
            .directory(HelperPaths.gameDir().toFile())
            .start();
    }

    private static PendingFileOpsIndex readIndex(Path indexFile) {
        if (!Files.isRegularFile(indexFile)) {
            return new PendingFileOpsIndex();
        }
        try {
            String raw = Files.readString(indexFile, StandardCharsets.UTF_8);
            PendingFileOpsIndex index = GSON.fromJson(raw, PendingFileOpsIndex.class);
            if (index == null) {
                index = new PendingFileOpsIndex();
            }
            if (index.operations == null) {
                index.operations = new ArrayList<>();
            }
            return index;
        } catch (Throwable ignored) {
            return new PendingFileOpsIndex();
        }
    }

    private static void writeIndex(Path indexFile, PendingFileOpsIndex index) throws IOException {
        Path parent = indexFile.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(indexFile, GSON.toJson(index), StandardCharsets.UTF_8);
    }

    private static void mergeOperation(List<PendingFileOp> existingOps, PendingFileOp incoming, long now) {
        if (existingOps == null) {
            return;
        }
        String targetKey = operationTargetKey(incoming);
        if (targetKey != null && !targetKey.isBlank()) {
            LinkedHashSet<String> targetKeys = new LinkedHashSet<>();
            targetKeys.add(targetKey);
            removeMatchingOperations(existingOps, targetKeys);
        }
        PendingFileOp stored = copyOperation(incoming);
        stored.id = UUID.randomUUID().toString();
        stored.createdAt = now;
        stored.updatedAt = now;
        existingOps.add(stored);
    }

    private static int removeMatchingOperations(List<PendingFileOp> operations, LinkedHashSet<String> targetKeys) {
        if (operations == null || operations.isEmpty() || targetKeys == null || targetKeys.isEmpty()) {
            return 0;
        }
        int removed = 0;
        for (int i = operations.size() - 1; i >= 0; i--) {
            PendingFileOp op = operations.get(i);
            String targetKey = operationTargetKey(op);
            if (targetKey != null && targetKeys.contains(targetKey)) {
                operations.remove(i);
                removed++;
            }
        }
        return removed;
    }

    private static String operationTargetKey(PendingFileOp op) {
        if (op == null || op.type == null) {
            return null;
        }
        return switch (op.type) {
            case "delete" -> normalizeKey(op.path);
            case "move", "copy", "write_text", "write_bytes", "mkdir" -> normalizeKey(op.target);
            default -> null;
        };
    }

    private static String normalizeKey(String raw) {
        Path normalized = resolvePath(raw);
        if (normalized != null) {
            return normalized.toString();
        }
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return raw;
    }

    private static PendingFileOp copyOperation(PendingFileOp raw) {
        PendingFileOp copy = new PendingFileOp();
        if (raw == null) {
            return copy;
        }
        copy.id = raw.id;
        copy.type = raw.type;
        copy.path = raw.path;
        copy.source = raw.source;
        copy.target = raw.target;
        copy.replaceExisting = raw.replaceExisting;
        copy.contentBase64 = raw.contentBase64;
        copy.createdAt = raw.createdAt;
        copy.updatedAt = raw.updatedAt;
        return copy;
    }

    private static List<PendingFileOp> normalize(List<PendingFileOp> operations) {
        List<PendingFileOp> normalized = new ArrayList<>();
        if (operations == null) {
            return normalized;
        }
        for (PendingFileOp raw : operations) {
            if (raw == null || raw.type == null || raw.type.isBlank()) {
                continue;
            }
            String type = raw.type.trim().toLowerCase(Locale.ROOT);
            PendingFileOp op = new PendingFileOp();
            op.type = type;
            op.replaceExisting = raw.replaceExisting;
            switch (type) {
                case "delete" -> {
                    Path path = resolvePath(raw.path);
                    if (path == null) {
                        continue;
                    }
                    op.path = path.toString();
                }
                case "move", "copy" -> {
                    Path source = resolvePath(raw.source);
                    Path target = resolvePath(raw.target);
                    if (source == null || target == null) {
                        continue;
                    }
                    op.source = source.toString();
                    op.target = target.toString();
                }
                case "write_text", "write_bytes", "mkdir" -> {
                    Path target = resolvePath(raw.target);
                    if (target == null) {
                        continue;
                    }
                    op.target = target.toString();
                    op.contentBase64 = raw.contentBase64;
                }
                default -> {
                    continue;
                }
            }
            normalized.add(op);
        }
        return normalized;
    }

    private static Path resolvePath(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            Path path = Path.of(raw);
            if (!path.isAbsolute()) {
                path = HelperPaths.gameDir().resolve(path);
            }
            return path.toAbsolutePath().normalize();
        } catch (InvalidPathException ignored) {
            return null;
        }
    }

    private static String safeMessage(Throwable throwable) {
        String message = throwable == null ? "" : throwable.getMessage();
        return (message == null || message.isBlank()) ? "Unknown error." : message;
    }

    private static String buildPowerShellScript() {
        return """
param(
  [string]$IndexFile,
  [string]$LockFile
)

$ErrorActionPreference = 'SilentlyContinue'
if (-not $IndexFile -or -not (Test-Path -LiteralPath $IndexFile)) {
  exit 0
}
if (-not $LockFile) {
  $LockFile = Join-Path (Split-Path -Parent $IndexFile) 'index.lock'
}

function Ensure-Parent([string]$PathValue) {
  if ([string]::IsNullOrWhiteSpace($PathValue)) {
    return
  }
  $parent = Split-Path -Parent $PathValue
  if (-not [string]::IsNullOrWhiteSpace($parent)) {
    New-Item -ItemType Directory -Force -Path $parent | Out-Null
  }
}

function Read-Index {
  if (-not (Test-Path -LiteralPath $IndexFile)) {
    return $null
  }
  $raw = Get-Content -LiteralPath $IndexFile -Raw
  if ([string]::IsNullOrWhiteSpace($raw)) {
    return $null
  }
  return ($raw | ConvertFrom-Json)
}

function Write-Index($index) {
  if ($null -eq $index -or $null -eq $index.operations -or @($index.operations).Count -eq 0) {
    Remove-Item -LiteralPath $IndexFile -Force -ErrorAction SilentlyContinue
    return
  }
  $index | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $IndexFile -Encoding UTF8
}

function Wait-For-ParentExit {
  for ($i = 0; $i -lt 600; $i++) {
    $index = Read-Index
    if ($null -eq $index) {
      return
    }
    $pidValue = 0
    try {
      $pidValue = [int64]$index.pid
    } catch {
      $pidValue = 0
    }
    if ($pidValue -le 0) {
      break
    }
    $proc = Get-Process -Id $pidValue -ErrorAction SilentlyContinue
    if ($null -eq $proc) {
      break
    }
    Start-Sleep -Seconds 1
  }
}

function Invoke-Op($op) {
  $type = [string]$op.type
  switch ($type) {
    'delete' {
      $path = [string]$op.path
      if ([string]::IsNullOrWhiteSpace($path)) { return $true }
      if (-not (Test-Path -LiteralPath $path)) { return $true }
      Remove-Item -LiteralPath $path -Recurse -Force -ErrorAction SilentlyContinue
      return (-not (Test-Path -LiteralPath $path))
    }
    'move' {
      $source = [string]$op.source
      $target = [string]$op.target
      if ([string]::IsNullOrWhiteSpace($source) -or [string]::IsNullOrWhiteSpace($target)) { return $true }
      if (-not (Test-Path -LiteralPath $source)) { return $true }
      Ensure-Parent $target
      if ($op.replaceExisting -and (Test-Path -LiteralPath $target)) {
        Remove-Item -LiteralPath $target -Recurse -Force -ErrorAction SilentlyContinue
      }
      Move-Item -LiteralPath $source -Destination $target -Force -ErrorAction SilentlyContinue
      return ((-not (Test-Path -LiteralPath $source)) -and (Test-Path -LiteralPath $target))
    }
    'copy' {
      $source = [string]$op.source
      $target = [string]$op.target
      if ([string]::IsNullOrWhiteSpace($source) -or [string]::IsNullOrWhiteSpace($target)) { return $true }
      if (-not (Test-Path -LiteralPath $source)) { return $false }
      if ((-not $op.replaceExisting) -and (Test-Path -LiteralPath $target)) { return $true }
      Ensure-Parent $target
      Copy-Item -LiteralPath $source -Destination $target -Recurse -Force -ErrorAction SilentlyContinue
      return (Test-Path -LiteralPath $target)
    }
    'write_text' {
      $target = [string]$op.target
      if ([string]::IsNullOrWhiteSpace($target)) { return $true }
      Ensure-Parent $target
      $content = ''
      if ($op.contentBase64) {
        $content = [System.Text.Encoding]::UTF8.GetString([System.Convert]::FromBase64String([string]$op.contentBase64))
      }
      [System.IO.File]::WriteAllText($target, $content, [System.Text.Encoding]::UTF8)
      return (Test-Path -LiteralPath $target)
    }
    'write_bytes' {
      $target = [string]$op.target
      if ([string]::IsNullOrWhiteSpace($target)) { return $true }
      Ensure-Parent $target
      $bytes = [byte[]]@()
      if ($op.contentBase64) {
        $bytes = [System.Convert]::FromBase64String([string]$op.contentBase64)
      }
      [System.IO.File]::WriteAllBytes($target, $bytes)
      return (Test-Path -LiteralPath $target)
    }
    'mkdir' {
      $target = [string]$op.target
      if ([string]::IsNullOrWhiteSpace($target)) { return $true }
      New-Item -ItemType Directory -Force -Path $target | Out-Null
      return (Test-Path -LiteralPath $target)
    }
    default {
      return $false
    }
  }
}

Wait-For-ParentExit

$lockHandle = $null
try {
  $lockHandle = [System.IO.File]::Open($LockFile, [System.IO.FileMode]::OpenOrCreate, [System.IO.FileAccess]::ReadWrite, [System.IO.FileShare]::None)
} catch {
  exit 0
}

try {
  for ($attempt = 0; $attempt -lt 30; $attempt++) {
    $index = Read-Index
    if ($null -eq $index) {
      exit 0
    }
    $operations = @()
    if ($index.operations) {
      $operations = @($index.operations)
    }
    if ($operations.Count -eq 0) {
      Remove-Item -LiteralPath $IndexFile -Force -ErrorAction SilentlyContinue
      exit 0
    }

    $remaining = @()
    foreach ($op in $operations) {
      if (-not (Invoke-Op $op)) {
        $remaining += $op
      }
    }

    $index.operations = $remaining
    Write-Index $index
    if ($remaining.Count -eq 0) {
      exit 0
    }
    Start-Sleep -Seconds 1
  }
  exit 1
} finally {
  if ($null -ne $lockHandle) {
    $lockHandle.Dispose()
  }
  Remove-Item -LiteralPath $LockFile -Force -ErrorAction SilentlyContinue
}
""";
    }

    public static final class PendingFileOp {
        public String id;
        public String type;
        public String path;
        public String source;
        public String target;
        public boolean replaceExisting;
        public String contentBase64;
        public long createdAt;
        public long updatedAt;
    }

    private static final class PendingFileOpsIndex {
        private int version = INDEX_VERSION;
        private long pid;
        private long updatedAt;
        private List<PendingFileOp> operations = new ArrayList<>();
    }

    public static final class PendingIndexResult {
        public final Path indexFile;
        public final List<PendingFileOp> operations;

        private PendingIndexResult(Path indexFile, List<PendingFileOp> operations) {
            this.indexFile = indexFile;
            this.operations = operations == null ? List.of() : List.copyOf(operations);
        }
    }

    public static final class ScheduleResult {
        public final boolean ok;
        public final String message;
        public final Path taskFile;
        public final int operationCount;

        private ScheduleResult(boolean ok, String message, Path taskFile, int operationCount) {
            this.ok = ok;
            this.message = message;
            this.taskFile = taskFile;
            this.operationCount = operationCount;
        }

        public static ScheduleResult success(Path taskFile, int operationCount) {
            return new ScheduleResult(true, "", taskFile, operationCount);
        }

        public static ScheduleResult error(String message) {
            return new ScheduleResult(false, message, null, 0);
        }
    }

    public static final class CancelResult {
        public final boolean ok;
        public final String message;
        public final Path indexFile;
        public final int removedCount;

        private CancelResult(boolean ok, String message, Path indexFile, int removedCount) {
            this.ok = ok;
            this.message = message;
            this.indexFile = indexFile;
            this.removedCount = removedCount;
        }

        public static CancelResult success(Path indexFile, int removedCount) {
            return new CancelResult(true, "", indexFile, removedCount);
        }

        public static CancelResult error(String message) {
            return new CancelResult(false, message, null, 0);
        }
    }
}
