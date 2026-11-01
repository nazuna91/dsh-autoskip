# DSH AutoSkip

自动点掉 Lanerc（`com.clggjv.xcjfmd.ffo`）换集时弹的「拯救 Lanerc 计划」激励广告弹窗。

## 为什么不用别的办法

```text
DNS 拦截   弹窗是应用自己用 Flutter 画的，没有网络请求可拦；素材拦掉了弹窗照旧
LSPosed    壳 libjgccxf 检测注入，模块一挂进程就死
改 APK     壳加密了 dex，重打包要脱壳，成本极高
```

无障碍服务是唯一不碰进程、不碰 root 的路：它只读界面文字，看到「暂时跳过」就点一下。

## 原理

```text
AccessibilityService 监听 com.clggjv.xcjfmd.ffo 的窗口事件
→ 广度优先遍历语义树（深度 24）
→ 文本/描述命中「暂时跳过 / 跳过 / 关闭」→ 找最近的可点击祖先 → performAction(CLICK)
```

## 用法

```text
1. 安装 APK
2. 打开 App → 点「去开启无障碍服务」
3. 设置 → 无障碍 → 已下载的应用 → DSH AutoSkip → 开启
4. 回 Lanerc 换集，弹窗出现时自动点「暂时跳过」
```

App 首页会显示已自动跳过的次数，方便确认在工作。

## 局限

```text
· Flutter 只有在检测到无障碍服务时才开启语义树，所以必须先开服务再看效果
· 如果按钮文字变了（比如改成「跳过 3s」），改 SKIP_TEXTS 重现编译
· 弹窗若用纯 Canvas 绘制且无语义节点，读不到文字 → 需要退回坐标点击
```

## 构建

GitHub Actions 自动出 `app-debug.apk`，或 Android Studio 打开本目录 Build APK。
