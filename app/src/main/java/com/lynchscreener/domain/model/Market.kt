package com.lynchscreener.domain.model

/** Exchange universe. */
enum class Market(val code: String) {
    BR("BR"),
    US("US");

    companion object {
        fun fromCode(code: String): Market = entries.firstOrNull { it.code == code } ?: BR
    }
}
