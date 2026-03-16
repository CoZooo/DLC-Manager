package com.cozooo.dlc_manager.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class DlcPendingFileOpsHelper {
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    private static final String SCRIPT_NAME = "dlc-pending-file-ops.ps1";
    private static final String TASK_DIR = "pending_file_ops";

    private DlcPendingFileOpsHelper() {
    }

    public static boolean isSupported() {
        String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        return osName.contains("win");
    }

    public static boolean scheduleDelete(List<Path> rawPaths) {
        if (rawPaths == null || rawPaths.isEmpty()) {
            return false;
        }
        List<PendingFileOp> operations = new ArrayList<>();
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (Path path : rawPaths) {
            if (path == null) {
                continue;
            }
            normalized.add(path.toAbsolutePath().normalize().toString());
        }
        for (String path : normalized) {
            PendingFileOp op = new PendingFileOp();
            op.type = "delete";
            op.path = path;
            operations.add(op);
        }
        return schedule(operations);
    }

    public static boolean scheduleMove(Path source, Path target, boolean replaceExisting) {
        if (source == null || target == null) {
            return false;
        }
        PendingFileOp op = new PendingFileOp();
        op.type = "move";
        op.source = source.toAbsolutePath().normalize().toString();
        op.target = target.toAbsolutePath().normalize().toString();
        op.replaceExisting = replaceExisting;
        return schedule(List.of(op));
    }

    public static boolean scheduleCopy(Path source, Path target, boolean replaceExisting) {
        if (source == null || target == null) {
            return false;
        }
        PendingFileOp op = new PendingFileOp();
        op.type = "copy";
        op.source = source.toAbsolutePath().normalize().toString();
        op.target = target.toAbsolutePath().normalize().toString();
        op.replaceExisting = replaceExisting;
        return schedule(List.of(op));
    }

    public static boolean scheduleWriteString(Path target, String content) {
        if (target == null) {
            return false;
        }
        PendingFileOp op = new PendingFileOp();
        op.type = "write_text";
        op.target = target.toAbsolutePath().normalize().toString();
        op.contentBase64 = Base64.getEncoder().encodeToString(
            (content == null ? "" : content).getBytes(StandardCharsets.UTF_8));
        return schedule(List.of(op));
    }

    public static boolean schedule(List<PendingFileOp> operations) {
        if (!isSupported() || operations == null || operations.isEmpty()) {
            return false;
        }
        List<PendingFileOp> normalizedOps = new ArrayList<>();
        for (PendingFileOp operation : operations) {
            if (operation == null || operation.type == null || operation.type.isBlank()) {
                continue;
            }
            normalizedOps.add(operation);
        }
        if (normalizedOps.isEmpty()) {
            return false;
        }
        Path dlcRoot = DlcPathConfig.getDlcRoot();
        Path taskRoot = DlcTmpPaths.resolve(dlcRoot, TASK_DIR);
        if (taskRoot == null) {
            return false;
        }
        try {
            Files.createDirectories(taskRoot);
            Path script = taskRoot.resolve(SCRIPT_NAME);
            Files.writeString(script, buildPowerShellScript(), StandardCharsets.UTF_8);

            PendingFileOpsTask task = new PendingFileOpsTask();
            task.pid = ProcessHandle.current().pid();
            task.operations = normalizedOps;
            Path taskFile = taskRoot.resolve(UUID.randomUUID() + ".json");
            Files.writeString(taskFile, GSON.toJson(task), StandardCharsets.UTF_8);

            new ProcessBuilder(
                "powershell.exe",
                "-NoProfile",
                "-NonInteractive",
                "-ExecutionPolicy", "Bypass",
                "-File", script.toString(),
                "-TaskFile", taskFile.toString())
                .directory(DlcPaths.gameDir().toFile())
                .start();
            return true;
        } catch (IOException ignored) {
            return false;
        }
    }

    private static String buildPowerShellScript() {
        return """
param(
  [string]$TaskFile
)

$ErrorActionPreference = 'SilentlyContinue'
if (-not $TaskFile -or -not (Test-Path -LiteralPath $TaskFile)) {
  exit 0
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
    default {
      return $false
    }
  }
}

$task = Get-Content -LiteralPath $TaskFile -Raw | ConvertFrom-Json
$pidValue = [int64]$task.pid
$operations = @()
if ($task.operations) {
  $operations = @($task.operations)
}

for ($i = 0; $i -lt 600; $i++) {
  $proc = Get-Process -Id $pidValue -ErrorAction SilentlyContinue
  if ($null -eq $proc) {
    break
  }
  Start-Sleep -Seconds 1
}

for ($attempt = 0; $attempt -lt 30; $attempt++) {
  $remaining = @()
  foreach ($op in $operations) {
    if (-not (Invoke-Op $op)) {
      $remaining += $op
    }
  }
  if ($remaining.Count -eq 0) {
    Remove-Item -LiteralPath $TaskFile -Force -ErrorAction SilentlyContinue
    exit 0
  }
  $operations = $remaining
  Start-Sleep -Seconds 1
}

$task.operations = $operations
$task | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $TaskFile -Encoding UTF8
exit 1
""";
    }

    public static final class PendingFileOp {
        private String type;
        private String path;
        private String source;
        private String target;
        private boolean replaceExisting;
        private String contentBase64;
    }

    private static final class PendingFileOpsTask {
        private long pid;
        private List<PendingFileOp> operations;
    }
}
