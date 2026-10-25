package com.itermeum.app.sync.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.itermeum.app.MainActivity
import com.itermeum.app.core.common.Clock
import com.itermeum.app.core.data.NormRepository
import com.itermeum.app.core.data.WorkRepository
import com.itermeum.app.core.database.dao.OutreachDao
import com.itermeum.app.core.engine.DayScheduleEngine
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first

/** Section 9: 4×2 widget — day progress, next norm, «Ритуал» button, calls counter. */
class DayWidget : GlanceAppWidget() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Deps {
        fun norms(): NormRepository
        fun outreach(): OutreachDao
        fun clock(): Clock
    }

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val deps = EntryPointAccessors.fromApplication(context, Deps::class.java)
        val date = deps.clock().logicalToday()
        val items = deps.norms().scheduleFor(date)
        val done = deps.norms().observeDoneIds(date).first()
        val next = DayScheduleEngine.nextItem(items, deps.clock().now(), done)
        val nextTitle = next?.let { deps.norms().norm(it.normId)?.title }
        val calls = deps.outreach().countFor(date)
        provideContent { Content(items.size, items.count { it.normId in done }, nextTitle, next?.time?.toString(), calls) }
    }

    @Composable
    private fun Content(total: Int, done: Int, nextTitle: String?, nextTime: String?, calls: Int) {
        val bg = ColorProvider(Color(0xFF0E0E0E))
        val fg = ColorProvider(Color(0xFFE8E4DC))
        val dim = ColorProvider(Color(0xFF9A958C))
        val accent = ColorProvider(Color(0xFF3F6B3F))
        val ctx = androidx.glance.LocalContext.current
        Column(
            modifier = GlanceModifier.fillMaxSize().background(bg).padding(12.dp)
                .clickable(actionStartActivity(Intent(Intent.ACTION_VIEW, Uri.parse("itermeum://today")).setClass(ctx, MainActivity::class.java))),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("$done/$total", style = TextStyle(color = fg, fontSize = 28.sp, fontWeight = FontWeight.Bold))
                Spacer(GlanceModifier.width(12.dp))
                Column(GlanceModifier.defaultWeight()) {
                    Text(nextTitle ?: "Все нормы дня выполнены", style = TextStyle(color = fg, fontSize = 14.sp))
                    Text(listOfNotNull(nextTime, "Звонки: $calls").joinToString(" · "), style = TextStyle(color = dim, fontSize = 12.sp))
                }
            }
            Spacer(GlanceModifier.height(8.dp))
            Row(GlanceModifier.fillMaxWidth()) {
                repeat(maxOf(total, 1)) { i ->
                    Spacer(GlanceModifier.height(6.dp).defaultWeight().background(if (i < done) accent else ColorProvider(Color(0xFF3A3A3A))))
                    if (i < total - 1) Spacer(GlanceModifier.width(2.dp))
                }
            }
            Spacer(GlanceModifier.height(10.dp))
            Text(
                "РИТУАЛ ПЕРЕХОДА · 15 с",
                style = TextStyle(color = fg, fontSize = 12.sp, fontWeight = FontWeight.Bold),
                modifier = GlanceModifier.fillMaxWidth().background(ColorProvider(Color(0xFF222222))).padding(8.dp)
                    .clickable(actionStartActivity(Intent(Intent.ACTION_VIEW, Uri.parse("itermeum://ritual")).setClass(ctx, MainActivity::class.java))),
            )
        }
    }

    companion object {
        suspend fun refresh(context: Context) = runCatching { DayWidget().updateAll(context) }
    }
}

class DayWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = DayWidget()
}
