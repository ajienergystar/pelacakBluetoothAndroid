package com.example.mobilepelacak.ui.common

import com.example.mobilepelacak.domain.repository.TimeProvider
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Emit waktu sekarang secara periodik; dipakai untuk status "hilang sejak…" & waktu relatif. */
fun tickerFlow(periodMillis: Long, timeProvider: TimeProvider): Flow<Long> = flow {
    while (true) {
        emit(timeProvider.now())
        delay(periodMillis)
    }
}

private val indonesian: Locale = Locale.forLanguageTag("id-ID")
private val dateTimeFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm:ss", indonesian)

fun formatDateTime(epochMillis: Long): String =
    dateTimeFormatter.format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()))

/** "baru saja", "12 dtk lalu", "5 mnt lalu", "3 jam lalu", "2 hari lalu". */
fun formatRelative(now: Long, epochMillis: Long): String {
    val diffSec = ((now - epochMillis) / 1000).coerceAtLeast(0)
    return when {
        diffSec < 3 -> "baru saja"
        diffSec < 60 -> "$diffSec dtk lalu"
        diffSec < 3_600 -> "${diffSec / 60} mnt lalu"
        diffSec < 86_400 -> "${diffSec / 3_600} jam lalu"
        else -> "${diffSec / 86_400} hari lalu"
    }
}
