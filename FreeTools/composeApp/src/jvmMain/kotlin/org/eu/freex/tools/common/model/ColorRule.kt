package org.eu.freex.tools.common.model

import kotlinx.serialization.Serializable


@Serializable
data class ColorRule(
    val id: Long,
    val x: Int? = -1,
    val y: Int? = -1,
    val targetHex: String,
    val biasHex: String,
    val isEnabled: Boolean
)