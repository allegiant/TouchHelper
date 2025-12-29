sealed interface FilterParams
object NoParams : FilterParams
data class BinarizationParams(
    val thresholdRange: ClosedFloatingPointRange<Float> = 0f..72f,
    val isRgbAvgEnabled: Boolean = false
) : FilterParams