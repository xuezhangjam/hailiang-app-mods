# 海亮应用修改版归档

整理星学伴、班级通知、学生端课堂和精准作业的修改版 APK，包含当前功能版与历史版本。APK 位于 [Releases](https://github.com/xuezhangjam/hailiang-app-mods/releases)，仓库提供版本说明和 SHA256 校验值。

> [!IMPORTANT]
> 本项目为个人维护的非官方修改版归档，不代表海亮、学校或相关产品厂商，也不表示获得其官方认可或授权背书。**GPL-3.0 仅适用于维护者有权许可的原创内容，不将原 APK、第三方代码、资源或商标改为 GPL。** 使用前请阅读[许可范围与第三方权利](NOTICE.md)及[项目声明](DISCLAIMER.md)。

## 下载

| 应用 | 版本 | 入口 |
|---|---|---|
| 星学伴 | 3.2.1.4 · AI 弹窗 Logo 版及历史包 | [最新版 APK](https://github.com/xuezhangjam/hailiang-app-mods/releases/download/launcher-3.2.1.4/starstudy-3.2.1.4-ai-logos.apk) · [历史包](https://github.com/xuezhangjam/hailiang-app-mods/releases/tag/launcher-3.2.1.4) |
| 星学伴旧版 | 3.2.1.0 · 九个历史版本 | [归档](https://github.com/xuezhangjam/hailiang-app-mods/releases/tag/launcher-3.2.1.0-history) |
| 班级通知 | 1.0.6 · M3e 原生平板版 | [下载](https://github.com/xuezhangjam/hailiang-app-mods/releases/tag/notice-1.0.6-m3e) |
| 学生端课堂 | 3.2.1.0 · v7 续答结果显示修复 | [v7 APK](https://github.com/xuezhangjam/hailiang-app-mods/releases/download/classroom-3.2.1.0-v7/student-classroom-3.2.1.0-continuation-results-v7.apk) · [更新与源码](https://github.com/xuezhangjam/hailiang-app-mods/releases/tag/classroom-3.2.1.0-v7) · [旧分支](https://github.com/xuezhangjam/hailiang-app-mods/releases/tag/classroom-3.2.1.0-mods) |
| 精准作业学生端 | 4.0.0.4 · 答案常显、解析默认收起 | [下载](https://github.com/xuezhangjam/hailiang-app-mods/releases/tag/homework-4.0.0.4-mod) |

完整文件名、功能说明和已知问题见 [版本目录](docs/versions.md)。共归档 **23 个 APK**。

## 学生端课堂 v7

手写题继续作答上传成功后，“我的作答”显示本次上传的笔迹，并重新读取课堂结果。尚未上传的新修改不混入结果；“展优作答”保持原逻辑。云端尚未返回相同笔迹时，显示版本标记为“本次续答已上传”，不代表老师端或后台覆盖已确认。

保留前版的 PPT 宿主测量、分屏底栏、题目返回、相册、AI 与画中画功能。包名为 `com.hljzjx.edu.student.classroom`，版本 `3.2.1.0 / 321000`。本地测试、签名与差异检查通过；尚无 Pixel Tablet 和学校服务器联调结果。

[发布与独立 APK 下载](https://github.com/xuezhangjam/hailiang-app-mods/releases/tag/classroom-3.2.1.0-v7) · [源码压缩包](https://github.com/xuezhangjam/hailiang-app-mods/releases/download/classroom-3.2.1.0-v7/student-classroom-continuation-results-v7-source.zip) · [可浏览辅助源码与构建脚本](apk-analysis/classroom-continuation-results-v7) · [详细说明](docs/classroom-v7.md)

## 星学伴当前版

基于 3.2.1.4，迁入旧版界面、MD3e／原版 UI 切换、独立课表与待办卡片、Day 轮转课表及通知，保留官方应用排序。AI 问问支持 ChatGPT、Gemini、DeepSeek 和海亮自带入口，使用自绘圆角弹窗及 Logo 图标。

当前包通过离线检查，自绘弹窗和完整功能仍待实机验收。历史包中包含已确认的登录故障和白底弹窗问题，已在版本目录标出，不能当作当前版。

## 安装与校验

本归档保留 APK 原始内容，不重新签名。同包名但签名不同的版本不能直接覆盖安装。可通过 [release-index.json](release-index.json) 查看包名、版本、签名证书指纹与原文件名，通过 [SHA256SUMS.txt](SHA256SUMS.txt) 校验下载文件。

## 许可证与项目声明

维护者有权许可、并以源码形式发布的原创代码、独立脚本、补丁和原创文档采用 **GNU GPL v3.0 only（SPDX: `GPL-3.0-only`）**，完整条款见 [LICENSE](LICENSE)，具体适用范围见 [NOTICE.md](NOTICE.md)。完整 APK 与第三方内容不因本仓库添加 LICENSE 而整体改用 GPL，也不因此取得原软件的分发授权。

GPL 允许商业使用；维护者免费共享项目的定位不构成额外的“禁止商用”许可条件。分发 GPL 覆盖作品时，应按标准条款履行对应源码等义务。当前仓库主要提供 APK 归档和版本说明，添加许可证不代表已发布每个历史安装包的完整对应源码。

非官方身份、使用边界、无担保及权利反馈方式见 [DISCLAIMER.md](DISCLAIMER.md)。声明不构成学校或厂商授权，不保证免除法律责任。

## 贡献者

- [xuezhangjam](https://github.com/xuezhangjam)：原有修改版设计、需求与实机反馈。
- **Codex**：AI 协作开发，参与版本迁移、界面和功能修改、问题排查、打包与发布整理。
