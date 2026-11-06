param(
    [ValidateSet("bi", "cn", "en")]
    [string]$UiLanguage = "bi",
    [string]$RepoUrl = "",
    [string]$RepoPath = ".",
    [string]$GitUserName = "",
    [string]$GitUserEmail = "",
    [string]$DefaultBranch = "main",
    [string]$NewRepo = "",
    [ValidateSet("private", "public")]
    [string]$RepoVisibility = "private",
    [ValidateSet("true", "false")]
    [string]$InstallGitHubDesktop = "true",
    [string]$RepoVar_AUTO_UPDATE_WORKFLOW = "true",
    [string]$RepoVar_VERSION_DC_PATH = "config/dlc_manager/version.dc",
    [string]$RepoVar_CLIENT_BRANCH = "",
    [string]$RepoVar_SERVER_BRANCH = "",
    [string]$RepoVar_AUTO_UPDATE_PLATFORM = "universal",
    [string]$RepoVar_AUTO_SYNC_TO_GITEE = "false",
    [string]$RepoVar_GITEE_USERNAME = "",
    [string]$RepoVar_GITEE_REPO = "",
    [string]$RepoSecret_GITEE_TOKEN = "",
    [switch]$ForceWizard,
    [switch]$SkipRepoSetup,
    [switch]$SkipGhLogin
)

$ErrorActionPreference = "Stop"
$ScriptStatePath = Join-Path (Split-Path -Parent $MyInvocation.MyCommand.Path) "setup_github_repo.state.json"
$TemplateRepo = "CoZooo/Modpack-Template"
$DefaultVersionDcPath = "config/dlc_manager/version.dc"

function Write-Step([string]$Message) {
    Write-Host ""
    Write-Host "==> $Message" -ForegroundColor Cyan
}

function BI([string]$Zh, [string]$En) {
    switch ($UiLanguage) {
        "cn" { return $Zh }
        "en" { return $En }
        default { return "$Zh / $En" }
    }
}

function Ask-Language([string]$Default = "cn") {
    $defaultIndex = if ($Default -eq "en") { "2" } else { "1" }
    while ($true) {
        Write-Host "Select language / 选择语言: [1] 中文  [2] English" -ForegroundColor Cyan
        $raw = Ask-Input -Prompt "Enter number / 请输入序号" -Default $defaultIndex
        switch ($raw.Trim()) {
            "1" { return "cn" }
            "2" { return "en" }
            default { Write-Host "Please enter 1 or 2 / 请输入 1 或 2" -ForegroundColor Yellow }
        }
    }
}

function Convert-ObjectToHashtable($Value) {
    if ($null -eq $Value) {
        return $null
    }
    if ($Value -is [System.Collections.IDictionary]) {
        $table = @{}
        foreach ($key in $Value.Keys) {
            $table[$key] = Convert-ObjectToHashtable $Value[$key]
        }
        return $table
    }
    if ($Value -is [System.Collections.IEnumerable] -and -not ($Value -is [string])) {
        $items = @()
        foreach ($item in $Value) {
            $items += ,(Convert-ObjectToHashtable $item)
        }
        return $items
    }
    if ($Value -is [psobject]) {
        $props = $Value.PSObject.Properties
        if ($props.Count -gt 0) {
            $table = @{}
            foreach ($prop in $props) {
                $table[$prop.Name] = Convert-ObjectToHashtable $prop.Value
            }
            return $table
        }
    }
    return $Value
}

function Read-ScriptState {
    if (-not (Test-Path $ScriptStatePath)) {
        return @{}
    }
    try {
        $raw = Get-Content -Path $ScriptStatePath -Raw -Encoding UTF8
        if ([string]::IsNullOrWhiteSpace($raw)) {
            return @{}
        }
        $state = Convert-ObjectToHashtable (ConvertFrom-Json -InputObject $raw)
        if ($null -eq $state) {
            return @{}
        }
        return $state
    } catch {
        Write-Host (BI "读取上次配置失败，将忽略已保存参数。" "Failed to read saved config. Saved parameters will be ignored.") -ForegroundColor Yellow
        return @{}
    }
}

function Write-ScriptState([hashtable]$State) {
    $json = $State | ConvertTo-Json -Depth 4
    Set-Content -Path $ScriptStatePath -Value $json -Encoding utf8
}

function Use-SavedValue([string]$Current, [string]$DefaultValue, [object]$SavedValue) {
    if ($null -eq $SavedValue) {
        return $Current
    }
    $savedText = [string]$SavedValue
    if ([string]::IsNullOrWhiteSpace($savedText)) {
        return $Current
    }
    if ($Current -eq $DefaultValue) {
        return $savedText
    }
    return $Current
}

function Test-LooksLikeUrl([string]$Value) {
    if ([string]::IsNullOrWhiteSpace($Value)) {
        return $false
    }
    return $Value.Trim() -match '^[a-zA-Z][a-zA-Z0-9+\.-]*://'
}

function Refresh-Path {
    $machine = [Environment]::GetEnvironmentVariable("Path", "Machine")
    $user = [Environment]::GetEnvironmentVariable("Path", "User")
    $env:Path = "$machine;$user"
}

function Test-Command([string]$Name) {
    return $null -ne (Get-Command $Name -ErrorAction SilentlyContinue)
}

function Get-OriginUrlSilently {
    $output = cmd /c "git remote get-url origin 2>nul"
    if ($LASTEXITCODE -ne 0) {
        return ""
    }
    if ($output -is [array]) {
        return ($output | Select-Object -First 1).Trim()
    }
    return ([string]$output).Trim()
}

function Ensure-Winget {
    if (Test-Command "winget") { return }
    throw (BI "未检测到 winget。请先安装 Microsoft App Installer 后重试。" "winget was not found. Please install Microsoft App Installer and try again.")
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
    if ([string]::IsNullOrWhiteSpace($Default)) {
        $tip = $Prompt
    } else {
        $tip = "{0} ({1}: {2})" -f $Prompt, (BI "默认" "default"), $Default
    }
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
    if ([string]::IsNullOrWhiteSpace($Default)) {
        $tip = $Prompt
    } else {
        $tip = "{0} ({1})" -f $Prompt, (BI "留空保持当前值" "leave empty to keep current value")
    }
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
        Write-Host (BI "请选择仓库可见性: [1] private  [2] public" "Select repository visibility: [1] private  [2] public")
        $raw = Ask-Input -Prompt (BI "请输入序号" "Enter number") -Default $defaultIndex
        switch ($raw.Trim()) {
            "1" { return "private" }
            "2" { return "public" }
            default { Write-Host (BI "请输入 1 或 2" "Please enter 1 or 2") -ForegroundColor Yellow }
        }
    }
}

function Install-WithWinget([string]$Id, [string]$DisplayName, [string]$CmdName) {
    if (Test-Command $CmdName) {
        Write-Host ((BI "$DisplayName 已安装，跳过。" "$DisplayName is already installed, skipping.")) -ForegroundColor Yellow
        return
    }
    Write-Host ((BI "安装 $DisplayName ..." "Installing $DisplayName ...")) -ForegroundColor Green
    winget install --id $Id -e --source winget --accept-source-agreements --accept-package-agreements --silent
    Refresh-Path
    if (-not (Test-Command $CmdName)) {
        throw (BI "$DisplayName 安装后仍不可用，请手动检查。" "$DisplayName is still unavailable after installation. Please check manually.")
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
        Write-Host (BI "已跳过 GitHub Desktop 安装（InstallGitHubDesktop=false）。" "Skipped GitHub Desktop installation (InstallGitHubDesktop=false).") -ForegroundColor Yellow
        return
    }
    if (Test-GitHubDesktopInstalled) {
        Write-Host (BI "GitHub Desktop 已安装，跳过。" "GitHub Desktop is already installed, skipping.") -ForegroundColor Yellow
        return
    }
    Write-Host (BI "安装 GitHub Desktop ..." "Installing GitHub Desktop ...") -ForegroundColor Green
    winget install --id GitHub.GitHubDesktop -e --source winget --accept-source-agreements --accept-package-agreements --silent
    if (-not (Test-GitHubDesktopInstalled)) {
        throw (BI "GitHub Desktop 安装后未检测到可执行文件，请手动检查。" "GitHub Desktop executable was not found after installation. Please check manually.")
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
        Write-Host (BI "未配置 Git user.name，后续 commit 可能失败。" "Git user.name is not configured. Future commits may fail.") -ForegroundColor Yellow
    }

    if (-not [string]::IsNullOrWhiteSpace($Email)) {
        git config --global user.email $Email
    } else {
        Write-Host (BI "未配置 Git user.email，后续 commit 可能失败。" "Git user.email is not configured. Future commits may fail.") -ForegroundColor Yellow
    }
}

function Ensure-GhAuth {
    param([switch]$SkipLogin)
    if ($SkipLogin) {
        Write-Host (BI "已跳过 gh 登录步骤。" "Skipped gh login step.") -ForegroundColor Yellow
        return
    }
    cmd /c "gh auth status -h github.com 1>nul 2>nul"
    $loggedIn = ($LASTEXITCODE -eq 0)

    if (-not $loggedIn) {
        Write-Host (BI "即将打开 gh 登录流程（浏览器授权）。" "Starting gh login flow (browser authorization).") -ForegroundColor Green
        gh auth login -h github.com -p https -w
        cmd /c "gh auth status -h github.com 1>nul 2>nul"
        if ($LASTEXITCODE -ne 0) {
            throw (BI "GitHub CLI 登录失败，请先执行 gh auth login。" "GitHub CLI login failed. Please run gh auth login first.")
        }
    } else {
        Write-Host (BI "GitHub CLI 已登录，跳过登录。" "GitHub CLI is already logged in, skipping.") -ForegroundColor Yellow
    }

    gh auth setup-git -h github.com
}

function Sync-TemplateRepoToLocal {
    param(
        [string]$TargetRepo,
        [string]$TargetPath,
        [string]$Branch
    )

    if ([string]::IsNullOrWhiteSpace($TargetRepo) -or [string]::IsNullOrWhiteSpace($TargetPath)) {
        return
    }

    $repoUrl = ""
    $defaultBranch = $Branch
    try {
        $repoInfo = gh repo view $TargetRepo --json url,defaultBranchRef | ConvertFrom-Json
        if ($null -ne $repoInfo) {
            $repoUrl = [string]$repoInfo.url
            if ($repoInfo.defaultBranchRef -and -not [string]::IsNullOrWhiteSpace([string]$repoInfo.defaultBranchRef.name)) {
                $defaultBranch = [string]$repoInfo.defaultBranchRef.name
            }
        }
    } catch {
        Write-Host (BI "获取模板仓库信息失败，跳过本地同步。" "Failed to read template repository info. Skipping local sync.") -ForegroundColor Yellow
        return
    }

    if ([string]::IsNullOrWhiteSpace($repoUrl) -or -not (Test-Path $TargetPath)) {
        return
    }

    try {
        Push-Location $TargetPath
        if (-not (Test-Path ".git")) {
            git init -b $defaultBranch | Out-Null
        }
        $originUrl = Get-OriginUrlSilently
        if ([string]::IsNullOrWhiteSpace($originUrl)) {
            git remote add origin $repoUrl | Out-Null
        } elseif ($originUrl -ne $repoUrl) {
            git remote set-url origin $repoUrl | Out-Null
        }

        cmd /c "git fetch origin 1>nul 2>nul"
        if ($LASTEXITCODE -ne 0) {
            Write-Host (BI "git fetch 失败，跳过模板内容同步。" "git fetch failed. Skipping template content sync.") -ForegroundColor Yellow
            return
        }

        cmd /c "git rev-parse --verify HEAD 1>nul 2>nul"
        $hasHeadCommit = ($LASTEXITCODE -eq 0)
        if (-not $hasHeadCommit) {
            git checkout -B $defaultBranch --track ("origin/" + $defaultBranch) | Out-Null
        } else {
            git pull origin $defaultBranch | Out-Null
        }
    } catch {
        Write-Host (BI "同步模板仓库内容到本地失败，请手动执行 git fetch/pull。" "Failed to sync template repository content to local. Please run git fetch/pull manually.") -ForegroundColor Yellow
    } finally {
        Pop-Location
    }
}

function Create-RepoFromTemplate {
    param(
        [string]$Template,
        [string]$TargetRepo,
        [string]$Visibility,
        [string]$TargetPath,
        [string]$Branch
    )

    if ([string]::IsNullOrWhiteSpace($Template) -or [string]::IsNullOrWhiteSpace($TargetRepo)) {
        return $TargetPath
    }

    $resolvedTargetPath = $TargetPath
    $expectedRepoSlug = Normalize-RepoSlug $TargetRepo
    if ([string]::IsNullOrWhiteSpace($resolvedTargetPath)) {
        $resolvedTargetPath = "."
    }

    $targetExists = Test-Path $resolvedTargetPath
    $targetHasFiles = $false
    $targetHasGit = $false
    $targetHasOrigin = $false
    $targetOriginMatches = $false
    if ($targetExists) {
        $targetHasFiles = $null -ne (Get-ChildItem -Path $resolvedTargetPath -Force -ErrorAction SilentlyContinue | Select-Object -First 1)
        $targetHasGit = Test-Path (Join-Path $resolvedTargetPath ".git")
        if ($targetHasGit) {
            try {
                Push-Location $resolvedTargetPath
                $originUrl = Get-OriginUrlSilently
                if (-not [string]::IsNullOrWhiteSpace($originUrl)) {
                    $targetHasOrigin = $true
                    $originSlug = Parse-RepoSlugFromUrl $originUrl
                    if (-not [string]::IsNullOrWhiteSpace($expectedRepoSlug) -and $originSlug -eq $expectedRepoSlug) {
                        $targetOriginMatches = $true
                    }
                }
            } finally {
                Pop-Location
            }
        }
    }

    $shouldClone = (-not $targetExists) -or (-not $targetHasFiles)
    $shouldCreate = $true

    Write-Host (BI "使用模板创建仓库: $TargetRepo (template: $Template)" "Creating repository from template: $TargetRepo (template: $Template)") -ForegroundColor Green
    if ($targetHasGit -and $targetOriginMatches) {
        Write-Host (BI "本地路径已是 Git 仓库且存在 origin，跳过 repo create/clone。" "Local path is already a Git repository with origin. Skipping repo create/clone.") -ForegroundColor Yellow
        $shouldCreate = $false
        $shouldClone = $false
    } elseif ($targetHasGit -and $targetHasOrigin) {
        Write-Host (BI "检测到本地 origin 指向了其他仓库，将自动改为目标仓库。" "Detected that local origin points to another repository. It will be updated to the target repository.") -ForegroundColor Yellow
        $shouldClone = $false
    } elseif (-not $shouldClone) {
        Write-Host (BI "本地路径非空，跳过 clone，仅创建/复用远程仓库。" "Local path is not empty. Skipping clone and only creating/reusing the remote repository.") -ForegroundColor Yellow
    } elseif ($targetHasGit) {
        Write-Host (BI "本地路径已是 Git 仓库，跳过 clone，仅创建/复用远程仓库。" "Local path is already a Git repository. Skipping clone and only creating/reusing the remote repository.") -ForegroundColor Yellow
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
        & gh @args | Out-Null
        $createExit = $LASTEXITCODE

        if ($createExit -ne 0) {
            & gh repo view $TargetRepo --json nameWithOwner | Out-Null
            $exists = ($LASTEXITCODE -eq 0)
            if (-not $exists) {
                throw (BI "模板建仓失败（gh exit code: $createExit）。" "Failed to create repository from template (gh exit code: $createExit).")
            }

            Write-Host (BI "目标仓库已存在，跳过创建并继续。" "Target repository already exists. Skipping creation and continuing.") -ForegroundColor Yellow
        }
    }

    if ($shouldClone) {
        Write-Host (BI "尝试克隆仓库到本地: $resolvedTargetPath" "Trying to clone repository to local path: $resolvedTargetPath") -ForegroundColor Yellow
        if (-not (Test-Path $resolvedTargetPath)) {
            New-Item -ItemType Directory -Path $resolvedTargetPath -Force | Out-Null
        }
        & gh repo clone $TargetRepo $resolvedTargetPath | Out-Null
        if ($LASTEXITCODE -eq 0 -and (Test-Path $resolvedTargetPath)) {
            return (Resolve-Path $resolvedTargetPath).Path
        }
        Write-Host (BI "自动 clone 失败，将继续使用当前路径配置。" "Automatic clone failed. Continuing with the current path.") -ForegroundColor Yellow
    }

    if (-not $shouldClone) {
        Sync-TemplateRepoToLocal -TargetRepo $TargetRepo -TargetPath $resolvedTargetPath -Branch $Branch
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

function Get-RepoUrlBySlug([string]$RepoSlug) {
    if ([string]::IsNullOrWhiteSpace($RepoSlug)) {
        return ""
    }
    try {
        $repoInfo = gh repo view $RepoSlug --json url | ConvertFrom-Json
        if ($null -ne $repoInfo -and -not [string]::IsNullOrWhiteSpace([string]$repoInfo.url)) {
            return [string]$repoInfo.url
        }
    } catch {
    }
    return ""
}

function Ensure-RepoMatchesTarget {
    param(
        [string]$TargetPath,
        [string]$ExpectedRepoSlug,
        [string]$ExpectedRepoUrl
    )

    if ([string]::IsNullOrWhiteSpace($ExpectedRepoSlug) -or [string]::IsNullOrWhiteSpace($ExpectedRepoUrl)) {
        return
    }
    if ([string]::IsNullOrWhiteSpace($TargetPath) -or -not (Test-Path $TargetPath) -or -not (Test-Path (Join-Path $TargetPath ".git"))) {
        return
    }

    try {
        Push-Location $TargetPath
        $originUrl = Get-OriginUrlSilently
        $originSlug = Parse-RepoSlugFromUrl $originUrl
        if ($originSlug -ne $ExpectedRepoSlug) {
            if ([string]::IsNullOrWhiteSpace($originUrl)) {
                git remote add origin $ExpectedRepoUrl
            } else {
                git remote set-url origin $ExpectedRepoUrl
            }
            Write-Host (BI "已将本地仓库绑定到目标 GitHub 仓库。" "Local repository has been rebound to the target GitHub repository.") -ForegroundColor Yellow
        }
    } finally {
        Pop-Location
    }
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
                $originUrl = Get-OriginUrlSilently
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
        Write-Host (BI "跳过 Variable $Name（空值）" "Skipping Variable $Name (empty value)") -ForegroundColor Yellow
        return
    }
    gh variable set $Name --body $Value --repo $RepoSlug
    Write-Host (BI "已写入 Variable: $Name" "Variable written: $Name") -ForegroundColor Green
}

function Set-RepoSecretIfPresent([string]$RepoSlug, [string]$Name, [string]$Value) {
    if ([string]::IsNullOrWhiteSpace($Value)) {
        Write-Host (BI "跳过 Secret $Name（空值）" "Skipping Secret $Name (empty value)") -ForegroundColor Yellow
        return
    }
    gh secret set $Name --body $Value --repo $RepoSlug
    Write-Host (BI "已写入 Secret: $Name" "Secret written: $Name") -ForegroundColor Green
}

function Setup-Repository {
    param(
        [string]$TargetPath,
        [string]$RemoteUrl,
        [string]$ExpectedRepoSlug,
        [string]$Branch
    )

    if (Test-LooksLikeUrl $TargetPath) {
        Write-Host (BI "本地路径不能是 URL，已改为当前目录。" "Local path cannot be a URL. Falling back to the current directory.") -ForegroundColor Yellow
        $TargetPath = "."
    }

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

    if (-not [string]::IsNullOrWhiteSpace($ExpectedRepoSlug)) {
        $expectedRepoUrl = Get-RepoUrlBySlug $ExpectedRepoSlug
        if (-not [string]::IsNullOrWhiteSpace($expectedRepoUrl)) {
            $originUrl = Get-OriginUrlSilently
            $originSlug = Parse-RepoSlugFromUrl $originUrl
            if ($originSlug -ne $ExpectedRepoSlug) {
                if ([string]::IsNullOrWhiteSpace($originUrl)) {
                    git remote add origin $expectedRepoUrl
                } else {
                    git remote set-url origin $expectedRepoUrl
                }
                Write-Host (BI "已强制修正本地仓库绑定到目标 GitHub 仓库。" "Local repository binding has been force-corrected to the target GitHub repository.") -ForegroundColor Yellow
            }
        }
    }

    git lfs install --local | Out-Null

    $largeFileTrackerScript = Join-Path (Split-Path -Parent $MyInvocation.MyCommand.Path) "track_large_files_lfs.ps1"
    if (-not (Test-Path -LiteralPath $largeFileTrackerScript -PathType Leaf)) {
        throw (BI "未找到大文件 Git LFS 扫描脚本：track_large_files_lfs.ps1" "Large-file Git LFS scan script was not found: track_large_files_lfs.ps1")
    }
    & $largeFileTrackerScript -UiLanguage $UiLanguage -RepoPath $repoDir
    if ($LASTEXITCODE -ne 0) {
        throw (BI "执行大文件 Git LFS 扫描失败。" "Large-file Git LFS scan failed.")
    }

    if (Test-Path ".gitattributes") {
        $gitattributesStatus = cmd /c "git status --porcelain -- .gitattributes"
        if ($null -eq $gitattributesStatus) {
            $gitattributesChanged = $false
        } elseif ($gitattributesStatus -is [array]) {
            $gitattributesChanged = -not [string]::IsNullOrWhiteSpace(([string]::Join([Environment]::NewLine, $gitattributesStatus)).Trim())
        } else {
            $gitattributesChanged = -not [string]::IsNullOrWhiteSpace(([string]$gitattributesStatus).Trim())
        }
    } else {
        $gitattributesChanged = $false
    }

    cmd /c "git rev-parse --verify HEAD 1>nul 2>nul"
    $hasHeadCommit = ($LASTEXITCODE -eq 0)
    if ($gitattributesChanged) {
        git add .gitattributes
        if (-not $hasHeadCommit) {
            git commit -m "chore: initialize repository with git lfs" | Out-Null
        } else {
            git commit -m "chore: update git lfs tracking" | Out-Null
        }
    }

    $hasOriginRemote = $false
    $originList = (git remote) 2>$null
    if ($originList -is [array]) {
        $hasOriginRemote = $originList -contains "origin"
    } else {
        $hasOriginRemote = ([string]$originList).Trim() -eq "origin" -or ([string]$originList) -match '(^|\r?\n)origin(\r?\n|$)'
    }

    if ($hasOriginRemote) {
        try {
            git push -u origin $Branch
        } catch {
            Write-Host (BI "首次推送失败，请检查仓库权限或分支保护策略。" "Initial push failed. Please check repository permissions or branch protection rules.") -ForegroundColor Yellow
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

    Write-Step (BI "向导配置" "Wizard setup")
    $SkipGhLoginRef.Value = $false
    $SkipRepoSetupRef.Value = $false

    $RepoPathRef.Value = Ask-Input -Prompt (BI "本地路径（留空则当前目录）" "Local path (leave empty to use current directory)") -Default $RepoPathRef.Value
    $NewRepoRef.Value = Ask-Input -Prompt (BI "请输入新仓库（owner/repo 或 repo）" "New repository (owner/repo or repo)") -Default $NewRepoRef.Value
    $RepoVisibilityRef.Value = Ask-Visibility -Default $RepoVisibilityRef.Value

    $GitUserNameRef.Value = Ask-Input -Prompt (BI "Git 用户名" "Git user name") -Default $GitUserNameRef.Value
    $GitUserEmailRef.Value = Ask-Input -Prompt (BI "Git 邮箱" "Git email") -Default $GitUserEmailRef.Value

    Write-Host ""
    Write-Host (BI "仓库变量（回车跳过）" "Repository variables (press Enter to skip)") -ForegroundColor Cyan
    $RepoVarAutoUpdateWorkflowRef.Value = Ask-Input -Prompt (BI "AUTO_UPDATE_WORKFLOW（是否启用推送自动生成更新包，true/false）" "AUTO_UPDATE_WORKFLOW (enable auto update package generation on push, true/false)") -Default $RepoVarAutoUpdateWorkflowRef.Value
    $RepoVarVersionDcPathRef.Value = Ask-Input -Prompt (BI "VERSION_DC_PATH（version.dc 路径，默认 config/dlc_manager/version.dc）" "VERSION_DC_PATH (path to version.dc, default: config/dlc_manager/version.dc)") -Default $RepoVarVersionDcPathRef.Value
    $RepoVarClientBranchRef.Value = Ask-Input -Prompt (BI "CLIENT_BRANCH（自动模式判定为客户端包的分支名，逗号分隔）" "CLIENT_BRANCH (branches treated as client package in auto mode, comma-separated)") -Default $RepoVarClientBranchRef.Value
    $RepoVarServerBranchRef.Value = Ask-Input -Prompt (BI "SERVER_BRANCH（自动模式判定为服务端包的分支名，逗号分隔）" "SERVER_BRANCH (branches treated as server package in auto mode, comma-separated)") -Default $RepoVarServerBranchRef.Value
    $RepoVarAutoUpdatePlatformRef.Value = Ask-Input -Prompt (BI "AUTO_UPDATE_PLATFORM（自动模式平台：universal/client/server）" "AUTO_UPDATE_PLATFORM (auto mode platform: universal/client/server)") -Default $RepoVarAutoUpdatePlatformRef.Value
    $RepoVarAutoSyncToGiteeRef.Value = Ask-Input -Prompt (BI "AUTO_SYNC_TO_GITEE（自动模式是否同步到 Gitee，true/false）" "AUTO_SYNC_TO_GITEE (sync to Gitee in auto mode, true/false)") -Default $RepoVarAutoSyncToGiteeRef.Value
    $RepoVarGiteeUsernameRef.Value = Ask-Input -Prompt (BI "GITEE_USERNAME（Gitee 用户名）" "GITEE_USERNAME (Gitee user name)") -Default $RepoVarGiteeUsernameRef.Value
    $RepoVarGiteeRepoRef.Value = Ask-Input -Prompt (BI "GITEE_REPO（Gitee 仓库名）" "GITEE_REPO (Gitee repository name)") -Default $RepoVarGiteeRepoRef.Value

    Write-Host ""
    Write-Host (BI "仓库 Secret（回车跳过或不修改）" "Repository secrets (press Enter to skip or keep current)") -ForegroundColor Cyan
    $RepoSecretGiteeTokenRef.Value = Ask-Secret -Prompt (BI "GITEE_TOKEN（Gitee 访问令牌）" "GITEE_TOKEN (Gitee access token)") -Default $RepoSecretGiteeTokenRef.Value
}

$savedState = Read-ScriptState
if ($UiLanguage -eq "bi") {
    $savedLanguage = [string]$savedState["UiLanguage"]
    if ($savedLanguage -in @("cn", "en")) {
        $UiLanguage = $savedLanguage
    } else {
        $UiLanguage = Ask-Language -Default "cn"
    }
}

Write-Step (BI "检查基础依赖" "Check prerequisites")
Ensure-Winget

if (-not $ForceWizard) {
    $RepoUrl = Use-SavedValue -Current $RepoUrl -DefaultValue "" -SavedValue $savedState["RepoUrl"]
    $RepoPath = Use-SavedValue -Current $RepoPath -DefaultValue "." -SavedValue $savedState["RepoPath"]
    $GitUserName = Use-SavedValue -Current $GitUserName -DefaultValue "" -SavedValue $savedState["GitUserName"]
    $GitUserEmail = Use-SavedValue -Current $GitUserEmail -DefaultValue "" -SavedValue $savedState["GitUserEmail"]
    $DefaultBranch = Use-SavedValue -Current $DefaultBranch -DefaultValue "main" -SavedValue $savedState["DefaultBranch"]
    $NewRepo = Use-SavedValue -Current $NewRepo -DefaultValue "" -SavedValue $savedState["NewRepo"]
    $RepoVisibility = Use-SavedValue -Current $RepoVisibility -DefaultValue "private" -SavedValue $savedState["RepoVisibility"]
    $InstallGitHubDesktop = Use-SavedValue -Current $InstallGitHubDesktop -DefaultValue "true" -SavedValue $savedState["InstallGitHubDesktop"]
    $RepoVar_AUTO_UPDATE_WORKFLOW = Use-SavedValue -Current $RepoVar_AUTO_UPDATE_WORKFLOW -DefaultValue "true" -SavedValue $savedState["RepoVar_AUTO_UPDATE_WORKFLOW"]
    $RepoVar_VERSION_DC_PATH = Use-SavedValue -Current $RepoVar_VERSION_DC_PATH -DefaultValue "" -SavedValue $savedState["RepoVar_VERSION_DC_PATH"]
    $RepoVar_CLIENT_BRANCH = Use-SavedValue -Current $RepoVar_CLIENT_BRANCH -DefaultValue "" -SavedValue $savedState["RepoVar_CLIENT_BRANCH"]
    $RepoVar_SERVER_BRANCH = Use-SavedValue -Current $RepoVar_SERVER_BRANCH -DefaultValue "" -SavedValue $savedState["RepoVar_SERVER_BRANCH"]
    $RepoVar_AUTO_UPDATE_PLATFORM = Use-SavedValue -Current $RepoVar_AUTO_UPDATE_PLATFORM -DefaultValue "universal" -SavedValue $savedState["RepoVar_AUTO_UPDATE_PLATFORM"]
    $RepoVar_AUTO_SYNC_TO_GITEE = Use-SavedValue -Current $RepoVar_AUTO_SYNC_TO_GITEE -DefaultValue "false" -SavedValue $savedState["RepoVar_AUTO_SYNC_TO_GITEE"]
    $RepoVar_GITEE_USERNAME = Use-SavedValue -Current $RepoVar_GITEE_USERNAME -DefaultValue "" -SavedValue $savedState["RepoVar_GITEE_USERNAME"]
    $RepoVar_GITEE_REPO = Use-SavedValue -Current $RepoVar_GITEE_REPO -DefaultValue "" -SavedValue $savedState["RepoVar_GITEE_REPO"]
}

if (Test-LooksLikeUrl $RepoPath) {
    Write-Host (BI "检测到保存的本地路径是 URL，已重置为当前目录，请重新确认。" "Saved local path looks like a URL. It has been reset to the current directory. Please confirm it again.") -ForegroundColor Yellow
    $RepoPath = "."
    $ForceWizard = $true
}

if ($ForceWizard -or
    ([string]::IsNullOrWhiteSpace($RepoUrl) -and
    $RepoPath -eq "." -and
    [string]::IsNullOrWhiteSpace($GitUserName) -and
    [string]::IsNullOrWhiteSpace($GitUserEmail) -and
    $DefaultBranch -eq "main" -and
    [string]::IsNullOrWhiteSpace($NewRepo) -and
    $RepoVisibility -eq "private" -and
    (-not $SkipRepoSetup) -and
    (-not $SkipGhLogin))) {
    Run-InteractiveWizard `
        -RepoUrlRef ([ref]$RepoUrl) `
        -RepoPathRef ([ref]$RepoPath) `
        -GitUserNameRef ([ref]$GitUserName) `
        -GitUserEmailRef ([ref]$GitUserEmail) `
        -DefaultBranchRef ([ref]$DefaultBranch) `
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

Write-ScriptState @{
    UiLanguage = $UiLanguage
    RepoUrl = $RepoUrl
    RepoPath = $RepoPath
    GitUserName = $GitUserName
    GitUserEmail = $GitUserEmail
    DefaultBranch = $DefaultBranch
    NewRepo = $NewRepo
    RepoVisibility = $RepoVisibility
    InstallGitHubDesktop = $InstallGitHubDesktop
    RepoVar_AUTO_UPDATE_WORKFLOW = $RepoVar_AUTO_UPDATE_WORKFLOW
    RepoVar_VERSION_DC_PATH = $RepoVar_VERSION_DC_PATH
    RepoVar_CLIENT_BRANCH = $RepoVar_CLIENT_BRANCH
    RepoVar_SERVER_BRANCH = $RepoVar_SERVER_BRANCH
    RepoVar_AUTO_UPDATE_PLATFORM = $RepoVar_AUTO_UPDATE_PLATFORM
    RepoVar_AUTO_SYNC_TO_GITEE = $RepoVar_AUTO_SYNC_TO_GITEE
    RepoVar_GITEE_USERNAME = $RepoVar_GITEE_USERNAME
    RepoVar_GITEE_REPO = $RepoVar_GITEE_REPO
}

Write-Step (BI "安装 Git / Git LFS / GitHub CLI" "Install Git / Git LFS / GitHub CLI")
Install-WithWinget -Id "Git.Git" -DisplayName "Git" -CmdName "git"
Install-WithWinget -Id "GitHub.GitLFS" -DisplayName "Git LFS" -CmdName "git-lfs"
Install-WithWinget -Id "GitHub.cli" -DisplayName "GitHub CLI" -CmdName "gh"
Ensure-GitHubDesktop -InstallFlag $InstallGitHubDesktop

Write-Step (BI "配置 Git 全局身份" "Configure global Git identity")
Ensure-GitIdentity -Name $GitUserName -Email $GitUserEmail

Write-Step (BI "启用 Git LFS" "Enable Git LFS")
git lfs install | Out-Null

Write-Step (BI "配置 GitHub 认证" "Configure GitHub authentication")
Ensure-GhAuth -SkipLogin:$SkipGhLogin

$hasNewRepo = -not [string]::IsNullOrWhiteSpace($NewRepo)

$resolvedRepoPath = $RepoPath
if ($hasNewRepo) {
    Write-Step (BI "基于模板创建 GitHub 仓库" "Create GitHub repository from template")
    $resolvedRepoPath = Create-RepoFromTemplate -Template $TemplateRepo -TargetRepo $NewRepo -Visibility $RepoVisibility -TargetPath $RepoPath -Branch $DefaultBranch
    if ([string]::IsNullOrWhiteSpace($RepoUrl)) {
        $RepoUrl = Get-RepoUrlBySlug (Normalize-RepoSlug $NewRepo)
    }
}

$expectedRepoSlug = Resolve-RepoSlug -NewRepoValue $NewRepo -RepoUrlValue $RepoUrl -RepoPathValue $resolvedRepoPath
$expectedRepoUrl = Get-RepoUrlBySlug $expectedRepoSlug
Ensure-RepoMatchesTarget -TargetPath $resolvedRepoPath -ExpectedRepoSlug $expectedRepoSlug -ExpectedRepoUrl $expectedRepoUrl

if (-not $SkipRepoSetup) {
    Write-Step (BI "配置仓库" "Configure repository")
    Setup-Repository -TargetPath $resolvedRepoPath -RemoteUrl $RepoUrl -ExpectedRepoSlug $expectedRepoSlug -Branch $DefaultBranch
}

Write-Step (BI "写入 Actions Repository Variables（空值自动跳过）" "Write Actions repository variables (empty values are skipped)")
$repoSlug = Resolve-RepoSlug -NewRepoValue $NewRepo -RepoUrlValue $RepoUrl -RepoPathValue $resolvedRepoPath
if ([string]::IsNullOrWhiteSpace($repoSlug)) {
    Write-Host (BI "未能识别目标仓库，跳过 Variables 写入。" "Could not determine target repository. Skipping variable write.") -ForegroundColor Yellow
} else {
    Write-Host (BI "目标仓库: $repoSlug" "Target repository: $repoSlug")
    Set-RepoVariableIfPresent -RepoSlug $repoSlug -Name "AUTO_UPDATE_WORKFLOW" -Value $RepoVar_AUTO_UPDATE_WORKFLOW
    if (-not [string]::IsNullOrWhiteSpace($RepoVar_VERSION_DC_PATH) -and $RepoVar_VERSION_DC_PATH -ne $DefaultVersionDcPath) {
        Set-RepoVariableIfPresent -RepoSlug $repoSlug -Name "VERSION_DC_PATH" -Value $RepoVar_VERSION_DC_PATH
    } else {
        Write-Host (BI "跳过 Variable VERSION_DC_PATH（使用默认路径）" "Skipping Variable VERSION_DC_PATH (using default path)") -ForegroundColor Yellow
    }
    Set-RepoVariableIfPresent -RepoSlug $repoSlug -Name "CLIENT_BRANCH" -Value $RepoVar_CLIENT_BRANCH
    Set-RepoVariableIfPresent -RepoSlug $repoSlug -Name "SERVER_BRANCH" -Value $RepoVar_SERVER_BRANCH
    Set-RepoVariableIfPresent -RepoSlug $repoSlug -Name "AUTO_UPDATE_PLATFORM" -Value $RepoVar_AUTO_UPDATE_PLATFORM
    Set-RepoVariableIfPresent -RepoSlug $repoSlug -Name "AUTO_SYNC_TO_GITEE" -Value $RepoVar_AUTO_SYNC_TO_GITEE
    Set-RepoVariableIfPresent -RepoSlug $repoSlug -Name "GITEE_USERNAME" -Value $RepoVar_GITEE_USERNAME
    Set-RepoVariableIfPresent -RepoSlug $repoSlug -Name "GITEE_REPO" -Value $RepoVar_GITEE_REPO
    Set-RepoSecretIfPresent -RepoSlug $repoSlug -Name "GITEE_TOKEN" -Value $RepoSecret_GITEE_TOKEN
}

Write-Step (BI "完成" "Done")
Write-Host "Git:  $(git --version)"
Write-Host "LFS:  $(git lfs version)"
Write-Host "GH:   $(gh --version | Select-Object -First 1)"
Write-Host ""
Write-Host (BI "你现在可以直接使用 GitHub 仓库进行提交和推送。" "You can now commit and push directly to the GitHub repository.") -ForegroundColor Green
