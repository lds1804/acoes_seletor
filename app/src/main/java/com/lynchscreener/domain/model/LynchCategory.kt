package com.lynchscreener.domain.model

/** Peter Lynch classification of a stock. */
enum class LynchCategory(val label: String) {
    FAST_GROWER("Fast Grower"),
    STALWART("Stalwart"),
    SLOW_GROWER("Slow Grower"),
    ASSET_PLAY("Asset Play"),
    CYCLICAL("Cyclical"),
    TURNAROUND("Turnaround"),
    VALUE_TRAP("Value Trap"),
    UNRATED("Sem classificação");

    companion object {
        fun fromLabel(label: String?): LynchCategory =
            entries.firstOrNull { it.label == label } ?: UNRATED
    }
}
