package com.androidreverse.ide.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.androidreverse.ide.R
import com.androidreverse.ide.core.binary.CallEdgeKind
import com.androidreverse.ide.core.binary.CallGraphData
import com.androidreverse.ide.core.binary.CfgEdgeType
import com.androidreverse.ide.core.binary.CfgGraph
import com.androidreverse.ide.ui.theme.JetBrainsMonoFamily
import com.androidreverse.ide.ui.theme.StatusError
import com.androidreverse.ide.ui.theme.StatusGood

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CfgScreen(
    graph: CfgGraph?,
    onBack: () -> Unit
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset(40f, 40f)) }

    val textMeasurer = rememberTextMeasurer()
    val cardFill = MaterialTheme.colorScheme.surface
    val cardHeaderFill = MaterialTheme.colorScheme.primaryContainer
    val cardBorder = MaterialTheme.colorScheme.outline
    val headerTextColor = MaterialTheme.colorScheme.onPrimaryContainer
    val bodyTextColor = MaterialTheme.colorScheme.onSurface
    val primaryEdge = MaterialTheme.colorScheme.primary

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("cfg_back_button")) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
                title = {
                    Text(
                        text = graph?.methodName ?: stringResource(R.string.tab_cfg),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleMedium
                    )
                },
                actions = {
                    IconButton(
                        onClick = {
                            scale = 1f
                            pan = Offset(40f, 40f)
                        },
                        modifier = Modifier.testTag("cfg_reset_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CenterFocusStrong,
                            contentDescription = stringResource(R.string.cd_reset_view)
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        if (graph == null) return@Scaffold

        val blockW = 540f
        val lineH = 38f
        val headerH = 56f
        val colGap = 110f
        val rowGap = 110f

        val rects = remember(graph) {
            val map = HashMap<Int, Pair<Offset, Size>>()
            val cols = if (graph.blocks.size > 2) 2 else 1
            val colHeights = FloatArray(cols) { 40f }
            graph.blocks.forEachIndexed { idx, block ->
                val col = if (idx == 0) 0 else idx % cols
                val h = headerH + (block.lines.take(10).size.coerceAtLeast(1) * lineH) + 28f
                val x = if (idx == 0 && cols == 2) (blockW + colGap) / 2f else col * (blockW + colGap)
                val y = if (idx == 0) 40f else colHeights[col]
                map[block.id] = Offset(x, y) to Size(blockW, h)
                if (idx == 0) {
                    val nextY = y + h + rowGap
                    for (c in 0 until cols) colHeights[c] = nextY
                } else {
                    colHeights[col] = y + h + rowGap
                }
            }
            map
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
                .pointerInput(Unit) {
                    detectTransformGestures { _, drag, zoom, _ ->
                        scale = (scale * zoom).coerceIn(0.35f, 2.2f)
                        pan += drag
                    }
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                withTransform({
                    translate(pan.x, pan.y)
                    scale(scale, scale, pivot = Offset.Zero)
                }) {
                    // Curved Bézier branch edges
                    for (edge in graph.edges) {
                        val from = rects[edge.fromBlockId] ?: continue
                        val to = rects[edge.toBlockId] ?: continue
                        val start = Offset(from.first.x + from.second.width / 2f, from.first.y + from.second.height)
                        val end = Offset(to.first.x + to.second.width / 2f, to.first.y)
                        val color = when (edge.type) {
                            CfgEdgeType.TRUE_BRANCH, CfgEdgeType.FALLTHROUGH -> StatusGood
                            CfgEdgeType.FALSE_BRANCH -> StatusError
                            CfgEdgeType.JUMP -> primaryEdge
                        }
                        val midY = (start.y + end.y) / 2f
                        val path = Path().apply {
                            moveTo(start.x, start.y)
                            cubicTo(start.x, midY, end.x, midY, end.x, end.y)
                        }
                        drawPath(path, color = color, style = Stroke(width = 5f))
                        drawCircle(color = color, radius = 8f, center = end)
                    }

                    // Basic block cards
                    for (block in graph.blocks) {
                        val (pos, size) = rects[block.id] ?: continue
                        drawRoundRect(
                            color = cardFill,
                            topLeft = pos,
                            size = size,
                            cornerRadius = CornerRadius(20f, 20f)
                        )
                        drawRoundRect(
                            color = cardHeaderFill,
                            topLeft = pos,
                            size = Size(size.width, headerH),
                            cornerRadius = CornerRadius(20f, 20f)
                        )
                        drawRoundRect(
                            color = cardBorder,
                            topLeft = pos,
                            size = size,
                            cornerRadius = CornerRadius(20f, 20f),
                            style = Stroke(width = 2.5f)
                        )

                        val headerText = "0x${block.startOffset.toString(16).padStart(4, '0')}"
                        val headerLayout = textMeasurer.measure(
                            text = headerText,
                            style = TextStyle(
                                fontFamily = JetBrainsMonoFamily,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = headerTextColor
                            )
                        )
                        drawText(
                            textLayoutResult = headerLayout,
                            topLeft = Offset(pos.x + 20f, pos.y + 14f)
                        )

                        val codeText = block.lines.take(10).joinToString("\n")
                        val bodyLayout = textMeasurer.measure(
                            text = codeText,
                            style = TextStyle(
                                fontFamily = JetBrainsMonoFamily,
                                fontSize = 12.sp,
                                lineHeight = 18.sp,
                                color = bodyTextColor
                            ),
                            constraints = Constraints(maxWidth = (size.width - 36f).toInt()),
                            overflow = TextOverflow.Ellipsis
                        )
                        drawText(
                            textLayoutResult = bodyLayout,
                            topLeft = Offset(pos.x + 18f, pos.y + headerH + 12f)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallGraphScreen(
    graph: CallGraphData?,
    depth: Int,
    onDepthDelta: (Int) -> Unit,
    onBack: () -> Unit
) {
    var scale by remember { mutableFloatStateOf(0.85f) }
    var pan by remember { mutableStateOf(Offset(40f, 80f)) }

    val textMeasurer = rememberTextMeasurer()
    val cardFill = MaterialTheme.colorScheme.surface
    val cardBorder = MaterialTheme.colorScheme.outline
    val titleColor = MaterialTheme.colorScheme.onSurface
    val subColor = MaterialTheme.colorScheme.onSurfaceVariant
    val callEdgeColor = MaterialTheme.colorScheme.primary

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("callgraph_back_button")) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
                title = {
                    Text(
                        text = graph?.targetTitle ?: stringResource(R.string.tab_calls),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleMedium
                    )
                },
                actions = {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { onDepthDelta(-1) },
                                modifier = Modifier.size(40.dp).testTag("depth_minus_button")
                            ) {
                                Icon(Icons.Rounded.Remove, contentDescription = stringResource(R.string.cd_decrease))
                            }
                            Text(
                                text = depth.toString(),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            IconButton(
                                onClick = { onDepthDelta(1) },
                                modifier = Modifier.size(40.dp).testTag("depth_plus_button")
                            ) {
                                Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.cd_increase))
                            }
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        if (graph == null) return@Scaffold

        val nodeW = 360f
        val nodeH = 128f
        val colStep = 470f
        val rowStep = 165f

        val positions = remember(graph) {
            val map = HashMap<String, Offset>()
            val byDepth = graph.nodes.groupBy { it.depth }
            val maxDepth = (byDepth.keys.maxOrNull() ?: 0)
            for ((d, nodes) in byDepth) {
                val colX = (maxDepth - d) * colStep + 40f
                nodes.forEachIndexed { idx, node ->
                    map[node.id] = Offset(colX, idx * rowStep + 40f)
                }
            }
            map
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
                .pointerInput(Unit) {
                    detectTransformGestures { _, drag, zoom, _ ->
                        scale = (scale * zoom).coerceIn(0.35f, 2.2f)
                        pan += drag
                    }
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                withTransform({
                    translate(pan.x, pan.y)
                    scale(scale, scale, pivot = Offset.Zero)
                }) {
                    for (edge in graph.edges) {
                        val from = positions[edge.fromId] ?: continue
                        val to = positions[edge.toId] ?: continue
                        val start = Offset(from.x + nodeW, from.y + nodeH / 2f)
                        val end = Offset(to.x, to.y + nodeH / 2f)
                        val color = when (edge.kind) {
                            CallEdgeKind.READ -> StatusGood
                            CallEdgeKind.WRITE -> StatusError
                            CallEdgeKind.CALL -> callEdgeColor
                        }
                        val midX = (start.x + end.x) / 2f
                        val path = Path().apply {
                            moveTo(start.x, start.y)
                            cubicTo(midX, start.y, midX, end.y, end.x, end.y)
                        }
                        drawPath(path, color = color, style = Stroke(width = 4.5f))
                    }

                    for (node in graph.nodes) {
                        val pos = positions[node.id] ?: continue
                        drawRoundRect(
                            color = cardFill,
                            topLeft = pos,
                            size = Size(nodeW, nodeH),
                            cornerRadius = CornerRadius(20f, 20f)
                        )
                        drawRoundRect(
                            color = if (node.depth == 0) callEdgeColor else cardBorder,
                            topLeft = pos,
                            size = Size(nodeW, nodeH),
                            cornerRadius = CornerRadius(20f, 20f),
                            style = Stroke(width = if (node.depth == 0) 4f else 2f)
                        )

                        val titleRes = textMeasurer.measure(
                            text = node.title,
                            style = TextStyle(
                                fontFamily = JetBrainsMonoFamily,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = titleColor
                            ),
                            constraints = Constraints(maxWidth = (nodeW - 32f).toInt()),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val subRes = textMeasurer.measure(
                            text = "${node.subtitle}\n${node.detail}",
                            style = TextStyle(
                                fontFamily = JetBrainsMonoFamily,
                                fontSize = 11.sp,
                                lineHeight = 16.sp,
                                color = subColor
                            ),
                            constraints = Constraints(maxWidth = (nodeW - 32f).toInt()),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        drawText(titleRes, topLeft = Offset(pos.x + 16f, pos.y + 18f))
                        drawText(subRes, topLeft = Offset(pos.x + 16f, pos.y + 54f))
                    }
                }
            }

            // Bottom floating legend pill
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                shadowElevation = 4.dp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LegendDot(StatusGood, stringResource(R.string.legend_reads))
                    LegendDot(StatusError, stringResource(R.string.legend_writes))
                    LegendDot(callEdgeColor, stringResource(R.string.legend_calls))
                }
            }
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
