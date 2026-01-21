package org.eu.freex.tools.modules.image.domain.mapper

import org.eu.freex.tools.modules.image.domain.model.AutoCropFilter
import org.eu.freex.tools.modules.image.domain.model.AutoCropMode
import org.eu.freex.tools.modules.image.domain.model.BinarizationFilter
import org.eu.freex.tools.modules.image.domain.model.BinarizationMode
import org.eu.freex.tools.modules.image.domain.model.BlackWhiteInvertFilter
import org.eu.freex.tools.modules.image.domain.model.DenoiseFilter
import org.eu.freex.tools.modules.image.domain.model.DeskewFilter
import org.eu.freex.tools.modules.image.domain.model.ExtendCropFilter
import org.eu.freex.tools.modules.image.domain.model.ExtractBlobsFilter
import org.eu.freex.tools.modules.image.domain.model.ExtractContoursFilter
import org.eu.freex.tools.modules.image.domain.model.GrayscaleFilter
import org.eu.freex.tools.modules.image.domain.model.GrayscaleMode
import org.eu.freex.tools.modules.image.domain.model.ImageFilter
import org.eu.freex.tools.modules.image.domain.model.MorphologyFilter
import org.eu.freex.tools.modules.image.domain.model.MorphologyMode
import org.eu.freex.tools.modules.image.domain.model.MultiColorFilter
import org.eu.freex.tools.modules.image.domain.model.PosterizationFilter
import org.eu.freex.tools.modules.image.domain.model.PosterizationMode
import org.eu.freex.tools.modules.image.domain.model.RemoveLinesFilter
import org.eu.freex.tools.modules.image.domain.model.RemoveNoiseFilter
import org.eu.freex.tools.modules.image.domain.model.ResizeScaleFilter
import org.eu.freex.tools.modules.image.domain.model.RotationFilter
import org.eu.freex.tools.modules.image.domain.model.SmartLayoutFilter
import org.eu.freex.tools.modules.image.domain.model.ViewFilter
import uniffi.touch_core.ColorRule
import uniffi.touch_core.ImageFilterWrapper
import uniffi.touch_core.InvertMode

fun ImageFilter.toRust(): ImageFilterWrapper {
    return when (this) {
        is BinarizationFilter -> {
            val mode = when (this.mode) {
                BinarizationMode.MANUAL -> uniffi.touch_core.BinarizationMode.MANUAL
                BinarizationMode.ADAPTIVE -> uniffi.touch_core.BinarizationMode.ADAPTIVE
                BinarizationMode.OTSU -> uniffi.touch_core.BinarizationMode.OTSU
            }
            val f = uniffi.touch_core.BinarizationFilter(
                mode,
                min.toInt(),
                max.toInt(),
                isRgbAvg,
                sauvolaK.toDouble(),
                windowSize.toInt()
            )
            ImageFilterWrapper.Binarization(f)
        }

        is GrayscaleFilter -> {
            val mode = when (this.mode) {
                GrayscaleMode.WEIGHTED -> uniffi.touch_core.GrayscaleMode.WEIGHTED
                GrayscaleMode.MAX -> uniffi.touch_core.GrayscaleMode.MAX
                GrayscaleMode.MIN -> uniffi.touch_core.GrayscaleMode.MIN
                GrayscaleMode.RED -> uniffi.touch_core.GrayscaleMode.RED
                GrayscaleMode.GREEN -> uniffi.touch_core.GrayscaleMode.GREEN
                GrayscaleMode.BLUE -> uniffi.touch_core.GrayscaleMode.BLUE
            }
            ImageFilterWrapper.Grayscale(uniffi.touch_core.GrayscaleFilter(mode))
        }

        is PosterizationFilter -> {
            val mode = when (this.mode) {
                PosterizationMode.RGB -> uniffi.touch_core.PosterizationMode.RGB
                PosterizationMode.HSV -> uniffi.touch_core.PosterizationMode.HSV
            }
            val f = uniffi.touch_core.PosterizationFilter(
                mode, isMultiValue, level, channel1, channel2, channel3
            )
            ImageFilterWrapper.Posterization(f)
        }

        is MultiColorFilter -> {
            val rustRules = this.rules.map { rule ->
                ColorRule(rule.id, rule.targetHex, rule.biasHex, rule.isEnabled)
            }
            val f = uniffi.touch_core.MultiColorFilter(rustRules, isInvert, keepOriginal)
            ImageFilterWrapper.MultiColor(f)
        }
        // [修复] 补全 RemoveNoiseFilter
        is RemoveNoiseFilter -> {
            val f = uniffi.touch_core.RemoveNoiseFilter(minArea, gap, removeWhite)
            ImageFilterWrapper.RemoveNoise(f)
        }

        is RemoveLinesFilter -> {
            val f = uniffi.touch_core.RemoveLinesFilter(minLength, removeHorizontal, removeVertical)
            ImageFilterWrapper.RemoveLines(f)
        }

        is ExtractContoursFilter -> {
            val f = uniffi.touch_core.ExtractContoursFilter(
                isCanny, cannyLow, cannyHigh, morphKernel.toUByte()
            )
            ImageFilterWrapper.ExtractContours(f)
        }

        is ExtractBlobsFilter -> {
            val f = uniffi.touch_core.ExtractBlobsFilter(
                minWidth.toUInt(), maxWidth.toUInt(), minHeight.toUInt(), maxHeight.toUInt(),
                minArea.toUInt(), maxArea.toUInt(), useEightConnectivity
            )
            ImageFilterWrapper.ExtractBlobs(f)
        }

        is DeskewFilter -> {
            val f = uniffi.touch_core.DeskewFilter(angle, isAuto, bgColor.toUByte())
            ImageFilterWrapper.Deskew(f)
        }

        is RotationFilter -> {
            val f = uniffi.touch_core.RotationFilter(
                isAuto, angle.toDouble(), maxSearchRange.toDouble(), precision.toDouble()
            )
            ImageFilterWrapper.Rotation(f)
        }

        is BlackWhiteInvertFilter -> {
            val rustMode = when (mode) {
                0 -> InvertMode.AUTO_TO_WHITE_BG
                1 -> InvertMode.AUTO_TO_BLACK_BG
                else -> InvertMode.FORCE
            }
            val f = uniffi.touch_core.BlackWhiteInvertFilter(rustMode)
            ImageFilterWrapper.BlackWhiteInvert(f)
        }

        is MorphologyFilter -> {
            val mode = when (this.mode) {
                MorphologyMode.DILATE -> uniffi.touch_core.MorphologyMode.DILATE
                MorphologyMode.ERODE -> uniffi.touch_core.MorphologyMode.ERODE
                MorphologyMode.OPEN -> uniffi.touch_core.MorphologyMode.OPEN
                MorphologyMode.CLOSE -> uniffi.touch_core.MorphologyMode.CLOSE
                MorphologyMode.GRADIENT -> uniffi.touch_core.MorphologyMode.GRADIENT
            }
            val f = uniffi.touch_core.MorphologyFilter(mode, kernelSize, iterations)
            ImageFilterWrapper.Morphology(f)
        }

        is SmartLayoutFilter -> {
            val f = uniffi.touch_core.SmartLayoutFilter(padding, minWidth, minHeight, fixedHeight, alignCenter)
            ImageFilterWrapper.SmartLayout(f)
        }

        is AutoCropFilter -> {
            val mode = when (this.mode) {
                AutoCropMode.AUTO_CORNERS -> uniffi.touch_core.AutoCropMode.AUTO_CORNERS
                AutoCropMode.FIXED_COLOR -> uniffi.touch_core.AutoCropMode.FIXED_COLOR
            }
            val f = uniffi.touch_core.AutoCropFilter(mode, tolerance, padding, noiseThreshold, fixedColorHex)
            ImageFilterWrapper.AutoCrop(f)
        }

        is ResizeScaleFilter -> {
            val f = uniffi.touch_core.ResizeScaleFilter(scaleFactor, highQuality)
            ImageFilterWrapper.ResizeScale(f)
        }

        is ExtendCropFilter -> {
            val f = uniffi.touch_core.ExtendCropFilter(x1, y1, x2, y2)
            ImageFilterWrapper.ExtendCrop(f)
        }

        is DenoiseFilter -> {
            val f = uniffi.touch_core.DenoiseFilter(radius)
            ImageFilterWrapper.Denoise(f)
        }

        is ViewFilter -> throw IllegalArgumentException("ViewFilter cannot be applied in Rust pipeline")
    }
}