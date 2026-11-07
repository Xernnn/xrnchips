package com.xern.xrnchips.data

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import com.xern.xrnchips.R

enum class Sfx(val resource: Int) {
    CHECK(R.raw.check),
    CHIPS(R.raw.chips),
    FOLD(R.raw.fold),
    DEAL(R.raw.deal),
    CARD(R.raw.card),
    WIN(R.raw.win),
    APPLAUSE(R.raw.applause),
    LEVEL(R.raw.level),
    TURN(R.raw.turn),
}

/** The short effects, loaded once and kept ready. A sound asked for before it has loaded is skipped. */
class Sounds(context: Context) {
    private val pool = SoundPool.Builder()
        .setMaxStreams(4)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()
    private val ids = Sfx.entries.associateWith { pool.load(context, it.resource, 1) }

    fun play(sfx: Sfx) {
        pool.play(ids.getValue(sfx), 1f, 1f, 1, 0, 1f)
    }
}
