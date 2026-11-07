package com.goldenduck.scoring

import androidx.annotation.Keep

@Keep
enum class DismissalType(val isBowlerWicket: Boolean) {
    BOWLED(isBowlerWicket = true),
    CAUGHT(isBowlerWicket = true),
    LBW(isBowlerWicket = true),
    STUMPED(isBowlerWicket = true),
    HIT_WICKET(isBowlerWicket = true),
    HIT_OUT_OF_GROUND(isBowlerWicket = true),
    RUN_OUT(isBowlerWicket = false),
    OBSTRUCTING(isBowlerWicket = false),
    RETIRED_HURT(isBowlerWicket = false),
    RETIRED_OUT(isBowlerWicket = false),
    OTHER(isBowlerWicket = false);
}
