// 文件路径: composeApp/src/jvmMain/kotlin/org/eu/freex/tools/viewmodel/ImageProcessingViewModel.kt
package org.eu.freex.tools.viewmodel

import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.IntOffset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.eu.freex.tools.model.*
import org.eu.freex.tools.utils.ImageUtils
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

class ImageProcessingViewModel {
    private val scope = CoroutineScope(Dispatchers.Main)

    // --- 核心状态 ---
    var sourceImages = mutableStateListOf<WorkImage>()
    var selectedSourceIndex by mutableStateOf(-1)

    // ... (规则、画布状态保持不变) ...
    var globalColorRules = mutableStateListOf<ColorRule>()
    var globalBias by mutableStateOf("101010")
    var globalFixedRects = mutableStateListOf<Rect>()
    var currentScope by mutableStateOf(RuleScope.GLOBAL)

    var mainScale by mutableStateOf(1f)
    var mainOffset by mutableStateOf(Offset.Zero)
    var hoverPixelPos by mutableStateOf<IntOffset?>(null)
    var hoverColor by mutableStateOf(Color.Transparent)

    var currentFilter by mutableStateOf<ImageFilter>(ViewFilter)

    // 阈值与网格
    var thresholdRange by mutableStateOf(0f..72f)
    var isRgbAvgEnabled by mutableStateOf(true)
    var isGridMode by mutableStateOf(false)
    var gridParams by mutableStateOf(GridParams(0, 0, 15, 15, 0, 0, 1, 1))

    // --- 流水线与结果 ---
    var pipelineSteps = mutableStateListOf<WorkImage>()
    var binaryPreview by mutableStateOf<WorkImage?>(null)
    var selectedPipelineIndex by mutableStateOf(0)

    // 【新增】切割结果列表 (存放拆分出来的字模)
    var segmentationResults = mutableStateListOf<WorkImage>()

    // 【新增】当前右侧面板的 Tab 索引 (0: 滤镜处理, 1: 切割识别)
    var rightPanelTabIndex by mutableStateOf(0)

    // ... (UI 交互状态保持不变) ...
    var showScreenCropper by mutableStateOf(false)
    var fullScreenCapture by mutableStateOf<BufferedImage?>(null)
    var showMappingDialog by mutableStateOf(false)
    var mappingBitmap by mutableStateOf<ImageBitmap?>(null)
    var mappingBufferedImg by mutableStateOf<BufferedImage?>(null)
    val fontLibrary = mutableStateListOf<FontItem>()

    // ... (Getter 属性保持不变) ...
    val currentSourceImage: WorkImage? get() = if (selectedSourceIndex in sourceImages.indices) sourceImages[selectedSourceIndex] else null
    val activeColorRules: List<ColorRule>
        get() = currentSourceImage?.localColorRules ?: globalColorRules
    val activeBias: String get() = currentSourceImage?.localBias ?: globalBias
    val activeBaseImage: WorkImage? get() = if (pipelineSteps.isNotEmpty()) pipelineSteps.last() else currentSourceImage

    val displayChain: List<WorkImage>
        get() {
            val list = mutableListOf<WorkImage>()
            if (currentSourceImage != null) list.add(currentSourceImage!!.copy(label = "原图"))
            list.addAll(pipelineSteps)
            return list
        }

    val currentWorkImage: WorkImage?
        get() = if (displayChain.isNotEmpty() && selectedPipelineIndex in displayChain.indices) {
            displayChain[selectedPipelineIndex]
        } else {
            activeBaseImage
        }

    var activeRects by mutableStateOf<List<Rect>>(emptyList())
        private set

    init {
        // ... (原有的 snapshotFlow 监听保持不变) ...

        snapshotFlow { selectedSourceIndex }.onEach {
            pipelineSteps.clear()
            binaryPreview = null
            selectedPipelineIndex = 0
            currentFilter = ViewFilter
            activeRects = emptyList()
            segmentationResults.clear() // 切换图片清空结果
        }.launchIn(scope)

        snapshotFlow { activeBaseImage }.onEach {
            activeRects = emptyList()
            segmentationResults.clear() // 基础图变化(如添加了新滤镜)清空旧结果
        }.launchIn(scope)

        // ... (其他监听保持不变) ...
        snapshotFlow { selectedPipelineIndex }.onEach { idx ->
            if (idx > 0 && idx <= pipelineSteps.size) {
                val step = pipelineSteps[idx - 1]
                if (step.params.containsKey("type") && step.params["type"] == "BINARIZATION") {
                    val min = (step.params["min"] as? Int) ?: 0
                    val max = (step.params["max"] as? Int) ?: 72
                    val rgb = (step.params["rgbAvg"] as? Boolean) ?: true
                    thresholdRange = min.toFloat()..max.toFloat()
                    isRgbAvgEnabled = rgb
                    currentFilter = ColorFilterType.BINARIZATION
                }
            }
        }.launchIn(scope)

        snapshotFlow { Pair(thresholdRange, isRgbAvgEnabled) }.onEach {
            if (selectedPipelineIndex > 0 && currentFilter == ColorFilterType.BINARIZATION) {
                val stepIndex = selectedPipelineIndex - 1
                if (stepIndex < pipelineSteps.size && pipelineSteps[stepIndex].params["type"] == "BINARIZATION") {
                    updateExistingBinarizationStep(stepIndex)
                }
            }
        }.launchIn(scope)
    }

    // 【修改】执行分割：计算框 + 裁剪小图
    fun performSegmentation() {
        scope.launch {
            computeRectsAndCrop()
        }
    }

    private suspend fun computeRectsAndCrop() {
        val base = activeBaseImage ?: return

        withContext(Dispatchers.Default) {
            val rawImg = base.bufferedImage

            withContext(Dispatchers.Main) {
                binaryPreview = null
                segmentationResults.clear() // 清空旧结果
            }

            // 1. 计算切割框 (Rects)
            val imgToSegment =
                if (currentWorkImage?.isBinary == true) currentWorkImage!!.bufferedImage else rawImg
            val rects = mutableListOf<Rect>()

            if (!currentSourceImage!!.localCropRects.isNullOrEmpty()) {
                rects.addAll(currentSourceImage!!.localCropRects!!)
            } else {
                if (isGridMode) {
                    rects.addAll(
                        ImageUtils.generateGridRects(
                            gridParams.x,
                            gridParams.y,
                            gridParams.w,
                            gridParams.h,
                            gridParams.colGap,
                            gridParams.rowGap,
                            gridParams.colCount,
                            gridParams.rowCount
                        )
                    )
                } else {
                    val rules = if (currentWorkImage?.isBinary == true) {
                        listOf(ColorRule(targetHex = "FFFFFF", biasHex = "000000"))
                    } else {
                        activeColorRules.filter { it.isEnabled }
                    }

                    if (rules.isNotEmpty()) {
                        try {
                            // A. 图片转字节
                            val imageBytes = bufferedImageToBytes(imgToSegment)

                            // B. 转换规则对象 (UI Model -> Rust Model)
                            val rustRules = rules.map { rule ->
                                uniffi.touch_core.ColorRule(
                                    id = rule.id,
                                    targetHex = rule.targetHex,
                                    biasHex = rule.biasHex,
                                    isEnabled = rule.isEnabled
                                )
                            }

                            // C. 调用 Rust 接口
                            val rustRects = uniffi.touch_core.scanComponents(imageBytes, rustRules)

                            // D. 转换结果 (Rust Rect -> Compose Rect)
                            val composeRects = rustRects.map { r ->
                                Rect(
                                    left = r.left.toFloat(),
                                    top = r.top.toFloat(),
                                    right = r.left.toFloat() + r.width.toFloat(),
                                    bottom = r.top.toFloat() + r.height.toFloat()
                                )
                            }
                            rects.addAll(composeRects)
                        } catch (e: Exception) {
                            e.printStackTrace() // 处理 Rust 抛出的 VisionError
                            println("Rust Segmentation Failed: ${e.message}")
                        }
                    }
                    rects.addAll(globalFixedRects)
                }
            }

            // 2. 根据 Rects 裁剪出小图
            val results = rects.mapIndexed { index, rect ->
                // 注意：这里从 imgToSegment (可能是二值化图) 中裁剪，也可以选择从 rawImg (原图) 裁剪
                // 通常字库制作是基于二值化结果的
                val cropped = ImageUtils.cropImage(imgToSegment, rect)
                WorkImage(
                    bitmap = cropped.toComposeImageBitmap(),
                    bufferedImage = cropped,
                    name = "${index}", // 这里的名字可以作为字库的 Key
                    label = "$index",
                    isBinary = currentWorkImage?.isBinary == true
                )
            }

            withContext(Dispatchers.Main) {
                activeRects = rects
                segmentationResults.addAll(results)

                // (可选) 在流水线里加一个记录，模仿截图逻辑
                // addProcessingStep("连通区域") { it }
            }
        }
    }

    private suspend fun updateExistingBinarizationStep(stepIndex: Int) {
        val inputImage = displayChain[stepIndex].bufferedImage
        val min = thresholdRange.start.toInt()
        val max = thresholdRange.endInclusive.toInt()
        val useRgbAvg = isRgbAvgEnabled
        val newParams =
            mapOf("type" to "BINARIZATION", "min" to min, "max" to max, "rgbAvg" to useRgbAvg)
        withContext(Dispatchers.Default) {
            try {
                val inputBytes = bufferedImageToBytes(inputImage)
                val rustFilter = uniffi.touch_core.ImageFilter.Color(uniffi.touch_core.ColorFilterType.BINARIZATION)

                // 【核心修改】传入 min 和 max
                val outputBytes = uniffi.touch_core.applyFilter(inputBytes, rustFilter, min, max)
                val newBitmap = bytesToBufferedImage(outputBytes)
                val updatedStep = pipelineSteps[stepIndex].copy(
                    bitmap = newBitmap.toComposeImageBitmap(),
                    bufferedImage = newBitmap,
                    params = newParams
                )
                withContext(Dispatchers.Main) { pipelineSteps[stepIndex] = updatedStep }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun loadFile(file: File) {
        scope.launch(Dispatchers.IO) {
            try {
                val img = ImageIO.read(file)
                if (img != null) withContext(Dispatchers.Main) {
                    sourceImages.add(WorkImage(img.toComposeImageBitmap(), img, file.name))
                    selectedSourceIndex = sourceImages.lastIndex
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun startScreenCapture() {
        scope.launch(Dispatchers.IO) {
            kotlinx.coroutines.delay(300);
            val capture = ImageUtils.captureFullScreen(); withContext(Dispatchers.Main) {
            fullScreenCapture = capture; showScreenCropper = true
        }
        }
    }

    fun confirmCrop(rect: Rect) {
        addProcessingStep("裁剪区域") { src ->
            ImageUtils.cropImage(
                src,
                rect
            )
        }; if (currentScope == RuleScope.GLOBAL) globalFixedRects.clear()
    }

    fun confirmScreenCrop(cropped: BufferedImage) {
        showScreenCropper = false; sourceImages.add(
            WorkImage(
                cropped.toComposeImageBitmap(),
                cropped,
                "截图 ${sourceImages.size + 1}"
            )
        ); selectedSourceIndex = sourceImages.lastIndex
    }

    fun addProcessingStep(
        label: String,
        isBinary: Boolean = false,
        params: Map<String, Any> = emptyMap(),
        processor: (BufferedImage) -> BufferedImage
    ) {
        val base = activeBaseImage ?: return
        scope.launch(Dispatchers.Default) {
            val processed = processor(base.bufferedImage)
            val newStep = WorkImage(
                processed.toComposeImageBitmap(),
                processed,
                "Step_${pipelineSteps.size}",
                label,
                isBinary = isBinary,
                params = params
            )
            withContext(Dispatchers.Main) {
                pipelineSteps.add(newStep); selectedPipelineIndex = 1 + pipelineSteps.lastIndex
            }
        }
    }

    fun handleProcessAdd(filter: ImageFilter) {
        val min = thresholdRange.start.toInt()
        val max = thresholdRange.endInclusive.toInt()

        // 1. 将 UI 层的滤镜模型 (Kotlin) 映射为 Rust 层的枚举 (UniFFI 生成)
        // 注意：根据您的包名配置，uniffi.touch_core 可能需要调整为实际的包名
        val rustFilter: uniffi.touch_core.ImageFilter? = when (filter) {
            // --- 彩色滤镜 ---
            ColorFilterType.BINARIZATION -> uniffi.touch_core.ImageFilter.Color(uniffi.touch_core.ColorFilterType.BINARIZATION)
            ColorFilterType.GRAYSCALE -> uniffi.touch_core.ImageFilter.Color(uniffi.touch_core.ColorFilterType.GRAYSCALE)
            ColorFilterType.COLOR_PICK -> uniffi.touch_core.ImageFilter.Color(uniffi.touch_core.ColorFilterType.COLOR_PICK)
            ColorFilterType.POSTERIZE -> uniffi.touch_core.ImageFilter.Color(uniffi.touch_core.ColorFilterType.POSTERIZE)

            // --- 黑白/二值化后滤镜 ---
            BlackWhiteFilterType.DENOISE -> uniffi.touch_core.ImageFilter.BlackWhite(uniffi.touch_core.BlackWhiteFilterType.DENOISE)
            BlackWhiteFilterType.INVERT -> uniffi.touch_core.ImageFilter.BlackWhite(uniffi.touch_core.BlackWhiteFilterType.INVERT)
            BlackWhiteFilterType.SKELETON -> uniffi.touch_core.ImageFilter.BlackWhite(uniffi.touch_core.BlackWhiteFilterType.SKELETON)
            BlackWhiteFilterType.DESKEW -> uniffi.touch_core.ImageFilter.BlackWhite(uniffi.touch_core.BlackWhiteFilterType.DESKEW)
            BlackWhiteFilterType.ROTATE_CORRECT -> uniffi.touch_core.ImageFilter.BlackWhite(uniffi.touch_core.BlackWhiteFilterType.ROTATE_CORRECT)
            BlackWhiteFilterType.REMOVE_LINES -> uniffi.touch_core.ImageFilter.BlackWhite(uniffi.touch_core.BlackWhiteFilterType.REMOVE_LINES)
            BlackWhiteFilterType.CONTOURS -> uniffi.touch_core.ImageFilter.BlackWhite(uniffi.touch_core.BlackWhiteFilterType.CONTOURS)
            BlackWhiteFilterType.EXTRACT_BLOBS -> uniffi.touch_core.ImageFilter.BlackWhite(uniffi.touch_core.BlackWhiteFilterType.EXTRACT_BLOBS)
            BlackWhiteFilterType.DILATE_ERODE -> uniffi.touch_core.ImageFilter.BlackWhite(uniffi.touch_core.BlackWhiteFilterType.DILATE_ERODE)
            BlackWhiteFilterType.FENCE_ADJUST -> uniffi.touch_core.ImageFilter.BlackWhite(uniffi.touch_core.BlackWhiteFilterType.FENCE_ADJUST)
            BlackWhiteFilterType.VALID_IMAGE -> uniffi.touch_core.ImageFilter.BlackWhite(uniffi.touch_core.BlackWhiteFilterType.VALID_IMAGE)
            BlackWhiteFilterType.KEEP_SIZE -> uniffi.touch_core.ImageFilter.BlackWhite(uniffi.touch_core.BlackWhiteFilterType.KEEP_SIZE)

            // --- 通用滤镜 ---
            is CommonFilterType -> when(filter) {
                CommonFilterType.SCALE_RATIO -> uniffi.touch_core.ImageFilter.Common(uniffi.touch_core.CommonFilterType.SCALE_RATIO)
                CommonFilterType.SCALE_NORM -> uniffi.touch_core.ImageFilter.Common(uniffi.touch_core.CommonFilterType.SCALE_NORM)
                CommonFilterType.FIXED_ROTATE -> uniffi.touch_core.ImageFilter.Common(uniffi.touch_core.CommonFilterType.FIXED_ROTATE)
                CommonFilterType.EXTEND_CROP -> uniffi.touch_core.ImageFilter.Common(uniffi.touch_core.CommonFilterType.EXTEND_CROP)
                CommonFilterType.FIXED_SMOOTH -> uniffi.touch_core.ImageFilter.Common(uniffi.touch_core.CommonFilterType.FIXED_SMOOTH)
                CommonFilterType.MEDIAN_BLUR -> uniffi.touch_core.ImageFilter.Common(uniffi.touch_core.CommonFilterType.MEDIAN_BLUR)
                else -> null
            }

            else -> null
        }

        if (rustFilter != null) {
            val label = filter.label
            val isBinary = filter == ColorFilterType.BINARIZATION

            // 构造用于 UI 回显的 params
            // 只有二值化需要记录 min/max/rgbAvg，以便后续点击步骤时回显到控制面板的滑块上
            val uiParams = if (isBinary) {
                mapOf("type" to "BINARIZATION", "min" to min, "max" to max, "rgbAvg" to isRgbAvgEnabled)
            } else {
                emptyMap()
            }

            // 复用 addProcessingStep，但在 processor 内部调用 Rust
            addProcessingStep(label, isBinary = isBinary, params = uiParams) { src ->
                try {
                    // 1. 图片转换：BufferedImage -> ByteArray (PNG)
                    val inputBytes = bufferedImageToBytes(src)

                    // 2. 准备参数
                    // 如果是二值化，传入 min (param1) 和 max (param2)
                    // 其他滤镜如果不需要参数，传 null
                    val p1 = if (isBinary) min else null
                    val p2 = if (isBinary) max else null

                    // 3. 调用 Rust 核心库接口
                    // applyFilter(image_data, filter, param1, param2)
                    val outputBytes = uniffi.touch_core.applyFilter(inputBytes, rustFilter, p1,p2)

                    // 4. 图片转换：ByteArray -> BufferedImage
                    bytesToBufferedImage(outputBytes)
                } catch (e: Exception) {
                    e.printStackTrace()
                    println("Rust process failed for $label: ${e.message}")
                    // 如果 Rust 处理失败，返回原图防止崩溃，或者抛出异常提示用户
                    src
                }
            }
        } else {
            println("Native filter mapping not found for: ${filter.label}")
            // 可以在这里保留旧的 Kotlin 实现作为 fallback，或者提示用户该功能未实现
        }
    }

    fun updateRules(action: (MutableList<ColorRule>) -> Unit) {
        if (currentScope == RuleScope.GLOBAL) action(globalColorRules) else if (currentSourceImage != null) {
            val newRules =
                (currentSourceImage!!.localColorRules ?: globalColorRules).map { it.copy() }
                    .toMutableList(); action(newRules);
            val newImage =
                currentSourceImage!!.copy(localColorRules = newRules); if (selectedSourceIndex in sourceImages.indices) sourceImages[selectedSourceIndex] =
                newImage
        }
    }

    fun openMappingDialog(rect: Rect) {
        if (currentWorkImage != null) {
            val charImg =
                ImageUtils.cropImage(currentWorkImage!!.bufferedImage, rect); mappingBufferedImg =
                charImg; mappingBitmap = charImg.toComposeImageBitmap(); showMappingDialog = true
        }
    }

    fun confirmMapping(char: String) {
        if (mappingBufferedImg != null) fontLibrary.add(
            FontItem(
                char,
                mappingBufferedImg!!
            )
        ); showMappingDialog = false
    }

    fun onColorPick(hex: String) {
        if (currentFilter == ColorFilterType.COLOR_PICK) {
            val targetList =
                if (currentScope == RuleScope.GLOBAL) globalColorRules else (currentSourceImage?.localColorRules
                    ?: globalColorRules); if (targetList.size < 10 && targetList.none { it.targetHex == hex }) {
                updateRules { it.add(ColorRule(targetHex = hex, biasHex = activeBias)) }
            }
        }
    }


    // 将 BufferedImage 转为 PNG 字节数组 (传给 Rust)
    private fun bufferedImageToBytes(image: BufferedImage): ByteArray {
        val stream = java.io.ByteArrayOutputStream()
        ImageIO.write(image, "png", stream)
        return stream.toByteArray()
    }

    // 将 字节数组 转回 BufferedImage (从 Rust 接收)
    private fun bytesToBufferedImage(bytes: ByteArray): BufferedImage {
        return ImageIO.read(java.io.ByteArrayInputStream(bytes))
    }
}