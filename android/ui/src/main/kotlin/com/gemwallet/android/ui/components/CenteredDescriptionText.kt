package com.gemwallet.android.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import com.gemwallet.android.ui.theme.adaptivePadding
import com.gemwallet.android.ui.theme.paddingSmall
import com.gemwallet.android.ui.theme.space0

private val descriptionLineHeightStyle = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None,
)

@Composable
fun CenteredDescriptionText(text: String, modifier: Modifier = Modifier, minLines: Int = 1) {
    val horizontalPadding = adaptivePadding(default = paddingSmall, compact = space0)

    Text(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPadding),
        text = text,
        style = LocalTextStyle.current.copy(lineHeightStyle = descriptionLineHeightStyle),
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.secondary,
        minLines = minLines,
    )
}
