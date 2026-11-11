# 颜色二维码识别

## 功能

- 摄像头实时预览，点「识别」抓取当前这一帧
- 一次可以识别画面里的多个二维码
- 输出每个二维码的内容 + 颜色名
- 结果在图上画框标注，点一下画面回到实时预览

## 实现原理

### 整体流程

```
摄像头抓帧（JPEG）
   │
   ▼
ImageSource.fromJpeg()  ──▶  Bitmap（最长边 ≤ 2048，已按旋转角旋正）
   │
   ▼
QrEngine.scan()
   ├── 第 1 遍：ML Kit 扫原图
   └── 没结果时：转灰度 + 对比度 ×2 再扫一次
   │
   ▼
每个 Barcode：取 rawValue + boundingBox
   │
   ▼
在 boundingBox 内采样颜色 ──▶ 色相归类 ──▶ 颜色名
   │
   ▼
List<Detect> ──▶ BoxPainter 画框 ──▶ 预览层 + 结果文本
```

### 1. 定位与解码

用 Google ML Kit 的 `BarcodeScanning`，格式限定 `FORMAT_QR_CODE`，把条形码和其它码制排除掉，减少误检。

### 2. 两次扫描

彩色二维码（尤其是黄、绿这类浅色）和背景的对比度低，ML Kit 第一遍经常检不出。所以第一遍没结果时，会把图转成灰度并拉高对比度（对比度矩阵 ×2）再扫一次。

**颜色判断永远用原图采样**，增强图只用来找位置——否则颜色会被灰度化毁掉。

### 3. 颜色判断

二维码里有大量黑白定位模块，直接对框内所有像素求平均，结果一定趋近灰色。所以：

1. 在二维码的 `boundingBox` 内每隔 3 个像素采样一次
2. **丢掉饱和度 < 0.30 或亮度 < 0.25 的像素**（黑白灰、过暗的部分）
3. 只对剩下的彩色像素求 RGB 均值
4. 把均值转成 HSV，按色相分段归类：

| 色相范围 | 判定 |
|---|---|
| < 20° 或 ≥ 330° | 红色 |
| 20° ~ 45° | 橙色 |
| 45° ~ 70° | 黄色 |
| 70° ~ 165° | 绿色 |
| 165° ~ 195° | 青色 |
| 195° ~ 260° | 蓝色 |
| 260° ~ 290° | 紫色 |
| 290° ~ 330° | 品红 |

如果一个彩色像素都没有，判为「无色」（也就是黑白二维码）。

## 代码架构

```
app/src/main/java/com/shizi/qr/
├── MainActivity.java        178 行   摄像头预览、抓帧、按钮与结果展示
├── img/
│   └── ImageSource.java      74 行   JPEG 解码 + 降采样 + 旋正
├── scan/
│   ├── Scanner.java          13 行   引擎接口
│   ├── Detect.java           17 行   一条结果
│   ├── ScanOutcome.java      17 行   结果集合 + 汇总文本
│   ├── BoxPainter.java       84 行   把框和标签画到图上
│   └── QrEngine.java        202 行   ★ 二维码识别与颜色判断
└── util/
    └── UiKit.java            11 行   Toast
```

### 文件

| 文件 | 职责 |
|---|---|
| `MainActivity` | 申请相机权限 → CameraX 预览 → `ImageCapture` 抓帧 → 调引擎 → 显示标注图和文字 |
| `ImageSource` | 把相机给的 JPEG 字节解码成 Bitmap，降采样到最长边 2048，按元数据旋转角旋正 |
| `Scanner` | 引擎接口，`scan(bitmap, callback)` + `close()`，让 `MainActivity` 不依赖具体实现 |
| `Detect` | 一条识别结果：`label`（框上的短标签）、`note`（详细内容）、`score`（置信度）、`box`（位置） |
| `ScanOutcome` | 结果列表 + 汇总后的多行文本 |
| `BoxPainter` | 把每个 `Detect` 的框和标签画到图片副本上 |
| `QrEngine` | **核心**：ML Kit 调用、两遍扫描策略、颜色采样与归类、结果拼装 |
| `UiKit` | Toast 封装 |

### 核心代码位置

| 位置 | 作用 |
|---|---|
| `QrEngine.scan()` | 两遍扫描的调度 |
| `QrEngine.boostContrast()` | 灰度 + 对比度 ×2 |
| `QrEngine.dominantColor()` | **颜色判断核心**：彩色像素采样 |
| `QrEngine.colorName()` | 色相分段归类 |
| `QrEngine.describe()` | 拼接结果文本 |

## 环境要求

| 项 | 版本 |
|---|---|
| Android Studio | 2021.3.1（Dolphin）及以上 |
| JDK | 11 或 17（Android Studio 自带的 JBR 即可） |
| Android Gradle Plugin | 7.3.1 |
| Gradle | 7.4 |
| compileSdk / targetSdk | 34 |
| minSdk | 24（Android 7.0） |
| NDK / CMake | **不需要** |
| 设备 | 需要摄像头，真机最好，模拟器要开虚拟摄像头 |

## 编译运行

1. 用 Android Studio 打开本目录
2. 首次打开会自动生成 `local.properties`（记录本机 SDK 路径），不要提交到版本库
3. 连接设备，点 Run
4. 首次启动会请求相机权限，允许后即可看到实时画面
5. 对准二维码，点「识别」

命令行构建：

```bash
./gradlew assembleDebug
```

产物在 `app/build/outputs/apk/debug/app-debug.apk`。

> 工程放在中文路径下时 AGP 会报非 ASCII 路径错误，本工程已在 `gradle.properties` 里加了一行 `android.overridePathCheck=true`，直接编译即可。

## 依赖

| 依赖 | 用途 | 许可 |
|---|---|---|
| [ML Kit Barcode Scanning 17.2.0](https://developers.google.com/ml-kit/vision/barcode-scanning/android) | 二维码定位与解码 | Google SDK 条款（闭源） |
| CameraX 1.3.0（core / camera2 / lifecycle / view） | 摄像头预览与抓帧 | Apache-2.0 |
| AndroidX AppCompat、Activity、Core | 基础 UI | Apache-2.0 |

ML Kit 的模型随 APK 一起打包，首次运行不需要联网下载。

> 构建时 ML Kit 会在清单里合并 `INTERNET` 和 `ACCESS_NETWORK_STATE` 权限（库自身声明），识别本身离线完成。
>
