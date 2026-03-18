param(
    [string]$RepoUrl = "",
    [string]$RepoPath = ".",
    [string]$GitUserName = "",
    [string]$GitUserEmail = "",
    [string]$DefaultBranch = "main",
    [string]$LfsPatterns = "*.jar,*.zip",
    [string]$TemplateRepo = "CoZooo/Modpack-Template",
    [string]$NewRepo = "",
    [ValidateSet("private", "public")]
    [string]$RepoVisibility = "private",
    [ValidateSet("true", "false")]
    [string]$InstallGitHubDesktop = "true",
    [string]$RepoVar_AUTO_UPDATE_WORKFLOW = "",
    [string]$RepoVar_VERSION_DC_PATH = "",
    [string]$RepoVar_CLIENT_BRANCH = "",
    [string]$RepoVar_SERVER_BRANCH = "",
    [string]$RepoVar_AUTO_UPDATE_PLATFORM = "",
    [string]$RepoVar_AUTO_SYNC_TO_GITEE = "",
    [string]$RepoVar_GITEE_USERNAME = "",
    [string]$RepoVar_GITEE_REPO = "",
    [string]$RepoSecret_GITEE_TOKEN = "",
    [switch]$SkipRepoSetup,
    [switch]$SkipGhLogin
)

$ErrorActionPreference = "Stop"

function Write-Step([string]$Message) {
    Write-Host ""
    Write-Host "==> $Message" -ForegroundColor Cyan
}

function Refresh-Path {
    $machine = [Environment]::GetEnvironmentVariable("Path", "Machine")
    $user = [Environment]::GetEnvironmentVariable("Path", "User")
    $env:Path = "$machine;$user"
}

function Test-Command([string]$Name) {
    return $null -ne (Get-Command $Name -ErrorAction SilentlyContinue)
}

function Ensure-Winget {
    if (Test-Command "winget") { return }
    throw "未检测到 winget。请先安装 Microsoft App Installer 后重试。"
}

function Ask-YesNo([string]$Prompt, [bool]$Default = $true) {
    $suffix = if ($Default) { "[Y/n]" } else { "[y/N]" }
    $raw = Read-Host "$Prompt $suffix"
    if ([string]::IsNullOrWhiteSpace($raw)) {
        return $Default
    }
    $v = $raw.Trim().ToLower()
    return $v -in @("y", "yes", "1", "true")
}

function Ask-Input([string]$Prompt, [string]$Default = "") {
    $tip = if ([string]::IsNullOrWhiteSpace($Default)) { $Prompt } else { "$Prompt (默认: $Default)" }
    $raw = Read-Host $tip
    if ([string]::IsNullOrWhiteSpace($raw)) {
        return $Default
    }
    return $raw.Trim()
}

function Convert-SecureStringToPlainText([Security.SecureString]$SecureValue) {
    if ($null -eq $SecureValue) { return "" }
    $ptr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($SecureValue)
    try {
        return [Runtime.InteropServices.Marshal]::PtrToStringBSTR($ptr)
    } finally {
        if ($ptr -ne [IntPtr]::Zero) {
            [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ptr)
        }
    }
}

function Ask-Secret([string]$Prompt, [string]$Default = "") {
    $tip = if ([string]::IsNullOrWhiteSpace($Default)) { $Prompt } else { "$Prompt (留空保持当前值)" }
    $secure = Read-Host $tip -AsSecureString
    $plain = Convert-SecureStringToPlainText $secure
    if ([string]::IsNullOrWhiteSpace($plain)) {
        return $Default
    }
    return $plain
}

function Ask-Visibility([string]$Default = "private") {
    $defaultIndex = if ($Default -eq "public") { "2" } else { "1" }
    while ($true) {
        Write-Host "请选择仓库可见性: [1] private  [2] public"
        $raw = Ask-Input -Prompt "请输入序号" -Default $defaultIndex
        switch ($raw.Trim()) {
            "1" { return "private" }
            "2" { return "public" }
            default { Write-Host "请输入 1 或 2" -ForegroundColor Yellow }
        }
    }
}

function Install-WithWinget([string]$Id, [string]$DisplayName, [string]$CmdName) {
    if (Test-Command $CmdName) {
        Write-Host "$DisplayName 已安装，跳过。" -ForegroundColor Yellow
        return
    }
    Write-Host "安装 $DisplayName ..." -ForegroundColor Green
    winget install --id $Id -e --source winget --accept-source-agreements --accept-package-agreements --silent
    Refresh-Path
    if (-not (Test-Command $CmdName)) {
        throw "$DisplayName 安装后仍不可用，请手动检查。"
    }
}

function Test-GitHubDesktopInstalled {
    $candidates = @(
        (Join-Path $env:LOCALAPPDATA "GitHubDesktop\GitHubDesktop.exe"),
        (Join-Path ${env:ProgramFiles} "GitHub Desktop\GitHubDesktop.exe"),
        (Join-Path ${env:ProgramFiles(x86)} "GitHub Desktop\GitHubDesktop.exe")
    )
    foreach ($p in $candidates) {
        if ($p -and (Test-Path $p)) {
            return $true
        }
    }
    return $false
}

function Ensure-GitHubDesktop([string]$InstallFlag) {
    if ($InstallFlag -ne "true") {
        Write-Host "已跳过 GitHub Desktop 安装（InstallGitHubDesktop=false）。" -ForegroundColor Yellow
        return
    }
    if (Test-GitHubDesktopInstalled) {
        Write-Host "GitHub Desktop 已安装，跳过。" -ForegroundColor Yellow
        return
    }
    Write-Host "安装 GitHub Desktop ..." -ForegroundColor Green
    winget install --id GitHub.GitHubDesktop -e --source winget --accept-source-agreements --accept-package-agreements --silent
    if (-not (Test-GitHubDesktopInstalled)) {
        throw "GitHub Desktop 安装后未检测到可执行文件，请手动检查。"
    }
}

function Ensure-GitIdentity([string]$Name, [string]$Email) {
    $currentName = (git config --global --get user.name) 2>$null
    $currentEmail = (git config --global --get user.email) 2>$null

    if ([string]::IsNullOrWhiteSpace($Name)) { $Name = $currentName }
    if ([string]::IsNullOrWhiteSpace($Email)) { $Email = $currentEmail }

    if (-not [string]::IsNullOrWhiteSpace($Name)) {
        git config --global user.name $Name
    } else {
        Write-Host "未配置 Git user.name，后续 commit 可能失败。" -ForegroundColor Yellow
    }

    if (-not [string]::IsNullOrWhiteSpace($Email)) {
        git config --global user.email $Email
    } else {
        Write-Host "未配置 Git user.email，后续 commit 可能失败。" -ForegroundColor Yellow
    }
}

function Ensure-GhAuth {
    param([switch]$SkipLogin)
    if ($SkipLogin) {
        Write-Host "已跳过 gh 登录步骤。" -ForegroundColor Yellow
        return
    }
    $loggedIn = $true
    try {
        gh auth status -h github.com | Out-Null
    } catch {
        $loggedIn = $false
    }

    if (-not $loggedIn) {
        Write-Host "即将打开 gh 登录流程（浏览器授权）。" -ForegroundColor Green
        gh auth login -h github.com -p https -w
    } else {
        Write-Host "GitHub CLI 已登录，跳过登录。" -ForegroundColor Yellow
    }

    gh auth setup-git -h github.com
}

function Create-RepoFromTemplate {
    param(
        [string]$Template,
        [string]$TargetRepo,
        [string]$Visibility,
        [string]$TargetPath
    )

    if ([string]::IsNullOrWhiteSpace($Template) -or [string]::IsNullOrWhiteSpace($TargetRepo)) {
        return $TargetPath
    }

    $resolvedTargetPath = $TargetPath
    if ([string]::IsNullOrWhiteSpace($resolvedTargetPath)) {
        $resolvedTargetPath = "."
    }

    $targetExists = Test-Path $resolvedTargetPath
    $targetHasFiles = $false
    $targetHasGit = $false
    $targetHasOrigin = $false
    if ($targetExists) {
        $targetHasFiles = $null -ne (Get-ChildItem -Path $resolvedTargetPath -Force -ErrorAction SilentlyContinue | Select-Object -First 1)
        $targetHasGit = Test-Path (Join-Path $resolvedTargetPath ".git")
        if ($targetHasGit) {
            try {
                Push-Location $resolvedTargetPath
                $originUrl = (git remote get-url origin) 2>$null
                if (-not [string]::IsNullOrWhiteSpace($originUrl)) {
                    $targetHasOrigin = $true
                }
            } finally {
                Pop-Location
            }
        }
    }

    $shouldClone = (-not $targetExists) -or (-not $targetHasFiles)
    $shouldCreate = $true

    Write-Host "使用模板创建仓库: $TargetRepo (template: $Template)" -ForegroundColor Green
    if ($targetHasGit -and $targetHasOrigin) {
        Write-Host "本地路径已是 Git 仓库且存在 origin，跳过 repo create/clone。" -ForegroundColor Yellow
        $shouldCreate = $false
        $shouldClone = $false
    } elseif (-not $shouldClone) {
        Write-Host "本地路径非空，跳过 clone，仅创建/复用远程仓库。" -ForegroundColor Yellow
    } elseif ($targetHasGit) {
        Write-Host "本地路径已是 Git 仓库，跳过 clone，仅创建/复用远程仓库。" -ForegroundColor Yellow
        $shouldClone = $false
    }

    if ($shouldCreate) {
        $args = @(
            "repo", "create", $TargetRepo,
            "--template", $Template
        )
        switch ($Visibility) {
            "public" { $args += "--public" }
            default { $args += "--private" }
        }
        & gh @args
        $createExit = $LASTEXITCODE

        if ($createExit -ne 0) {
            & gh repo view $TargetRepo --json nameWithOwner | Out-Null
            $exists = ($LASTEXITCODE -eq 0)
            if (-not $exists) {
                throw "模板建仓失败（gh exit code: $createExit）。"
            }

            Write-Host "目标仓库已存在，跳过创建并继续。" -ForegroundColor Yellow
        }
    }

    if ($shouldClone) {
        Write-Host "尝试克隆仓库到本地: $resolvedTargetPath" -ForegroundColor Yellow
        if (-not (Test-Path $resolvedTargetPath)) {
            New-Item -ItemType Directory -Path $resolvedTargetPath -Force | Out-Null
        }
        & gh repo clone $TargetRepo $resolvedTargetPath
        if ($LASTEXITCODE -eq 0 -and (Test-Path $resolvedTargetPath)) {
            return (Resolve-Path $resolvedTargetPath).Path
        }
        Write-Host "自动 clone 失败，将继续使用当前路径配置。" -ForegroundColor Yellow
    }

    if (Test-Path $resolvedTargetPath) {
        return (Resolve-Path $resolvedTargetPath).Path
    }
    return $resolvedTargetPath
}

function Parse-RepoSlugFromUrl([string]$Url) {
    if ([string]::IsNullOrWhiteSpace($Url)) { return "" }
    $u = $Url.Trim()
    $m = [regex]::Match($u, "github\.com[:/](?<slug>[^/]+/[^/.]+)(?:\.git)?/?$")
    if ($m.Success) { return $m.Groups["slug"].Value }
    return ""
}

function Normalize-RepoSlug([string]$RepoInput) {
    if ([string]::IsNullOrWhiteSpace($RepoInput)) { return "" }
    $raw = $RepoInput.Trim().Trim("/")
    if ($raw.Contains("/")) { return $raw }
    $owner = (gh api user --jq .login).Trim()
    if ([string]::IsNullOrWhiteSpace($owner)) { return "" }
    return "$owner/$raw"
}

function Resolve-RepoSlug([string]$NewRepoValue, [string]$RepoUrlValue, [string]$RepoPathValue) {
    $slug = Normalize-RepoSlug $NewRepoValue
    if (-not [string]::IsNullOrWhiteSpace($slug)) { return $slug }

    $slug = Parse-RepoSlugFromUrl $RepoUrlValue
    if (-not [string]::IsNullOrWhiteSpace($slug)) { return $slug }

    try {
        $fullPath = Resolve-Path -Path $RepoPathValue -ErrorAction SilentlyContinue
        if ($fullPath) {
            Push-Location $fullPath.Path
            try {
                $originUrl = (git remote get-url origin) 2>$null
                $slug = Parse-RepoSlugFromUrl $originUrl
                if (-not [string]::IsNullOrWhiteSpace($slug)) { return $slug }
            } finally {
                Pop-Location
            }
        }
    } catch {
    }
    return ""
}

function Set-RepoVariableIfPresent([string]$RepoSlug, [string]$Name, [string]$Value) {
    if ([string]::IsNullOrWhiteSpace($Value)) {
        Write-Host "跳过 Variable $Name（空值）" -ForegroundColor Yellow
        return
    }
    gh variable set $Name --body $Value --repo $RepoSlug
    Write-Host "已写入 Variable: $Name" -ForegroundColor Green
}

function Set-RepoSecretIfPresent([string]$RepoSlug, [string]$Name, [string]$Value) {
    if ([string]::IsNullOrWhiteSpace($Value)) {
        Write-Host "跳过 Secret $Name（空值）" -ForegroundColor Yellow
        return
    }
    gh secret set $Name --body $Value --repo $RepoSlug
    Write-Host "已写入 Secret: $Name" -ForegroundColor Green
}

function Setup-Repository {
    param(
        [string]$TargetPath,
        [string]$RemoteUrl,
        [string]$Branch,
        [string]$Patterns
    )

    $fullPath = Resolve-Path -Path $TargetPath -ErrorAction SilentlyContinue
    if (-not $fullPath) {
        New-Item -ItemType Directory -Path $TargetPath -Force | Out-Null
        $fullPath = Resolve-Path -Path $TargetPath
    }
    $repoDir = $fullPath.Path
    Set-Location $repoDir

    if (-not (Test-Path ".git")) {
        git init -b $Branch | Out-Null
    }

    if (-not [string]::IsNullOrWhiteSpace($RemoteUrl)) {
        $origin = (git remote) 2>$null
        if ($origin -contains "origin") {
            git remote set-url origin $RemoteUrl
        } else {
            git remote add origin $RemoteUrl
        }
    }

    git lfs install --local | Out-Null

    $splitPatterns = $Patterns.Split(",") | ForEach-Object { $_.Trim() } | Where-Object { $_ -ne "" }
    foreach ($pattern in $splitPatterns) {
        git lfs track $pattern | Out-Null
    }

    if (-not (git rev-parse --verify HEAD 2>$null)) {
        if (-not (Test-Path "README.md")) {
            "# Repo Initialized" | Out-File -FilePath "README.md" -Encoding utf8
        }
        git add .gitattributes README.md
        git commit -m "chore: initialize repository with git lfs" | Out-Null
    }

    if (-not [string]::IsNullOrWhiteSpace($RemoteUrl)) {
        try {
            git push -u origin $Branch
        } catch {
            Write-Host "首次推送失败，请检查仓库权限或分支保护策略。" -ForegroundColor Yellow
        }
    }
}

function Run-InteractiveWizard {
    param(
        [ref]$RepoUrlRef,
        [ref]$RepoPathRef,
        [ref]$GitUserNameRef,
        [ref]$GitUserEmailRef,
        [ref]$DefaultBranchRef,
        [ref]$LfsPatternsRef,
        [ref]$TemplateRepoRef,
        [ref]$NewRepoRef,
        [ref]$RepoVisibilityRef,
        [ref]$RepoVarAutoUpdateWorkflowRef,
        [ref]$RepoVarVersionDcPathRef,
        [ref]$RepoVarClientBranchRef,
        [ref]$RepoVarServerBranchRef,
        [ref]$RepoVarAutoUpdatePlatformRef,
        [ref]$RepoVarAutoSyncToGiteeRef,
        [ref]$RepoVarGiteeUsernameRef,
        [ref]$RepoVarGiteeRepoRef,
        [ref]$RepoSecretGiteeTokenRef,
        [ref]$SkipRepoSetupRef,
        [ref]$SkipGhLoginRef
    )

    Write-Step "向导配置"
    $SkipGhLoginRef.Value = $false
    $SkipRepoSetupRef.Value = $false

    $RepoPathRef.Value = Ask-Input -Prompt "本地路径 (留空则当前目录)" -Default $RepoPathRef.Value

    $TemplateRepoRef.Value = "CoZooo/Modpack-Template"
    $NewRepoRef.Value = Ask-Input -Prompt "请输入新仓库 (owner/repo 或 repo)" -Default $NewRepoRef.Value
    $RepoVisibilityRef.Value = Ask-Visibility -Default $RepoVisibilityRef.Value
    $LfsPatternsRef.Value = "*.jar,*.zip"

    $GitUserNameRef.Value = Ask-Input -Prompt "Git 用户名" -Default $GitUserNameRef.Value
    $GitUserEmailRef.Value = Ask-Input -Prompt "Git 邮箱" -Default $GitUserEmailRef.Value

    Write-Host ""
    Write-Host "仓库变量（回车跳过）" -ForegroundColor Cyan
    $RepoVarAutoUpdateWorkflowRef.Value = Ask-Input -Prompt "AUTO_UPDATE_WORKFLOW（是否启用推送自动生成更新包，true/false）" -Default $RepoVarAutoUpdateWorkflowRef.Value
    $RepoVarVersionDcPathRef.Value = Ask-Input -Prompt "VERSION_DC_PATH（version.dc 路径，默认 DLC/info/version.dc）" -Default $RepoVarVersionDcPathRef.Value
    $RepoVarClientBranchRef.Value = Ask-Input -Prompt "CLIENT_BRANCH（自动模式判定为客户端包的分支名，逗号分隔）" -Default $RepoVarClientBranchRef.Value
    $RepoVarServerBranchRef.Value = Ask-Input -Prompt "SERVER_BRANCH（自动模式判定为服务端包的分支名，逗号分隔）" -Default $RepoVarServerBranchRef.Value
    $RepoVarAutoUpdatePlatformRef.Value = Ask-Input -Prompt "AUTO_UPDATE_PLATFORM（自动模式平台：universal/client/server）" -Default $RepoVarAutoUpdatePlatformRef.Value
    $RepoVarAutoSyncToGiteeRef.Value = Ask-Input -Prompt "AUTO_SYNC_TO_GITEE（自动模式是否同步到 Gitee，true/false）" -Default $RepoVarAutoSyncToGiteeRef.Value
    $RepoVarGiteeUsernameRef.Value = Ask-Input -Prompt "GITEE_USERNAME（Gitee 用户名）" -Default $RepoVarGiteeUsernameRef.Value
    $RepoVarGiteeRepoRef.Value = Ask-Input -Prompt "GITEE_REPO（Gitee 仓库名）" -Default $RepoVarGiteeRepoRef.Value

    Write-Host ""
    Write-Host "仓库 Secret（回车跳过或不修改）" -ForegroundColor Cyan
    $RepoSecretGiteeTokenRef.Value = Ask-Secret -Prompt "GITEE_TOKEN（Gitee 访问令牌）" -Default $RepoSecretGiteeTokenRef.Value
}

Write-Step "检查基础依赖"
Ensure-Winget

if ([string]::IsNullOrWhiteSpace($RepoUrl) -and
    $RepoPath -eq "." -and
    [string]::IsNullOrWhiteSpace($GitUserName) -and
    [string]::IsNullOrWhiteSpace($GitUserEmail) -and
    $DefaultBranch -eq "main" -and
    $LfsPatterns -eq "*.jar,*.zip" -and
    [string]::IsNullOrWhiteSpace($TemplateRepo) -and
    [string]::IsNullOrWhiteSpace($NewRepo) -and
    $RepoVisibility -eq "private" -and
    (-not $SkipRepoSetup) -and
    (-not $SkipGhLogin)) {
    Run-InteractiveWizard `
        -RepoUrlRef ([ref]$RepoUrl) `
        -RepoPathRef ([ref]$RepoPath) `
        -GitUserNameRef ([ref]$GitUserName) `
        -GitUserEmailRef ([ref]$GitUserEmail) `
        -DefaultBranchRef ([ref]$DefaultBranch) `
        -LfsPatternsRef ([ref]$LfsPatterns) `
        -TemplateRepoRef ([ref]$TemplateRepo) `
        -NewRepoRef ([ref]$NewRepo) `
        -RepoVisibilityRef ([ref]$RepoVisibility) `
        -RepoVarAutoUpdateWorkflowRef ([ref]$RepoVar_AUTO_UPDATE_WORKFLOW) `
        -RepoVarVersionDcPathRef ([ref]$RepoVar_VERSION_DC_PATH) `
        -RepoVarClientBranchRef ([ref]$RepoVar_CLIENT_BRANCH) `
        -RepoVarServerBranchRef ([ref]$RepoVar_SERVER_BRANCH) `
        -RepoVarAutoUpdatePlatformRef ([ref]$RepoVar_AUTO_UPDATE_PLATFORM) `
        -RepoVarAutoSyncToGiteeRef ([ref]$RepoVar_AUTO_SYNC_TO_GITEE) `
        -RepoVarGiteeUsernameRef ([ref]$RepoVar_GITEE_USERNAME) `
        -RepoVarGiteeRepoRef ([ref]$RepoVar_GITEE_REPO) `
        -RepoSecretGiteeTokenRef ([ref]$RepoSecret_GITEE_TOKEN) `
        -SkipRepoSetupRef ([ref]$SkipRepoSetup) `
        -SkipGhLoginRef ([ref]$SkipGhLogin)
}

Write-Step "安装 Git / Git LFS / GitHub CLI"
Install-WithWinget -Id "Git.Git" -DisplayName "Git" -CmdName "git"
Install-WithWinget -Id "GitHub.GitLFS" -DisplayName "Git LFS" -CmdName "git-lfs"
Install-WithWinget -Id "GitHub.cli" -DisplayName "GitHub CLI" -CmdName "gh"
Ensure-GitHubDesktop -InstallFlag $InstallGitHubDesktop

Write-Step "配置 Git 全局身份"
Ensure-GitIdentity -Name $GitUserName -Email $GitUserEmail

Write-Step "启用 Git LFS"
git lfs install | Out-Null

Write-Step "配置 GitHub 认证"
Ensure-GhAuth -SkipLogin:$SkipGhLogin

$hasTemplate = -not [string]::IsNullOrWhiteSpace($TemplateRepo)
$hasNewRepo = -not [string]::IsNullOrWhiteSpace($NewRepo)
if ($hasTemplate -xor $hasNewRepo) {
    throw "TemplateRepo 和 NewRepo 需要同时填写，或同时留空。"
}

$resolvedRepoPath = $RepoPath
if ($hasTemplate -and $hasNewRepo) {
    Write-Step "基于模板创建 GitHub 仓库"
    $resolvedRepoPath = Create-RepoFromTemplate -Template $TemplateRepo -TargetRepo $NewRepo -Visibility $RepoVisibility -TargetPath $RepoPath
}

if (-not $SkipRepoSetup) {
    Write-Step "配置仓库"
    Setup-Repository -TargetPath $resolvedRepoPath -RemoteUrl $RepoUrl -Branch $DefaultBranch -Patterns $LfsPatterns
}

Write-Step "写入 Actions Repository Variables（空值自动跳过）"
$repoSlug = Resolve-RepoSlug -NewRepoValue $NewRepo -RepoUrlValue $RepoUrl -RepoPathValue $resolvedRepoPath
if ([string]::IsNullOrWhiteSpace($repoSlug)) {
    Write-Host "未能识别目标仓库，跳过 Variables 写入。" -ForegroundColor Yellow
} else {
    Write-Host "目标仓库: $repoSlug"
    Set-RepoVariableIfPresent -RepoSlug $repoSlug -Name "AUTO_UPDATE_WORKFLOW" -Value $RepoVar_AUTO_UPDATE_WORKFLOW
    Set-RepoVariableIfPresent -RepoSlug $repoSlug -Name "VERSION_DC_PATH" -Value $RepoVar_VERSION_DC_PATH
    Set-RepoVariableIfPresent -RepoSlug $repoSlug -Name "CLIENT_BRANCH" -Value $RepoVar_CLIENT_BRANCH
    Set-RepoVariableIfPresent -RepoSlug $repoSlug -Name "SERVER_BRANCH" -Value $RepoVar_SERVER_BRANCH
    Set-RepoVariableIfPresent -RepoSlug $repoSlug -Name "AUTO_UPDATE_PLATFORM" -Value $RepoVar_AUTO_UPDATE_PLATFORM
    Set-RepoVariableIfPresent -RepoSlug $repoSlug -Name "AUTO_SYNC_TO_GITEE" -Value $RepoVar_AUTO_SYNC_TO_GITEE
    Set-RepoVariableIfPresent -RepoSlug $repoSlug -Name "GITEE_USERNAME" -Value $RepoVar_GITEE_USERNAME
    Set-RepoVariableIfPresent -RepoSlug $repoSlug -Name "GITEE_REPO" -Value $RepoVar_GITEE_REPO
    Set-RepoSecretIfPresent -RepoSlug $repoSlug -Name "GITEE_TOKEN" -Value $RepoSecret_GITEE_TOKEN
}

Write-Step "完成"
Write-Host "Git:  $(git --version)"
Write-Host "LFS:  $(git lfs version)"
Write-Host "GH:   $(gh --version | Select-Object -First 1)"
Write-Host ""
Write-Host "你现在可以直接使用 GitHub 仓库进行提交和推送。" -ForegroundColor Green
