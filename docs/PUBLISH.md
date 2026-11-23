# 开源发布指南（维护者用）

把「广职大课表」以 GPLv3 发布到 GitHub 的完整流程。命令都在 **Git Bash** 里执行，路径按需替换。

> 仓库地址约定：`https://github.com/Campsisian/GPU-Timetable`
> 项目目录：`C:\Users\17391\WorkBuddy\2026-11-15-20-37-42\gzpyp-timetable`
> 注意：**目录名（gzpyp-timetable）和仓库名（GPU-Timetable）不一样没关系**，git 不要求一致。

---

## 第 0 步：检查 Git 是否可用

```bash
git --version
git config --global user.name "Campsisian"
git config --global user.email "你的GitHub邮箱"
```

`user.email` 建议填 GitHub 账号绑定的邮箱，这样提交记录才会算在你名下、头像也能对上。

如果你开了两步验证（推荐开），推送 HTTPS 时不能用账号密码，要用 **Personal Access Token**，见第 4 步。

---

## 第 1 步：本地初始化仓库

```bash
cd /c/Users/17391/WorkBuddy/2026-11-15-20-37-42/gzpyp-timetable

git init
git branch -M main
```

此时还没有任何文件被跟踪。

---

## 第 2 步：确认要提交什么（**这步不能跳**）

```bash
git add .
git status
```

逐条看输出的文件列表，必须**不含**下面这类：

| 不能提交 | 原因 |
|---|---|
| `keystore.properties` | 里面是签名证书的密码 |
| `keystore/*.jks` | 签名证书本体，泄露等于别人能冒充你发版 |
| `local.properties` | 本机 SDK 路径，与别人无关 |
| `*.apk` | 50 MB，不该进 git 历史（走 Releases 分发） |
| `build/`、`.gradle/`、`*.log` | 构建产物和日志 |

这些都写在 `.gitignore` 里了，正常情况下不会出现。如果 `git status` 里看到了，说明 `.gitignore` 没生效，**先别提交**，检查一下文件名大小写或路径。

确认干净后：

```bash
git commit -m "feat: 广职大课表 v2.7.2，GPLv3 开源首版"
```

---

## 第 3 步：在 GitHub 上建空仓库

1. 打开 <https://github.com/new>
2. **Repository name** 填 `GPU-Timetable`
3. **Description** 可以填：`广州职业技术大学教务系统课表客户端 · Kotlin + Material 3`
4. 可见性选 **Public**（要开源必须 Public；Private 仓库不算开源）
5. **下面四个勾全部不要勾**：
   - ❌ Add a README file
   - ❌ Add .gitignore
   - ❌ Choose a license
   - ❌（如果出现）Add a template
6. 点 **Create repository**

之所以不勾：你本地已经有 `README.md`、`.gitignore`、`LICENSE` 了。如果 GitHub 上先生成一份，push 时会报 `rejected — fetch first`（历史不相关，无法合并），新手很容易卡在这里。

建好后页面会显示一段命令，先不用管，看第 4 步。

---

## 第 4 步：关联远程并推送

### 方式 A：HTTPS + Token（最省事）

1. 生成 Token：GitHub 右上角头像 → **Settings** → 左侧最下 **Developer settings** → **Personal access tokens** → **Tokens (classic)** → **Generate new token (classic)**
2. Note 随便写（如 `push-timetable`），Expiration 选 90 天
3. 勾选 **`repo`** 这一项（必须，其它不用勾）
4. 生成后**立刻复制**那串 `ghp_xxxx`，页面关掉就再也看不到了

然后：

```bash
cd /c/Users/17391/WorkBuddy/2026-11-15-20-37-42/gzpyp-timetable

git remote add origin https://github.com/Campsisian/GPU-Timetable.git
git push -u origin main
```

弹出登录框时：
- 用户名：`Campsisian`
- 密码：**粘贴刚才的 `ghp_...` Token**（不是你的 GitHub 登录密码）

Windows 会把它记进「凭据管理器」，之后 push 不用再输。

### 方式 B：SSH（一次配置，长期免密）

```bash
ssh-keygen -t ed25519 -C "你的GitHub邮箱"     # 一路回车，密码可留空
cat ~/.ssh/id_ed25519.pub                      # 复制输出的一整行
```

把这一行粘到 GitHub → Settings → **SSH and GPG keys** → **New SSH key**，然后：

```bash
git remote add origin git@github.com:Campsisian/GPU-Timetable.git
git push -u origin main
```

首次连接会问 `Are you sure you want to continue connecting?`，输 `yes` 回车。

### 推送慢或卡住

你本机有本地代理，可以只给 GitHub 走代理：

```bash
git config --global http.https://github.com.proxy http://127.0.0.1:36097
```

取消：

```bash
git config --global --unset http.https://github.com.proxy
```

---

## 第 5 步：发第一个 Release（APK 从这里下载）

APK 不进 git，放在 Release 里。

1. 仓库页右侧 **Releases** → **Create a new release**
2. **Choose a tag** 输入 `v2.7.2` → 点 “Create new tag: v2.7.2 on publish”
3. **Release title**：`广职大课表 v2.7.2`
4. **Describe this release** 可以写：

   ```markdown
   GPLv3 开源首版。

   - 完整 Material 3 界面，支持浅色 / 深色 / 跟随系统
   - 教务系统自动登录 + 本机验证码识别（准确率 89.3%）
   - 桌面小组件（可滚动列表）
   - 上课提醒（精确闹钟）

   系统要求 Android 8.0+，支持 arm64-v8a / armeabi-v7a。

   SHA-256：`（粘贴 apk 的哈希）`
   ```

5. 把 `广职大课表-v2.7.2.apk` 拖进 **Attach binaries** 区域（单个文件上限 2 GB，50 MB 没问题）
6. 点 **Publish release**

算 APK 哈希给下载者校验：

```bash
cd /c/Users/17391/WorkBuddy/2026-11-15-20-37-42/gzpyp-timetable
sha256sum "广职大课表-v2.7.2.apk"
```

---

## 第 6 步：把仓库门面配好

### About（右侧信息栏）

仓库首页右上角齿轮 ⚙ → 编辑：

- **Description**：`广州职业技术大学（广职大）教务系统课表客户端 · Kotlin + Jetpack Compose + Material 3`
- **Website**：可留空
- **Topics**（影响被搜到的概率，逐个添加）：
  `android` `kotlin` `jetpack-compose` `material-design` `timetable` `schedule`
  `widget` `onnxruntime` `captcha` `gplv3` `jsxsd` `school-project`

### 确认协议被识别

首页右侧应显示 **GPL-3.0 license**。如果显示 “View license” 或识别不出，多半是 `LICENSE` 文件被改坏了，重新从 <https://www.gnu.org/licenses/gpl-3.0.txt> 下载原文覆盖。

### 社交预览图（分享到微信/QQ 时的缩略图）

Settings → General → 最下 **Social preview** → 上传一张 1280×640 的 PNG。
`docs/banner.svg` 是矢量图，GitHub 不接受 SVG 作社交预览，需要导出成 PNG（浏览器打开 SVG → 截图，或用 Inkscape 导出）。

---

## 第 7 步：以后每次发新版

```bash
cd /c/Users/17391/WorkBuddy/2026-11-15-20-37-42/gzpyp-timetable

# 1. 改版本号：app/build.gradle.kts 里 versionCode +1、versionName 改掉
# 2. 打包
export JAVA_HOME="C:/Users/17391/AndroidDev/jdk/jdk-17.0.21+9"
cmd //c "C:\\Users\\17391\\AndroidDev\\gradle-8.7\\bin\\gradle.bat :app:assembleRelease"
cp app/build/outputs/apk/release/app-release.apk "广职大课表-vX.Y.Z.apk"

# 3. 提交源码
git add -A
git commit -m "release: vX.Y.Z"
git push

# 4. 打 tag 并推送（可选，GitHub 也会自动建）
git tag vX.Y.Z
git push origin vX.Y.Z
```

然后到 Releases 页面 **Draft a new release** → 选刚推的 tag → 上传新 APK。

**关键**：必须用**同一个** `keystore/gpu-release.jks` 签名，否则老用户无法覆盖升级（系统报“签名不一致”）。

---

## 常见错误对照表

| 报错 | 原因 | 解决 |
|---|---|---|
| `rejected — fetch first` / `non-fast-forward` | GitHub 上已有一份提交（通常是建仓库时勾了 README） | `git pull --rebase origin main` 后再 push；或删掉远程仓库重建（不勾任何文件） |
| `Authentication failed` | 用账号密码代替了 Token | 重新生成 Token，勾 `repo`，密码框粘 Token |
| `remote: Support for password authentication was removed` | 同上 | 同上 |
| `Permission denied (publickey)` | SSH key 没加到 GitHub，或用了 HTTPS 地址配 SSH | 检查 `git remote -v` 地址格式，`ssh -T git@github.com` 测试 |
| `file is 105 MB; this exceeds GitHub's file size limit` | 把 APK 或模型提交进去了 | `git rm --cached 大文件`，加进 `.gitignore`，再 `git commit --amend`；历史里已有则需 `git filter-repo` 清理 |
| push 卡住 / `Failed to connect` | 网络 | 配第 4 步的代理，或改用 SSH |
| 提交记录作者不是自己 | `user.email` 不是 GitHub 邮箱 | 改配置后 `git commit --amend --reset-author` |
| `fatal: not a git repository` | 目录没初始化，或不在项目目录 | `cd` 到项目目录再执行 |
| Release 里 APK 下载后无法安装 | 签名与旧版不一致 / 下载不完整 | 对比 SHA-256；确认同一 keystore |

---

## 安全红线

1. **永远不要** `git add keystore/` 或 `keystore.properties`。一旦推上去，任何人拿到就能给你的 App 签恶意更新。
   - 如果不小心推了：立刻换新 keystore（代价是所有用户要卸载重装），并把旧文件从历史里彻底清掉（`git filter-repo --path keystore --invert-paths`）。
2. Token 泄露的处理：GitHub → Settings → Developer settings → 对应 token → **Delete**，重新生成。
3. 仓库里不要出现真实学号、姓名、成绩单。测试样本 `app/src/test/resources/main.html` 已脱敏为「张三 / 2026000001 / 示例学院」。
4. `local.properties`、`keystore.properties` 里可能带本机绝对路径与密码，`.gitignore` 已排除。

---

## 备份清单（比推送更重要）

开源之后这套东西丢了会很麻烦，建议复制到 U 盘 / 私有网盘：

- `keystore/gpu-release.jks` —— 签名证书
- `keystore.properties` —— 证书密码
- 证书指纹记录（alias `gpu`，有效期至 2056 年 11 月）：
  - SHA-256：`c2b8ef5407c00bfaa91ffea8025f7bad8f17c45aab3a9d913d734b47a3d1cc5b`
  - SHA-1：`e796e7549441adca43953252eec7951a50e57d82`
  - 主题：`CN=GPU Timetable, OU=Personal, O=Campsisian, L=Guangzhou, ST=Guangdong, C=CN`

> 证书主题为 `CN=GPU Timetable, O=Campsisian`，与仓库名一致。**一旦对外发过版本，就不要再换证书**——换了证书，所有已安装的手机都无法覆盖升级，必须卸载重装。
