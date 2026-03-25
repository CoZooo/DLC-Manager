param(
    [ValidateSet("bi", "cn", "en")]
    [string]$UiLanguage = "cn",
    [string]$RepoPath = ".",
    [int64]$MinSizeMiB = 100,
    [switch]$DryRun,
    [switch]$NoStage
)

$ErrorActionPreference = "Stop"

function BI([string]$Zh, [string]$En) {
    switch ($UiLanguage) {
        "cn" { return $Zh }
        "en" { return $En }
        default { return "$Zh / $En" }
    }
}

function Write-Step([string]$Message) {
    Write-Host ""
    Write-Host "==> $Message" -ForegroundColor Cyan
}

function Get-CommandOrThrow([string]$Name, [string]$Hint) {
    $cmd = Get-Command $Name -ErrorAction SilentlyContinue
    if ($null -eq $cmd) {
        throw $Hint
    }
    return $cmd
}

function Get-RepoRoot([string]$Path) {
    $resolvedPath = Resolve-Path -LiteralPath $Path
    Push-Location $resolvedPath
    try {
        $root = git rev-parse --show-toplevel 2>$null
        if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($root)) {
            throw (BI "指定路径不是 Git 仓库。" "The target path is not a Git repository.")
        }
        return $root.Trim()
    } finally {
        Pop-Location
    }
}

function Get-RelativePath([string]$BasePath, [string]$FullPath) {
    $baseUri = [System.Uri]((Resolve-Path -LiteralPath $BasePath).Path.TrimEnd('\', '/') + [System.IO.Path]::DirectorySeparatorChar)
    $fileUri = [System.Uri](Resolve-Path -LiteralPath $FullPath).Path
    $relativeUri = $baseUri.MakeRelativeUri($fileUri)
    return [System.Uri]::UnescapeDataString($relativeUri.ToString()).Replace('/', [System.IO.Path]::DirectorySeparatorChar)
}

function Test-IsGitTracked([string]$RootPath, [string]$RelativePath) {
    Push-Location $RootPath
    try {
        $output = git ls-files --cached -- "$RelativePath" 2>$null
        if ($LASTEXITCODE -ne 0) {
            return $false
        }
        if ($output -is [array]) {
            return ($output.Count -gt 0)
        }
        return (-not [string]::IsNullOrWhiteSpace([string]$output))
    } finally {
        Pop-Location
    }
}

function Test-IsGitIgnored([string]$RootPath, [string]$RelativePath) {
    Push-Location $RootPath
    try {
        git check-ignore --quiet -- "$RelativePath"
        return ($LASTEXITCODE -eq 0)
    } finally {
        Pop-Location
    }
}

function Get-CandidatePaths([string]$RootPath) {
    $result = New-Object System.Collections.Generic.List[string]
    $allFiles = Get-ChildItem -LiteralPath $RootPath -Recurse -File -Force -ErrorAction Stop
    foreach ($item in $allFiles) {
        $fullPath = $item.FullName
        $relativePath = Get-RelativePath $RootPath $fullPath
        $relativePathGit = $relativePath.Replace('\', '/')

        if ($relativePathGit -eq ".git" -or $relativePathGit.StartsWith(".git/")) {
            continue
        }

        $isTracked = Test-IsGitTracked $RootPath $relativePathGit
        if (-not $isTracked) {
            $isIgnored = Test-IsGitIgnored $RootPath $relativePathGit
            if ($isIgnored) {
                continue
            }
        }

        $result.Add($fullPath)
    }
    return $result
}

function Test-IsLfsTracked([string]$RootPath, [string]$RelativePath) {
    Push-Location $RootPath
    try {
        $output = git check-attr filter -- "$RelativePath" 2>$null
        if ($LASTEXITCODE -ne 0) {
            return $false
        }
        return ($output -match ':\s*filter:\s*lfs\s*$')
    } finally {
        Pop-Location
    }
}

function Add-LfsTracking([string]$RootPath, [string]$RelativePath, [bool]$ShouldStage) {
    Push-Location $RootPath
    try {
        git lfs track --filename -- "$RelativePath" | Out-Null
        if ($LASTEXITCODE -ne 0) {
            throw (BI "执行 git lfs track 失败。" "git lfs track failed.")
        }

        if ($ShouldStage) {
            git add -- "$RelativePath" ".gitattributes"
            if ($LASTEXITCODE -ne 0) {
                throw (BI "暂存文件或 .gitattributes 失败。" "Failed to stage file or .gitattributes.")
            }
        }
    } finally {
        Pop-Location
    }
}

Write-Step (BI "检查基础依赖" "Check prerequisites")
Get-CommandOrThrow "git" (BI "未检测到 Git，请先安装 Git。" "Git was not found. Please install Git first.") | Out-Null
Get-CommandOrThrow "git-lfs" (BI "未检测到 Git LFS，请先安装 Git LFS。" "Git LFS was not found. Please install Git LFS first.") | Out-Null

$repoRoot = Get-RepoRoot $RepoPath
$thresholdBytes = $MinSizeMiB * 1MB
$shouldStage = -not $NoStage.IsPresent

Write-Step (BI "启用仓库本地 Git LFS" "Enable local Git LFS")
Push-Location $repoRoot
try {
    git lfs install --local | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw (BI "启用 Git LFS 失败。" "Failed to enable Git LFS.")
    }
} finally {
    Pop-Location
}

Write-Step (BI "扫描大文件" "Scan large files")
$candidatePaths = Get-CandidatePaths $repoRoot
$largeFiles = New-Object System.Collections.Generic.List[object]

Write-Host ("{0}: {1}" -f (BI "扫描文件数" "Scanned files"), $candidatePaths.Count)

foreach ($fullPath in $candidatePaths) {
    $item = Get-Item -LiteralPath $fullPath
    if ($item.Length -le $thresholdBytes) {
        continue
    }

    $relativePath = Get-RelativePath $repoRoot $fullPath
    $relativePathGit = $relativePath.Replace('\', '/')
    $alreadyTracked = Test-IsLfsTracked $repoRoot $relativePathGit

    $largeFiles.Add([pscustomobject]@{
        FullPath       = $fullPath
        RelativePath   = $relativePathGit
        SizeBytes      = $item.Length
        SizeMiB        = [math]::Round($item.Length / 1MB, 2)
        AlreadyTracked = $alreadyTracked
    })
}

if ($largeFiles.Count -eq 0) {
    Write-Host (BI "没有发现大于阈值的文件。" "No files larger than the threshold were found.") -ForegroundColor Green
    exit 0
}

Write-Host (BI "扫描结果如下：" "Scan results:") -ForegroundColor Yellow
foreach ($file in $largeFiles | Sort-Object RelativePath) {
    $statusText = if ($file.AlreadyTracked) {
        BI "已由 LFS 跟踪" "already tracked by LFS"
    } else {
        BI "待加入 LFS" "pending LFS tracking"
    }
    Write-Host ("- {0} ({1} MiB) [{2}]" -f $file.RelativePath, $file.SizeMiB, $statusText)
}

$pendingFiles = @($largeFiles | Where-Object { -not $_.AlreadyTracked })
if ($pendingFiles.Count -eq 0) {
    Write-Host (BI "所有超大文件都已经由 Git LFS 跟踪。" "All oversized files are already tracked by Git LFS.") -ForegroundColor Green
    exit 0
}

if ($DryRun) {
    Write-Host (BI "当前为 DryRun，仅展示将要加入 LFS 的文件，不执行修改。" "DryRun enabled. No changes were made.") -ForegroundColor Yellow
    exit 0
}

Write-Step (BI "加入 Git LFS 跟踪" "Add Git LFS tracking")
foreach ($file in $pendingFiles | Sort-Object RelativePath) {
    Write-Host ("+ {0}" -f $file.RelativePath) -ForegroundColor Green
    Add-LfsTracking -RootPath $repoRoot -RelativePath $file.RelativePath -ShouldStage:$shouldStage
}

Write-Step (BI "处理完成" "Completed")
Write-Host ("{0}: {1}" -f (BI "仓库路径" "Repository"), $repoRoot)
Write-Host ("{0}: {1}" -f (BI "阈值" "Threshold"), ("{0} MiB" -f $MinSizeMiB))
Write-Host ("{0}: {1}" -f (BI "新增 LFS 跟踪文件数" "Newly tracked files"), $pendingFiles.Count)
if ($shouldStage) {
    Write-Host (BI "已自动暂存目标文件和 .gitattributes。" "Target files and .gitattributes have been staged automatically.")
} else {
    Write-Host (BI "未自动暂存文件，请按需手动 git add。" "Files were not staged automatically. Run git add manually if needed.")
}
