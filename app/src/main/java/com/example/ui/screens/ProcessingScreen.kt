package com.example.ui.screens

import android.content.Context
import android.graphics.PathMeasure
import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ClipEntity
import com.example.data.model.PipelineStep
import com.example.data.model.PipelineUiState
import com.example.ui.components.ClipForgeTopAppBar
import com.example.ui.components.SoundEffectManager
import com.example.ui.theme.DarkBg
import com.example.ui.theme.DarkSurfaceElevated
import com.example.ui.theme.DarkSurfaceHighlight
import com.example.ui.theme.ElectricViolet
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.ViralCoral
import com.example.ui.theme.ViralEmerald
import kotlin.math.roundToInt
import kotlin.random.Random

fun isReduceAnimationsEnabled(context: Context): Boolean {
    return try {
        val durationScale = Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1.0f
        )
        val transitionScale = Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.TRANSITION_ANIMATION_SCALE,
            1.0f
        )
        durationScale == 0f || transitionScale == 0f
    } catch (_: Exception) {
        false
    }
}

private fun formatRemainingTime(seconds: Long): String {
    if (seconds <= 0) return "Almost done"
    val mins = seconds / 60
    val secs = seconds % 60
    return if (mins > 0) {
        "~$mins min $secs sec remaining"
    } else {
        "~$secs sec remaining"
    }
}

enum class StepState {
    PENDING,
    ACTIVE,
    DONE,
    FAILED
}

@Composable
fun ProcessingScreen(
    projectTitle: String,
    uiState: PipelineUiState,
    projectId: Long = 0L,
    onRetry: () -> Unit = {},
    onCancel: () -> Unit = {},
    onViewClips: () -> Unit = {},
    onNavigateBack: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val reduceAnimations = remember(context) { isReduceAnimationsEnabled(context) }

    val celebrationAnim = remember { Animatable(0f) }
    LaunchedEffect(uiState.isDone) {
        if (uiState.isDone) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            SoundEffectManager.playSuccessCelebration(context)
            if (!reduceAnimations) {
                celebrationAnim.snapTo(0f)
                celebrationAnim.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 1800, easing = LinearEasing)
                )
            }
        }
    }

    val listState = rememberLazyListState()

    LaunchedEffect(uiState.currentStep) {
        val targetIdx = uiState.currentStep.ordinal.coerceIn(0, PipelineStep.entries.lastIndex)
        try {
            listState.animateScrollToItem(
                index = (targetIdx + 1).coerceAtLeast(0),
                scrollOffset = -100
            )
        } catch (_: Exception) {}
    }

    val hasError = uiState.failedStep != null

    Scaffold(
        topBar = {
            ClipForgeTopAppBar(
                title = if (hasError) "Processing Issue" else if (uiState.isDone) "Clips Ready!" else "Processing Video",
                canNavigateBack = true,
                onNavigateBack = onNavigateBack
            )
        },
        containerColor = DarkBg,
        modifier = modifier
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(top = 16.dp, bottom = 96.dp)
            ) {
                item {
                    ProcessingTopSection(
                        projectTitle = projectTitle,
                        overallProgress = uiState.overallProgressPercent,
                        activeSubtitle = if (uiState.liveSubtitle.isNotBlank()) {
                            uiState.liveSubtitle
                        } else {
                            uiState.currentStep.defaultSubtitle
                        },
                        estimatedTimeRemainingSeconds = uiState.estimatedTimeRemainingSeconds,
                        isDone = uiState.isDone,
                        hasError = hasError,
                        reduceAnimations = reduceAnimations
                    )
                }

                item {
                    Text(
                        text = "PROCESSING TIMELINE",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextMuted,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 4.dp)
                    )
                }

                items(PipelineStep.entries) { step ->
                    val isCompleted = uiState.completedSteps.contains(step) ||
                        (uiState.isDone && step == PipelineStep.DONE)
                    val isFailed = uiState.failedStep == step
                    val isActive = step == uiState.currentStep && !isCompleted && !isFailed
                    val stepState = when {
                        isFailed -> StepState.FAILED
                        isCompleted -> StepState.DONE
                        isActive -> StepState.ACTIVE
                        else -> StepState.PENDING
                    }
                    val isLast = step == PipelineStep.entries.last()

                    StepTimelineRow(
                        step = step,
                        state = stepState,
                        liveSubtitle = if (isActive && uiState.liveSubtitle.isNotBlank()) {
                            uiState.liveSubtitle
                        } else {
                            step.defaultSubtitle
                        },
                        stepProgress = if (isActive) uiState.stepProgressPercent else if (isCompleted) 100 else 0,
                        errorMessage = if (isFailed) uiState.errorMessage else null,
                        isLast = isLast,
                        reduceAnimations = reduceAnimations,
                        onRetry = onRetry
                    )
                }

                item {
                    AnimatedVisibility(
                        visible = uiState.detectedClipsPreview.isNotEmpty(),
                        enter = fadeIn(tween(350)) + expandVertically()
                    ) {
                        DetectedClipsPreviewCard(
                            clips = uiState.detectedClipsPreview,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }

                item {
                    AnimatedVisibility(
                        visible = uiState.isDone,
                        enter = fadeIn(tween(400)) +
                            scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)) +
                            expandVertically()
                    ) {
                        Button(
                            onClick = {
                                SoundEffectManager.playButtonTap()
                                onViewClips()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = NeonCyan,
                                contentColor = Color(0xFF090D16)
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("view_clips_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "View Viral Clips (${uiState.detectedClipsPreview.size.coerceAtLeast(1)})",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        }
                    }
                }

                if (!uiState.isDone && uiState.failedStep == null) {
                    item {
                        OutlinedButton(
                            onClick = {
                                SoundEffectManager.playButtonTap()
                                onCancel()
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("cancel_pipeline_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Cancel,
                                contentDescription = null,
                                tint = ViralCoral,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Cancel Processing",
                                color = ViralCoral,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            if (celebrationAnim.value in 0.001f..0.999f && !reduceAnimations) {
                ConfettiCanvas(
                    progress = celebrationAnim.value,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@Composable
fun ProcessingTopSection(
    projectTitle: String,
    overallProgress: Int,
    activeSubtitle: String,
    estimatedTimeRemainingSeconds: Long?,
    isDone: Boolean,
    hasError: Boolean,
    reduceAnimations: Boolean,
    modifier: Modifier = Modifier
) {
    val animatedProgress by animateFloatAsState(
        targetValue = overallProgress / 100f,
        animationSpec = if (reduceAnimations) snap() else tween(350, easing = FastOutSlowInEasing),
        label = "overall_progress_ring"
    )

    Card(
        colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated),
        shape = RoundedCornerShape(20.dp),
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, DarkSurfaceHighlight, RoundedCornerShape(20.dp))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(118.dp)
            ) {
                CircularProgressIndicator(
                    progress = { 1.0f },
                    modifier = Modifier.fillMaxSize(),
                    color = DarkSurfaceHighlight,
                    strokeWidth = 9.dp,
                    trackColor = Color.Transparent
                )
                CircularProgressIndicator(
                    progress = { animatedProgress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxSize(),
                    color = when {
                        hasError -> ViralCoral
                        isDone -> ViralEmerald
                        else -> NeonCyan
                    },
                    strokeWidth = 9.dp,
                    trackColor = Color.Transparent,
                    strokeCap = StrokeCap.Round
                )
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "${(animatedProgress * 100).roundToInt()}%",
                        fontSize = 26.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = TextPrimary
                    )
                    Text(
                        text = if (isDone) "READY" else if (hasError) "FAILED" else "CREATING",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        color = when {
                            hasError -> ViralCoral
                            isDone -> ViralEmerald
                            else -> NeonCyan
                        }
                    )
                }
            }

            Text(
                text = projectTitle,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )

            AnimatedContent(
                targetState = activeSubtitle,
                transitionSpec = {
                    if (reduceAnimations) {
                        fadeIn(snap()) togetherWith fadeOut(snap())
                    } else {
                        fadeIn(tween(300)) togetherWith fadeOut(tween(300))
                    }
                },
                label = "step_subtitle_crossfade"
            ) { text ->
                Text(
                    text = text,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (hasError) ViralCoral else TextPrimary,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }

            if (estimatedTimeRemainingSeconds != null && estimatedTimeRemainingSeconds > 0 && !isDone && !hasError) {
                Surface(
                    color = DarkBg.copy(alpha = 0.7f),
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, DarkSurfaceHighlight)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Timer,
                            contentDescription = null,
                            tint = NeonCyan,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = formatRemainingTime(estimatedTimeRemainingSeconds),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = NeonCyan
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun StepTimelineRow(
    step: PipelineStep,
    state: StepState,
    liveSubtitle: String,
    stepProgress: Int,
    errorMessage: String?,
    isLast: Boolean,
    reduceAnimations: Boolean,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDone = state == StepState.DONE
    val isActive = state == StepState.ACTIVE
    val isFailed = state == StepState.FAILED

    val shakeOffset = remember { Animatable(0f) }
    LaunchedEffect(isFailed) {
        if (isFailed && !reduceAnimations) {
            for (i in 0..2) {
                shakeOffset.animateTo(-8f, tween(60, easing = LinearEasing))
                shakeOffset.animateTo(8f, tween(60, easing = LinearEasing))
            }
            shakeOffset.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
        } else {
            shakeOffset.snapTo(0f)
        }
    }

    val connectorFillProgress by animateFloatAsState(
        targetValue = if (isDone) 1f else 0f,
        animationSpec = if (reduceAnimations) snap() else tween(350, easing = FastOutSlowInEasing),
        label = "connector_fill"
    )

    val thinProgressAnimated by animateFloatAsState(
        targetValue = stepProgress / 100f,
        animationSpec = if (reduceAnimations) snap() else tween(300),
        label = "thin_progress_bar"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "pulse_glow")
    val pulseScale by if (!reduceAnimations && isActive) {
        infiniteTransition.animateFloat(
            initialValue = 1.0f,
            targetValue = 1.22f,
            animationSpec = infiniteRepeatable(
                animation = tween(850, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pulse_scale"
        )
    } else {
        remember { mutableFloatStateOf(1f) }
    }

    val pulseAlpha by if (!reduceAnimations && isActive) {
        infiniteTransition.animateFloat(
            initialValue = 0.35f,
            targetValue = 0.75f,
            animationSpec = infiniteRepeatable(
                animation = tween(850, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pulse_alpha"
        )
    } else {
        remember { mutableFloatStateOf(0f) }
    }

    val iconColor by animateColorAsState(
        targetValue = when {
            isFailed -> ViralCoral
            isDone -> ViralEmerald
            isActive -> NeonCyan
            else -> TextMuted.copy(alpha = 0.5f)
        },
        animationSpec = tween(300),
        label = "icon_color"
    )

    val titleColor by animateColorAsState(
        targetValue = when {
            isFailed -> ViralCoral
            isDone || isActive -> TextPrimary
            else -> TextMuted
        },
        animationSpec = tween(300),
        label = "title_color"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .offset { IntOffset(shakeOffset.value.roundToInt(), 0) },
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(36.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(36.dp)
            ) {
                if (isActive) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .scale(pulseScale)
                            .clip(CircleShape)
                            .background(ElectricViolet.copy(alpha = pulseAlpha * 0.4f))
                            .border(1.5.dp, NeonCyan.copy(alpha = pulseAlpha), CircleShape)
                    )
                }

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                isDone -> ViralEmerald.copy(alpha = 0.18f)
                                isFailed -> ViralCoral.copy(alpha = 0.22f)
                                isActive -> ElectricViolet.copy(alpha = 0.35f)
                                else -> DarkSurfaceElevated
                            }
                        )
                        .border(
                            width = if (isActive) 1.5.dp else 1.dp,
                            color = when {
                                isDone -> ViralEmerald
                                isFailed -> ViralCoral
                                isActive -> NeonCyan
                                else -> DarkSurfaceHighlight
                            },
                            shape = CircleShape
                        )
                ) {
                    when {
                        isDone -> {
                            AnimatedDrawCheckmark(
                                reduceAnimations = reduceAnimations,
                                color = ViralEmerald
                            )
                        }
                        isFailed -> {
                            Icon(
                                imageVector = Icons.Default.ErrorOutline,
                                contentDescription = "Failed",
                                tint = ViralCoral,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        else -> {
                            Icon(
                                imageVector = step.icon,
                                contentDescription = step.title,
                                tint = iconColor,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            if (!isLast) {
                TimelineConnector(
                    fillProgress = connectorFillProgress,
                    modifier = Modifier
                        .width(3.dp)
                        .fillMaxHeight()
                        .weight(1f, fill = true)
                )
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(bottom = if (isLast) 0.dp else 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = step.title,
                    fontSize = 14.sp,
                    fontWeight = if (isActive || isFailed) FontWeight.Bold else FontWeight.Medium,
                    color = titleColor
                )
                if (isActive && stepProgress > 0) {
                    Text(
                        text = "$stepProgress%",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = NeonCyan
                    )
                }
            }

            Text(
                text = if (isFailed && !errorMessage.isNullOrBlank()) {
                    errorMessage
                } else {
                    liveSubtitle
                },
                fontSize = 12.sp,
                color = when {
                    isFailed -> ViralCoral
                    isActive -> NeonCyan
                    isDone -> TextSecondary
                    else -> TextMuted
                },
                lineHeight = 16.sp
            )

            AnimatedVisibility(
                visible = isActive,
                enter = fadeIn(tween(250)) + expandVertically(),
                exit = fadeOut(tween(200)) + shrinkVertically()
            ) {
                LinearProgressIndicator(
                    progress = { thinProgressAnimated },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = NeonCyan,
                    trackColor = DarkSurfaceHighlight
                )
            }

            AnimatedVisibility(
                visible = isFailed,
                enter = fadeIn(tween(300)) + expandVertically(),
                exit = fadeOut(tween(200)) + shrinkVertically()
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = ViralCoral.copy(alpha = 0.12f)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp)
                        .border(1.dp, ViralCoral.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Processing failed. Tap Retry to try again.",
                            fontSize = 11.sp,
                            color = Color.White,
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = {
                                SoundEffectManager.playButtonTap()
                                onRetry()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = ElectricViolet),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("retry_step_button")
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Retry", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AnimatedDrawCheckmark(
    reduceAnimations: Boolean,
    color: Color,
    modifier: Modifier = Modifier
) {
    val checkProgress = remember { Animatable(if (reduceAnimations) 1f else 0f) }
    val bounceScale by animateFloatAsState(
        targetValue = 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "checkmark_bounce"
    )

    LaunchedEffect(Unit) {
        if (!reduceAnimations) {
            checkProgress.snapTo(0f)
            checkProgress.animateTo(1f, tween(320, easing = FastOutSlowInEasing))
        }
    }

    Canvas(
        modifier = modifier
            .size(16.dp)
            .scale(bounceScale)
    ) {
        val checkPath = Path().apply {
            moveTo(size.width * 0.22f, size.height * 0.50f)
            lineTo(size.width * 0.44f, size.height * 0.72f)
            lineTo(size.width * 0.82f, size.height * 0.28f)
        }
        if (reduceAnimations || checkProgress.value >= 0.99f) {
            drawPath(
                path = checkPath,
                color = color,
                style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
        } else {
            val pm = PathMeasure()
            pm.setPath(checkPath.asAndroidPath(), false)
            val segmentAndroidPath = android.graphics.Path()
            val length = pm.length
            pm.getSegment(0f, length * checkProgress.value, segmentAndroidPath, true)
            drawPath(
                path = segmentAndroidPath.asComposePath(),
                color = color,
                style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
        }
    }
}

@Composable
fun TimelineConnector(
    fillProgress: Float,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        drawLine(
            color = DarkSurfaceHighlight,
            start = Offset(size.width / 2, 0f),
            end = Offset(size.width / 2, size.height),
            strokeWidth = size.width,
            cap = StrokeCap.Round
        )
        if (fillProgress > 0f) {
            val fillHeight = size.height * fillProgress
            drawLine(
                brush = Brush.verticalGradient(
                    colors = listOf(ViralEmerald, NeonCyan),
                    startY = 0f,
                    endY = fillHeight
                ),
                start = Offset(size.width / 2, 0f),
                end = Offset(size.width / 2, fillHeight),
                strokeWidth = size.width,
                cap = StrokeCap.Round
            )
        }
    }
}

@Composable
fun DetectedClipsPreviewCard(
    clips: List<ClipEntity>,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, NeonCyan.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = NeonCyan,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Detected Viral Clips",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }
                Surface(
                    color = NeonCyan.copy(alpha = 0.18f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "${clips.size} ready",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = NeonCyan,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(clips) { clip ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = DarkBg),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .width(170.dp)
                            .border(1.dp, DarkSurfaceHighlight, RoundedCornerShape(12.dp))
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    color = ViralCoral.copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = "★ ${clip.viralityScore}",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = ViralCoral,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                                val durationSec = ((clip.endTimeMs - clip.startTimeMs) / 1000).coerceAtLeast(1)
                                Text(
                                    text = "${durationSec}s • 9:16",
                                    fontSize = 10.sp,
                                    color = TextMuted
                                )
                            }
                            Text(
                                text = clip.title,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                lineHeight = 15.sp
                            )
                            Text(
                                text = clip.emotionalPeak,
                                fontSize = 10.sp,
                                color = NeonCyan,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

private data class ConfettiParticle(
    val initialXRatio: Float,
    val speedY: Float,
    val speedX: Float,
    val rotationSpeed: Float,
    val width: Float,
    val height: Float,
    val color: Color
)

@Composable
fun ConfettiCanvas(
    progress: Float,
    modifier: Modifier = Modifier
) {
    val particles = remember {
        val colors = listOf(
            NeonCyan,
            ElectricViolet,
            ViralEmerald,
            ViralCoral,
            Color(0xFFFFD700),
            Color(0xFFFF69B4)
        )
        val rnd = Random(42)
        List(60) {
            ConfettiParticle(
                initialXRatio = rnd.nextFloat(),
                speedY = rnd.nextFloat() * 1.4f + 0.6f,
                speedX = (rnd.nextFloat() - 0.5f) * 0.4f,
                rotationSpeed = (rnd.nextFloat() - 0.5f) * 720f,
                width = rnd.nextFloat() * 8f + 6f,
                height = rnd.nextFloat() * 14f + 8f,
                color = colors[rnd.nextInt(colors.size)]
            )
        }
    }

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        for (p in particles) {
            val y = (progress * p.speedY) * h
            val x = (p.initialXRatio * w) + (progress * p.speedX * w)
            val currentRotation = progress * p.rotationSpeed
            val alpha = (1f - progress).coerceIn(0f, 1f)

            rotate(currentRotation, pivot = Offset(x, y)) {
                drawRoundRect(
                    color = p.color.copy(alpha = alpha),
                    topLeft = Offset(x - p.width / 2, y - p.height / 2),
                    size = Size(p.width, p.height),
                    cornerRadius = CornerRadius(2f, 2f)
                )
            }
        }
    }
}
