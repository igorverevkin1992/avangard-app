package com.itermeum.app.feature.prayer

import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import com.itermeum.app.R
import com.itermeum.app.core.common.Clock
import com.itermeum.app.core.designsystem.ImCross
import com.itermeum.app.core.designsystem.ImHeader
import com.itermeum.app.core.designsystem.ImPanel
import com.itermeum.app.core.designsystem.ImSectionLabel
import com.itermeum.app.core.designsystem.ImSpacing
import com.itermeum.app.core.designsystem.onAccent
import com.itermeum.app.core.liturgy.LiturgicalCalendar
import com.itermeum.app.core.liturgy.Season
import com.itermeum.app.core.liturgy.YearOverview
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import javax.inject.Inject
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

@HiltViewModel
class LiturgicalYearViewModel @Inject constructor(calendar: LiturgicalCalendar, clock: Clock) : ViewModel() {
    val today: LocalDate = clock.logicalToday()
    val year: YearOverview = calendar.overview(today)
}

@Composable
private fun Season.label(): String = stringResource(
    when (this) {
        Season.ADVENT -> R.string.season_advent
        Season.CHRISTMAS -> R.string.season_christmas
        Season.ORDINARY_1 -> R.string.season_ordinary_1
        Season.LENT -> R.string.season_lent
        Season.TRIDUUM -> R.string.season_triduum
        Season.EASTER -> R.string.season_easter
        Season.ORDINARY_2 -> R.string.season_ordinary_2
    },
)

/**
 * D-54: the liturgical year as a wheel, like the poster in the parish hall —
 * the seasons in their colours around the circle from Advent at the top, the
 * solemnities as dots on the rim, the months inside, and a gold needle at today.
 */
@Composable
fun LiturgicalYearScreen(onBack: () -> Unit, viewModel: LiturgicalYearViewModel = hiltViewModel()) {
    val y = viewModel.year
    val today = viewModel.today
    val dateFmt = DateTimeFormatter.ofPattern("d MMMM", Locale("ru"))
    val current = y.seasons.firstOrNull { !today.isBefore(it.start) && today.isBefore(it.endExclusive) }
    val seasonLabels = y.seasons.associate { it.season to it.season.label() }
    val wheelLabel = stringResource(R.string.year_wheel_a11y, y.litYear)

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(ImSpacing.screen), verticalArrangement = Arrangement.spacedBy(ImSpacing.grid)) {
        ImHeader(stringResource(R.string.year_title), onBack = onBack)
        Text(
            stringResource(R.string.year_subtitle, y.start.year, y.endExclusive.minusDays(1).year, y.cycle.name),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Box(Modifier.fillMaxWidth().aspectRatio(1f).semantics { contentDescription = wheelLabel }, contentAlignment = Alignment.Center) {
            YearWheel(y, today, seasonLabels)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                ImCross(28.dp)
                Spacer(Modifier.size(ImSpacing.tight))
                Text(stringResource(R.string.year_cycle, y.cycle.name), style = MaterialTheme.typography.labelLarge)
                current?.let { Text(seasonLabels.getValue(it.season), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }

        ImSectionLabel(stringResource(R.string.year_seasons))
        y.seasons.forEach { s ->
            val now = s == current
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(14.dp).background(s.color.accent()))
                Spacer(Modifier.width(ImSpacing.grid))
                Column(Modifier.weight(1f)) {
                    Text(seasonLabels.getValue(s.season), style = MaterialTheme.typography.bodyLarge, color = if (now) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                    Text("${s.start.format(dateFmt)} — ${s.endExclusive.minusDays(1).format(dateFmt)}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (now) Text(stringResource(R.string.year_now), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
        }

        ImSectionLabel(stringResource(R.string.year_solemnities))
        ImPanel {
            y.feasts.forEach { f ->
                val past = f.date.isBefore(today)
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                    Box(Modifier.size(8.dp).background(f.color.accent()))
                    Spacer(Modifier.width(ImSpacing.grid))
                    Text(f.date.format(dateFmt), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(96.dp))
                    Text(f.title, style = MaterialTheme.typography.bodyMedium, color = if (past) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}

@Composable
private fun YearWheel(y: YearOverview, today: LocalDate, seasonLabels: Map<Season, String>) {
    val gold = MaterialTheme.colorScheme.tertiary
    val hair = MaterialTheme.colorScheme.outlineVariant
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val months = stringArrayResource(R.array.months_short)
    Canvas(Modifier.fillMaxSize()) {
        val total = ChronoUnit.DAYS.between(y.start, y.endExclusive).toFloat()
        fun angle(d: LocalDate) = -90f + 360f * ChronoUnit.DAYS.between(y.start, d) / total
        val outer = min(size.width, size.height) / 2f - 10.dp.toPx()
        val ring = outer * 0.24f
        val mid = outer - ring / 2f
        val c = Offset(size.width / 2f, size.height / 2f)
        val arcBox = Size(mid * 2, mid * 2)
        val arcTopLeft = Offset(c.x - mid, c.y - mid)

        // Seasons: one coloured arc each, a hair of gap between them.
        y.seasons.forEach { s ->
            val a0 = angle(s.start)
            val sweep = angle(s.endExclusive) - a0
            drawArc(s.color.accent(), a0 + 0.4f, (sweep - 0.8f).coerceAtLeast(0.5f), useCenter = false, topLeft = arcTopLeft, size = arcBox, style = Stroke(ring))
        }
        // Gold hairlines on both rims of the ring.
        drawCircle(gold, radius = outer, center = c, style = Stroke(1.dp.toPx()))
        drawCircle(gold, radius = outer - ring, center = c, style = Stroke(1.dp.toPx()))

        drawIntoCanvas { canvas ->
            val native = canvas.nativeCanvas
            // Season names along the ring, where the arc is long enough to hold them.
            val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = ring * 0.30f; typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD); letterSpacing = 0.08f; textAlign = Paint.Align.CENTER
            }
            val oval = RectF(c.x - mid, c.y - mid, c.x + mid, c.y + mid)
            y.seasons.forEach { s ->
                val a0 = angle(s.start)
                val sweep = angle(s.endExclusive) - a0
                val text = seasonLabels.getValue(s.season).uppercase()
                val arcLen = (Math.PI * mid * sweep / 180f).toFloat()
                if (label.measureText(text) > arcLen * 0.9f) return@forEach
                label.color = onAccent(s.color.accent()).toArgb()
                val center = a0 + sweep / 2f
                // Text on the lower half reads left to right too: run that path the other way.
                val bottom = center in 0f..180f
                val path = Path().apply { if (bottom) addArc(oval, a0 + sweep, -sweep) else addArc(oval, a0, sweep) }
                native.drawTextOnPath(text, path, 0f, label.textSize * 0.35f, label)
            }
            // Months inside the ring.
            val monthPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = ring * 0.24f; color = muted.toArgb(); textAlign = Paint.Align.CENTER }
            val inner = outer - ring - ring * 0.32f
            var m = y.start.withDayOfMonth(1).plusMonths(1)
            while (m.isBefore(y.endExclusive)) {
                val a = Math.toRadians(angle(m).toDouble())
                val tickIn = outer - ring
                drawLine(hair, Offset(c.x + (tickIn - 8f) * cos(a).toFloat(), c.y + (tickIn - 8f) * sin(a).toFloat()), Offset(c.x + tickIn * cos(a).toFloat(), c.y + tickIn * sin(a).toFloat()), strokeWidth = 1.5f)
                val mm = m.plusDays(14)
                val am = Math.toRadians(angle(mm).toDouble())
                native.drawText(months[m.monthValue - 1], c.x + inner * cos(am).toFloat(), c.y + inner * sin(am).toFloat() + monthPaint.textSize * 0.35f, monthPaint)
                m = m.plusMonths(1)
            }
        }

        // Solemnities: dots on the outer rim.
        y.feasts.forEach { f ->
            val a = Math.toRadians(angle(f.date).toDouble())
            val r = outer + 4.dp.toPx()
            drawCircle(f.color.accent(), radius = 3.dp.toPx(), center = Offset(c.x + r * cos(a).toFloat(), c.y + r * sin(a).toFloat()))
            drawCircle(gold, radius = 3.dp.toPx(), center = Offset(c.x + r * cos(a).toFloat(), c.y + r * sin(a).toFloat()), style = Stroke(1f))
        }

        // Today: a gold needle across the ring with a knob outside.
        if (!today.isBefore(y.start) && today.isBefore(y.endExclusive)) {
            val a = Math.toRadians((angle(today) + 180f / total).toDouble())
            val from = outer - ring - 6.dp.toPx()
            val to = outer + 6.dp.toPx()
            drawLine(gold, Offset(c.x + from * cos(a).toFloat(), c.y + from * sin(a).toFloat()), Offset(c.x + to * cos(a).toFloat(), c.y + to * sin(a).toFloat()), strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
            drawCircle(gold, radius = 5.dp.toPx(), center = Offset(c.x + (to + 4.dp.toPx()) * cos(a).toFloat(), c.y + (to + 4.dp.toPx()) * sin(a).toFloat()))
        }
    }
}
