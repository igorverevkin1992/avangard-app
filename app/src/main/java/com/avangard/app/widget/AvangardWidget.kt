package com.avangard.app.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Box
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
import com.avangard.app.MainActivity
import com.avangard.app.R
import com.avangard.app.core.common.toStartOfDayEpoch
import com.avangard.app.core.domain.model.DailyReport
import dagger.hilt.android.EntryPointAccessors

class AvangardWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            AvangardWidgetEntryPoint::class.java,
        )
        val clock = entryPoint.clock()
        val today = clock.today().toStartOfDayEpoch(clock.zone())
        val report = entryPoint.reportRepository().findForDate(today)
        provideContent {
            GlanceTheme { WidgetContent(context, report) }
        }
    }
}

@Composable
private fun WidgetContent(context: Context, report: DailyReport?) {
    val ivory = ColorProvider(WidgetColors.Ivory)
    val warmGray = ColorProvider(WidgetColors.WarmGray)
    val copper = ColorProvider(WidgetColors.ReardenCopper)
    val cyan = ColorProvider(WidgetColors.BlueprintCyan)
    val red = ColorProvider(WidgetColors.AtlasRed)

    val stage = report.toStage()

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ColorProvider(WidgetColors.Anthracite))
            .padding(12.dp)
            .clickable(actionStartActivity<MainActivity>()),
    ) {
        Text(
            text = context.getString(R.string.widget_artifact_label),
            style = TextStyle(color = warmGray, fontSize = 11.sp, fontWeight = FontWeight.Medium),
        )
        Spacer(GlanceModifier.height(4.dp))
        Text(
            text = report?.targetArtifact?.takeIf { it.isNotBlank() }
                ?: context.getString(R.string.widget_state_pending),
            style = TextStyle(
                color = if (report?.targetArtifact.isNullOrBlank()) red else ivory,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            ),
            maxLines = 2,
        )
        Spacer(GlanceModifier.height(8.dp))
        Row(modifier = GlanceModifier.fillMaxWidth().height(6.dp)) {
            Segment(
                color = if (stage == Stage.Pending) red else copper,
            )
            Spacer(GlanceModifier.width(2.dp))
            Segment(
                color = when (stage) {
                    Stage.Pending -> warmGray
                    Stage.InProgress -> cyan
                    Stage.Completed, Stage.Failed -> copper
                },
            )
            Spacer(GlanceModifier.width(2.dp))
            Segment(
                color = when (stage) {
                    Stage.Completed -> copper
                    Stage.Failed -> red
                    else -> warmGray
                },
            )
        }
        Spacer(GlanceModifier.height(6.dp))
        Text(
            text = context.getString(report.toStateLabel()),
            style = TextStyle(
                color = when (stage) {
                    Stage.Pending -> red
                    Stage.InProgress -> cyan
                    Stage.Completed -> copper
                    Stage.Failed -> red
                },
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
            ),
        )
    }
}

@Composable
private fun Segment(color: androidx.glance.unit.ColorProvider) {
    Box(
        modifier = GlanceModifier
            .width(SEGMENT_WIDTH.dp)
            .height(6.dp)
            .background(color),
    ) {}
}

private const val SEGMENT_WIDTH = 56

private enum class Stage { Pending, InProgress, Completed, Failed }

private fun DailyReport?.toStage(): Stage = when {
    this == null -> Stage.Pending
    isCompleted -> Stage.Completed
    failureCause != null -> Stage.Failed
    else -> Stage.InProgress
}

private fun DailyReport?.toStateLabel(): Int = when (this.toStage()) {
    Stage.Pending -> R.string.widget_state_pending
    Stage.InProgress -> R.string.widget_state_in_progress
    Stage.Completed -> R.string.widget_state_completed
    Stage.Failed -> R.string.widget_state_failed
}
