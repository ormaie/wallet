package com.gemwallet.android.features.transfer.presents.receive

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gemwallet.android.domains.asset.chain
import com.gemwallet.android.ext.toIdentifier
import com.gemwallet.android.features.transfer.presents.receive.components.rememberQRCodePainter
import com.gemwallet.android.features.transfer.viewmodels.receive.ReceiveViewModel
import com.gemwallet.android.ui.R
import com.gemwallet.android.ui.components.buttons.MainActionButton
import com.gemwallet.android.ui.components.buttons.mainActionButtonColors
import com.gemwallet.android.ui.components.clipboard.rememberClipboard
import com.gemwallet.android.ui.components.image.AssetIcon
import com.gemwallet.android.ui.components.image.ListItemImageView
import com.gemwallet.android.ui.components.list_item.ListItemImage
import com.gemwallet.android.ui.components.parseMarkdownToAnnotatedString
import com.gemwallet.android.ui.components.screen.LoadingScene
import com.gemwallet.android.ui.components.screen.Scene
import com.gemwallet.android.ui.icons.AppIcons
import com.gemwallet.android.ui.localization.string
import com.gemwallet.android.ui.shareText
import com.gemwallet.android.ui.theme.SceneSizing
import com.gemwallet.android.ui.theme.WindowDimension
import com.gemwallet.android.ui.theme.compactIconSize
import com.gemwallet.android.ui.theme.isCompactDimension
import com.gemwallet.android.ui.theme.paddingDefault
import com.gemwallet.android.ui.theme.paddingHalfSmall
import com.gemwallet.android.ui.theme.paddingMiddle
import com.gemwallet.android.ui.theme.paddingSmall
import com.gemwallet.android.ui.theme.pendingColor
import com.gemwallet.android.ui.theme.sceneContentPadding
import com.gemwallet.android.ui.theme.smallIconSize
import com.gemwallet.android.ui.theme.space10
import com.gemwallet.android.ui.theme.space2
import com.gemwallet.android.ui.theme.space4
import com.gemwallet.android.ui.theme.space6
import com.gemwallet.android.ui.theme.space8
import com.wallet.core.primitives.AssetData
import com.wallet.core.primitives.AssetId
import kotlinx.coroutines.delay
import uniffi.gemstone.GemAssetText
import uniffi.gemstone.GemChainRow
import uniffi.gemstone.GemCopy
import uniffi.gemstone.chainRow

private val qrSize = 280.dp
private val qrSizeCompact = 208.dp
private val qrCardCornerRadius = 18.dp
private val qrCardBottomPadding = 22.dp
private val qrCardItemSpacing = 18.dp
private val qrInnerCornerRadius = 14.dp
private val assetIconSize = 40.dp
private val headerMinHeight = 44.dp
private val networkPillHeight = 36.dp
private val networkPillMaxWidth = 200.dp
private val networkPillArrowSize = 18.dp
private val networkDotSize = 3.dp
private val warningIconSize = 1.35.em
private val warningIconSlotWidth = 1.75.em
private const val WARNING_ICON_ID = "receive_warning_icon"
private const val ADDRESS_HIGHLIGHT_EDGE_COUNT = 6
private const val SINGLE_LINE_ADDRESS_MAX_LENGTH = 16
private const val LONG_ADDRESS_LENGTH = 56
private const val WARNING_EMPHASIS_ALPHA = 0.75f
private const val COPY_FEEDBACK_DURATION_MS = 1_500L
private const val COPY_FEEDBACK_ANIMATION_MS = 180
private const val COPY_ICON_INITIAL_SCALE = 0.6f

@Composable
fun ReceiveScreen(assetId: AssetId, closeIcon: Boolean = false, onCancel: () -> Unit) {
    val viewModel = hiltViewModel<ReceiveViewModel, ReceiveViewModel.Factory>(
        key = assetId.toIdentifier(),
    ) { it.create(assetId) }
    val assetInfo by viewModel.asset.collectAsStateWithLifecycle()
    val networks by viewModel.networks.collectAsStateWithLifecycle()
    var isShowingNetworkSelector by rememberSaveable { mutableStateOf(false) }
    val info = assetInfo

    if (info != null) {
        LaunchedEffect(info.asset.id) {
            viewModel.setVisible()
        }
        val assetState = remember(info.asset) { viewModel.assetState(info.asset) }
        val warnings = remember(assetState) { viewModel.warnings(assetState) }
        val copyText = remember(info.asset.id, info.account.address) { viewModel.copyAddress(info) }
        val chainRow = remember(networks.networks, info.asset.id) {
            networks.networks.firstOrNull { it.assetId == info.asset.id.toIdentifier() }?.row ?: chainRow(info.asset.id.chain.string)
        }
        ReceiveScene(
            closeIcon = closeIcon,
            assetInfo = info,
            header = assetState.asset,
            warnings = warnings,
            shareText = viewModel.shareAddress(info),
            copyText = copyText,
            chainRow = chainRow,
            onSelectNetwork = if (networks.showsSelector) {
                { isShowingNetworkSelector = true }
            } else {
                null
            },
            onCancel = onCancel,
        )
        ReceiveNetworkSelector(
            isVisible = isShowingNetworkSelector,
            networks = networks.networks,
            onSelect = viewModel::selectAsset,
            onDismiss = { isShowingNetworkSelector = false },
        )
    } else {
        LoadingScene(title = stringResource(R.string.wallet_receive), onCancel)
    }
}

@Composable
internal fun ReceiveScene(
    closeIcon: Boolean,
    assetInfo: AssetData,
    header: GemAssetText,
    warnings: List<String>,
    shareText: String?,
    copyText: GemCopy?,
    chainRow: GemChainRow,
    onSelectNetwork: (() -> Unit)?,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    val clipboard = rememberClipboard()
    val shareTitle = stringResource(R.string.common_share)
    val isCompactHeight = isCompactDimension(WindowDimension.Height)
    var copyTrigger by remember(copyText) { mutableIntStateOf(0) }
    var isCopied by remember(copyText) { mutableStateOf(false) }

    LaunchedEffect(copyText, copyTrigger) {
        if (copyTrigger > 0) {
            delay(COPY_FEEDBACK_DURATION_MS)
            isCopied = false
        }
    }

    val onShare = fun () {
        context.shareText(subject = null, text = shareText, chooserTitle = shareTitle)
    }

    val onCopyClick = fun () {
        copyText?.let {
            clipboard.copy(it)
            isCopied = true
            copyTrigger++
        }
    }

    Scene(
        title = stringResource(R.string.wallet_receive),
        onClose = onCancel,
        closeIcon = closeIcon,
        actions = {
            IconButton(onShare) {
                Icon(AppIcons.Share, shareTitle)
            }
        },
        mainAction = {
            ReceiveCopyButton(
                isCopied = isCopied,
                onClick = onCopyClick,
            )
        },
    ) {
        if (assetInfo.account.address.isEmpty()) {
            return@Scene
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(
                    horizontal = sceneContentPadding(),
                    vertical = if (isCompactHeight) paddingSmall else paddingDefault,
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = SceneSizing.contentMaxWidth)
                    .fillMaxWidth(),
            ) {
                ReceiveCard(
                    header = header,
                    chainRow = chainRow,
                    address = assetInfo.account.address,
                    isCompactHeight = isCompactHeight,
                    onSelectNetwork = onSelectNetwork,
                    onCopyClick = onCopyClick,
                )
                if (warnings.isNotEmpty()) {
                    Spacer(modifier = Modifier.size(paddingMiddle))
                    ReceiveWarnings(warnings = warnings)
                }
            }
        }
    }
}

@Composable
private fun ReceiveCopyButton(isCopied: Boolean, onClick: () -> Unit) {
    val containerColor by animateColorAsState(
        targetValue = if (isCopied) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
        animationSpec = tween(COPY_FEEDBACK_ANIMATION_MS),
        label = "receive_copy_container",
    )
    val contentColor by animateColorAsState(
        targetValue = if (isCopied) MaterialTheme.colorScheme.onTertiary else MaterialTheme.colorScheme.onPrimary,
        animationSpec = tween(COPY_FEEDBACK_ANIMATION_MS),
        label = "receive_copy_content",
    )

    MainActionButton(
        colors = mainActionButtonColors(
            containerColor = containerColor,
            contentColor = contentColor,
        ),
        onClick = onClick,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(space6),
        ) {
            AnimatedContent(
                targetState = isCopied,
                transitionSpec = {
                    (fadeIn(tween(COPY_FEEDBACK_ANIMATION_MS)) + scaleIn(tween(COPY_FEEDBACK_ANIMATION_MS), initialScale = COPY_ICON_INITIAL_SCALE))
                        .togetherWith(fadeOut(tween(COPY_FEEDBACK_ANIMATION_MS)) + scaleOut(tween(COPY_FEEDBACK_ANIMATION_MS), targetScale = COPY_ICON_INITIAL_SCALE))
                },
                label = "receive_copy_icon",
            ) { copied ->
                Icon(
                    imageVector = if (copied) AppIcons.Check else AppIcons.ContentCopyOutlined,
                    contentDescription = null,
                    modifier = Modifier.size(compactIconSize),
                )
            }
            Text(
                text = stringResource(
                    if (isCopied) R.string.wallet_copied else R.string.wallet_copy_address,
                ),
            )
        }
    }
}

@Composable
private fun ReceiveCard(
    header: GemAssetText,
    chainRow: GemChainRow,
    address: String,
    isCompactHeight: Boolean,
    onSelectNetwork: (() -> Unit)?,
    onCopyClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(qrCardCornerRadius))
            .background(MaterialTheme.colorScheme.background)
            .padding(
                start = paddingDefault,
                top = paddingDefault,
                end = paddingDefault,
                bottom = if (isCompactHeight) paddingDefault else qrCardBottomPadding,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(if (isCompactHeight) paddingMiddle else qrCardItemSpacing),
    ) {
        ReceiveCardHeader(
            header = header,
            chainRow = chainRow,
            onSelectNetwork = onSelectNetwork,
        )
        ReceiveQrCode(
            address = address,
            imageSize = if (isCompactHeight) qrSizeCompact else qrSize,
            onCopyClick = onCopyClick,
        )
        ReceiveAddress(
            address = address,
            onCopyClick = onCopyClick,
        )
    }
}

@Composable
private fun ReceiveCardHeader(header: GemAssetText, chainRow: GemChainRow, onSelectNetwork: (() -> Unit)?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = headerMinHeight),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(paddingMiddle),
    ) {
        AssetIcon(
            icon = header.icon,
            size = assetIconSize,
            badgeBackgroundColor = MaterialTheme.colorScheme.background,
        )
        Text(
            text = header.asset.symbol,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        ReceiveNetworkPill(
            chainRow = chainRow,
            onSelectNetwork = onSelectNetwork,
        )
    }
}

@Composable
private fun ReceiveNetworkPill(chainRow: GemChainRow, onSelectNetwork: (() -> Unit)?) {
    val context = LocalContext.current
    val standard = chainRow.standard?.string(context)?.takeIf { it.isNotBlank() }

    Row(
        modifier = Modifier
            .widthIn(max = networkPillMaxWidth)
            .heightIn(min = networkPillHeight)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface)
            .let { base ->
                onSelectNetwork?.let {
                    base.clickable(
                        role = Role.Button,
                        onClickLabel = stringResource(R.string.transfer_network),
                        onClick = it,
                    )
                } ?: base
            }
            .padding(
                start = space6,
                top = space6,
                end = if (onSelectNetwork != null) space8 else space10,
                bottom = space6,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(space6),
    ) {
        ListItemImageView(
            image = ListItemImage.Asset(chainRow.icon),
            size = smallIconSize,
        )
        Text(
            text = chainRow.title,
            modifier = Modifier.weight(1f, fill = false),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        standard?.let { label ->
            Box(
                modifier = Modifier
                    .size(networkDotSize)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondary),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
        }
        if (onSelectNetwork != null) {
            Icon(
                imageVector = AppIcons.KeyboardArrowDown,
                contentDescription = null,
                modifier = Modifier.size(networkPillArrowSize),
                tint = MaterialTheme.colorScheme.secondary,
            )
        }
    }
}

@Composable
private fun ReceiveQrCode(address: String, imageSize: Dp, onCopyClick: () -> Unit) {
    Box(
        modifier = Modifier
            .widthIn(max = imageSize)
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(qrInnerCornerRadius))
            .background(Color.White)
            .clickable(onClick = onCopyClick)
            .padding(space6),
        contentAlignment = Alignment.Center,
    ) {
        rememberQRCodePainter(
            content = address,
            size = imageSize,
        )?.let { painter ->
            Image(
                modifier = Modifier.fillMaxSize(),
                painter = painter,
                contentDescription = stringResource(R.string.wallet_copy_address),
                contentScale = ContentScale.FillBounds,
            )
        }
    }
}

@Composable
private fun ReceiveAddress(address: String, onCopyClick: () -> Unit) {
    val highlightColor = MaterialTheme.colorScheme.onSurface
    val mutedColor = MaterialTheme.colorScheme.secondary
    val formattedAddress = remember(address, highlightColor, mutedColor) {
        formatReceiveAddress(address, highlightColor, mutedColor)
    }
    val textStyle = if (address.length > LONG_ADDRESS_LENGTH) {
        MaterialTheme.typography.bodySmall
    } else {
        MaterialTheme.typography.bodyMedium
    }.copy(fontFamily = FontFamily.Monospace)

    Text(
        text = formattedAddress,
        modifier = Modifier
            .clip(RoundedCornerShape(space10))
            .clickable(onClick = onCopyClick)
            .testTag(address)
            .padding(horizontal = paddingSmall, vertical = space2),
        textAlign = TextAlign.Center,
        style = textStyle,
    )
}

@Composable
private fun ReceiveWarnings(warnings: List<String>) {
    val emphasisColor = MaterialTheme.colorScheme.onSurface.copy(alpha = WARNING_EMPHASIS_ALPHA)
    val primaryWarning = warnings.first()
    val secondaryWarnings = warnings.drop(1)
    val text = remember(primaryWarning, emphasisColor) {
        formatReceiveWarning(primaryWarning, emphasisColor)
    }
    val inlineContent = remember {
        mapOf(
            WARNING_ICON_ID to InlineTextContent(
                placeholder = Placeholder(
                    width = warningIconSlotWidth,
                    height = warningIconSize,
                    placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter,
                ),
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Icon(
                        imageVector = AppIcons.Warning,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxHeight()
                            .aspectRatio(1f),
                        tint = pendingColor,
                    )
                }
            },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = paddingHalfSmall),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(space4),
    ) {
        Text(
            modifier = Modifier.fillMaxWidth(),
            text = text,
            inlineContent = inlineContent,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.secondary,
            style = MaterialTheme.typography.bodySmall,
        )
        secondaryWarnings.forEach { note ->
            Text(
                modifier = Modifier.fillMaxWidth(),
                text = note,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.secondary,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

private fun formatReceiveWarning(warning: String, emphasisColor: Color): AnnotatedString {
    val parsed = parseMarkdownToAnnotatedString(warning)
    return buildAnnotatedString {
        appendInlineContent(WARNING_ICON_ID)
        val textOffset = length
        append(parsed.text)
        parsed.spanStyles.forEach { range ->
            val style = if (range.item.fontWeight == FontWeight.Bold) {
                range.item.copy(color = emphasisColor, fontWeight = FontWeight.Medium)
            } else {
                range.item
            }
            addStyle(style, textOffset + range.start, textOffset + range.end)
        }
    }
}

private fun formatReceiveAddress(address: String, highlightColor: Color, mutedColor: Color): AnnotatedString {
    if (address.isEmpty()) return AnnotatedString("")
    val edgeCount = minOf(ADDRESS_HIGHLIGHT_EDGE_COUNT, address.length / 3)
    val highlightStyle = SpanStyle(color = highlightColor, fontWeight = FontWeight.SemiBold)
    val mutedStyle = SpanStyle(color = mutedColor, fontWeight = FontWeight.Normal)

    return buildAnnotatedString {
        if (edgeCount == 0) {
            withStyle(highlightStyle) { append(address) }
            return@buildAnnotatedString
        }
        val tailStart = address.length - edgeCount
        withStyle(highlightStyle) { append(address.substring(0, edgeCount)) }
        withStyle(mutedStyle) {
            if (address.length <= SINGLE_LINE_ADDRESS_MAX_LENGTH) {
                append(address.substring(edgeCount, tailStart))
            } else {
                val split = (address.length + 1) / 2
                append(address.substring(edgeCount, split))
                append('\n')
                append(address.substring(split, tailStart))
            }
        }
        withStyle(highlightStyle) { append(address.substring(tailStart)) }
    }
}
