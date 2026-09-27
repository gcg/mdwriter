package dev.mdwriter.ui.find

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.mdwriter.R
import dev.mdwriter.editor.EditorController
import dev.mdwriter.editor.FindResult
import dev.mdwriter.ui.theme.WriterDimens
import dev.mdwriter.ui.theme.WriterMotion
import dev.mdwriter.ui.theme.WriterTheme
import dev.mdwriter.ui.theme.hairline
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The find & replace bar (02 §9/§11, T17): a flat `surface` strip docked at the top of the editor, with a 1 px
 * bottom `divider` border. Stateless (recording-lambda testable, `FindBarTest`) — [FindBarHost] owns the state and
 * the [dev.mdwriter.editor.EditorController] wiring; this composable never touches the controller.
 */
@Composable
fun FindBar(
    query: TextFieldValue,
    onQueryChange: (TextFieldValue) -> Unit,
    matchCase: Boolean,
    onMatchCase: (Boolean) -> Unit,
    result: FindResult,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onClose: () -> Unit,
    readOnly: Boolean,
    replaceOpen: Boolean,
    onReplaceToggle: () -> Unit,
    replacement: TextFieldValue,
    onReplacementChange: (TextFieldValue) -> Unit,
    onReplaceOne: () -> Unit,
    onReplaceAll: () -> Unit,
    queryFocusRequester: FocusRequester,
    modifier: Modifier = Modifier,
    replacedMessage: String? = null,
) {
    val colors = WriterTheme.colors
    val replaceAllDescription = stringResource(R.string.find_replace_all_description)
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = colors.surface,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                ),
        ) {
            // ---- Row 1 (56 dp): search icon, query field, counter, previous/next/close -----------------------------
            Row(
                Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_search),
                    contentDescription = null,
                    tint = colors.textSecondary,
                    modifier = Modifier.size(20.dp).padding(start = 4.dp),
                )
                Box(
                    Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp)
                        .height(40.dp)
                        .background(colors.surfaceHover, RoundedCornerShape(WriterDimens.searchFieldCornerRadius))
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (query.text.isEmpty()) {
                        Text(
                            text = stringResource(R.string.find_hint),
                            color = colors.textSecondary,
                            style = WriterTheme.typography.rowTitle,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = onQueryChange,
                        singleLine = true,
                        textStyle = WriterTheme.typography.rowTitle.copy(color = colors.text),
                        cursorBrush = SolidColor(colors.accent),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search, autoCorrectEnabled = false),
                        keyboardActions = KeyboardActions(onSearch = { onNext() }),
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .testTag("find.query")
                                .focusRequester(queryFocusRequester)
                                .onPreviewKeyEvent { e ->
                                    if (e.type != KeyEventType.KeyDown) {
                                        false
                                    } else {
                                        when (e.key) {
                                            Key.Enter, Key.NumPadEnter -> {
                                                if (e.isShiftPressed) onPrevious() else onNext()
                                                true
                                            }

                                            Key.Escape -> {
                                                onClose()
                                                true
                                            }

                                            else -> {
                                                false
                                            }
                                        }
                                    }
                                },
                    )
                }
                if (replacedMessage != null) {
                    Text(text = replacedMessage, color = colors.textSecondary, style = WriterTheme.typography.caption)
                } else if (query.text.isNotEmpty()) {
                    val truncatedMark = if (result.truncated) "+" else ""
                    Text(
                        text = "${result.index + 1} / ${result.count}$truncatedMark",
                        color = colors.textSecondary,
                        style = WriterTheme.typography.caption,
                        modifier =
                            Modifier
                                .testTag("find.counter")
                                .semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
                IconButton(onClick = onPrevious, enabled = result.count > 0) {
                    Icon(
                        painter = painterResource(R.drawable.ic_expand_less),
                        contentDescription = stringResource(R.string.find_previous),
                        tint = if (result.count > 0) colors.text else colors.textSecondary,
                    )
                }
                IconButton(onClick = onNext, enabled = result.count > 0) {
                    Icon(
                        painter = painterResource(R.drawable.ic_expand_more),
                        contentDescription = stringResource(R.string.find_next),
                        tint = if (result.count > 0) colors.text else colors.textSecondary,
                    )
                }
                IconButton(onClick = onClose) {
                    Icon(
                        painter = painterResource(R.drawable.ic_close),
                        contentDescription = stringResource(R.string.find_close),
                        tint = colors.text,
                    )
                }
            }
            // ---- Row 2 (48 dp): match case / replace toggle, and (when open) the replace fields --------------------
            Row(
                Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FindToggle(
                    checked = matchCase,
                    onCheckedChange = onMatchCase,
                    iconRes = R.drawable.ic_match_case,
                    contentDescription = stringResource(R.string.find_match_case),
                )
                if (!readOnly) {
                    FindToggle(
                        checked = replaceOpen,
                        onCheckedChange = { onReplaceToggle() },
                        iconRes = R.drawable.ic_find_replace,
                        contentDescription = stringResource(R.string.find_replace_toggle),
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
                if (replaceOpen && !readOnly) {
                    Box(
                        Modifier
                            .weight(1f)
                            .padding(start = 8.dp, end = 4.dp)
                            .height(36.dp)
                            .background(colors.surfaceHover, RoundedCornerShape(WriterDimens.searchFieldCornerRadius))
                            .padding(horizontal = 10.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        if (replacement.text.isEmpty()) {
                            Text(
                                text = stringResource(R.string.replace_hint),
                                color = colors.textSecondary,
                                style = WriterTheme.typography.rowTitle,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        BasicTextField(
                            value = replacement,
                            onValueChange = onReplacementChange,
                            singleLine = true,
                            textStyle = WriterTheme.typography.rowTitle.copy(color = colors.text),
                            cursorBrush = SolidColor(colors.accent),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done, autoCorrectEnabled = false),
                            keyboardActions = KeyboardActions(onDone = { onReplaceOne() }),
                            modifier = Modifier.fillMaxWidth().testTag("find.replace"),
                        )
                    }
                    FindTextButton(
                        text = stringResource(R.string.find_replace_one),
                        enabled = result.count > 0,
                        onClick = onReplaceOne,
                    )
                    FindTextButton(
                        text = stringResource(R.string.find_replace_all),
                        enabled = result.count > 0,
                        onClick = onReplaceAll,
                        modifier = Modifier.semantics { contentDescription = replaceAllDescription },
                    )
                } else {
                    Box(Modifier.weight(1f))
                }
            }
            // 1 px bottom border (02 §11 "flat, no elevation, 1 px divider bottom border").
            Box(Modifier.fillMaxWidth().height(hairline()).background(colors.divider))
        }
    }
}

/** "Replace" / "All" (02 §11 "Row 2"): a compact text button — never Material3's `TextButton`, whose enforced
 * 58 dp `minWidth` leaves too little room for the replacement field at 360 dp (found by `FindBarTest`'s own
 * narrow-width case). Disabled (`count == 0`) dims to `textSecondary`. */
@Composable
private fun FindTextButton(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = WriterTheme.colors
    Text(
        text = text,
        color = if (enabled) colors.accent else colors.textSecondary,
        style = WriterTheme.typography.rowTitle,
        modifier =
            modifier
                .clip(RoundedCornerShape(WriterDimens.searchFieldCornerRadius))
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                .padding(horizontal = 10.dp, vertical = 6.dp),
    )
}

/** A checkbox-role icon toggle (match case / replace): checked = `text` tint on a `surfaceHover` circle, unchecked
 * = `textSecondary` (02 §11 "Row 2"). */
@Composable
private fun FindToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    iconRes: Int,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    val colors = WriterTheme.colors
    Box(
        modifier
            .size(WriterDimens.touchTarget)
            .clip(CircleShape)
            .then(if (checked) Modifier.background(colors.surfaceHover, CircleShape) else Modifier)
            .clickable(role = Role.Checkbox) { onCheckedChange(!checked) }
            .semantics { toggleableState = if (checked) ToggleableState.On else ToggleableState.Off },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = contentDescription,
            tint = if (checked) colors.text else colors.textSecondary,
        )
    }
}

/**
 * The controller wiring (T17 step 5/6): owns the query/match-case/replace-open/replacement state
 * (`rememberSaveable`, so it survives rotation), the 150 ms search debounce, and the "N replaced" 2 s message.
 * Never composed twice — one instance lives for [dev.mdwriter.ui.editor.EditorScreen]'s whole lifetime so the
 * query text/case/replace-panel state persists across opening and closing the bar; [visible] only drives the
 * [AnimatedVisibility] around the actual [FindBar] content.
 */
@Composable
fun FindBarHost(
    visible: Boolean,
    controller: EditorController,
    focusToken: Int,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue()) }
    var matchCase by rememberSaveable { mutableStateOf(false) }
    var replaceOpen by rememberSaveable { mutableStateOf(false) }
    var replacement by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue()) }
    var replacedMessage by remember { mutableStateOf<String?>(null) }
    var editVersion by remember { mutableIntStateOf(0) }
    val queryFocusRequester = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    val replacedFormat = stringResource(R.string.find_replaced)
    val result by controller.findResult.collectAsStateWithLifecycle()

    LaunchedEffect(controller) { controller.edits.collect { editVersion++ } }

    // The debounce (task step 5): a key change (query/matchCase/an edit) cancels the previous delayed run.
    LaunchedEffect(query.text, matchCase, editVersion, visible) {
        if (!visible) return@LaunchedEffect
        delay(FIND_DEBOUNCE_MS)
        controller.find(query.text, matchCase)
    }

    // Pre-fills from the current selection, focuses the query field and selects all of it — on every open AND
    // every subsequent Ctrl+F while already open (task step 6: "the token change re-focuses the field and selects
    // all").
    LaunchedEffect(focusToken) {
        if (!visible) return@LaunchedEffect
        val selected = controller.selectedTextForFind()
        val text = selected ?: query.text
        query = TextFieldValue(text, TextRange(0, text.length))
        runCatching { queryFocusRequester.requestFocus() }
    }

    // Safety net (task §D): guarantees the highlights are cleared even if `onClose` was bypassed somehow (e.g. the
    // whole editor screen tearing down while find was open).
    DisposableEffect(controller) { onDispose { controller.clearFind() } }

    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = expandVertically(tween(WriterMotion.PILL_IN_MS)) + fadeIn(tween(WriterMotion.PILL_IN_MS)),
        exit = shrinkVertically(tween(WriterMotion.PILL_OUT_MS)) + fadeOut(tween(WriterMotion.PILL_OUT_MS)),
    ) {
        FindBar(
            query = query,
            onQueryChange = { query = it },
            matchCase = matchCase,
            onMatchCase = { matchCase = it },
            result = result,
            onNext = controller::findNext,
            onPrevious = controller::findPrevious,
            onClose = onClose,
            readOnly = controller.isReadOnly,
            replaceOpen = replaceOpen,
            onReplaceToggle = { replaceOpen = !replaceOpen },
            replacement = replacement,
            onReplacementChange = { replacement = it },
            onReplaceOne = { controller.replaceCurrent(replacement.text) },
            onReplaceAll = {
                scope.launch {
                    val n = controller.replaceAll(replacement.text)
                    if (n > 0) {
                        replacedMessage = replacedFormat.format(n)
                        delay(FIND_REPLACED_MESSAGE_MS)
                        replacedMessage = null
                    }
                }
            },
            queryFocusRequester = queryFocusRequester,
            replacedMessage = replacedMessage,
        )
    }
}

private const val FIND_DEBOUNCE_MS = 150L
private const val FIND_REPLACED_MESSAGE_MS = 2_000L
