package org.eu.freex.bridge

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform