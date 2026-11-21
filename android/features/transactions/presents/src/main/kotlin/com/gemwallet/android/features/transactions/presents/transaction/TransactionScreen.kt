package com.gemwallet.android.features.transactions.presents.transaction

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gemwallet.android.ext.errorText
import com.gemwallet.android.features.transactions.viewmodels.TransactionViewModel
import com.gemwallet.android.features.transactions.viewmodels.chain
import com.gemwallet.android.ui.R
import com.gemwallet.android.ui.components.screen.FatalStateScene
import com.gemwallet.android.ui.localization.string
import com.gemwallet.android.ui.localization.text
import com.gemwallet.android.ui.shareText
import uniffi.gemstone.GemLoadState

@Composable
fun TransactionScreen(onAction: (TransactionAction.Navigation) -> Unit, viewModel: TransactionViewModel = hiltViewModel()) {
    val transaction by viewModel.details.collectAsStateWithLifecycle()
    val loadState by viewModel.loadState.collectAsStateWithLifecycle()
    var isShowFeeDetails by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current

    fun onShare(url: String, name: String) {
        context.shareText(subject = null, text = url, chooserTitle = name)
    }

    val model = transaction
    if (model == null) {
        when (val state = loadState) {
            is GemLoadState.Error -> {
                FatalStateScene(
                    title = stringResource(R.string.errors_error_occurred),
                    message = state.error.errorText().text(context),
                    onCancel = { onAction(TransactionAction.Close) },
                    onTryAgain = viewModel::load,
                )
                return
            }

            GemLoadState.Loading, GemLoadState.Data, GemLoadState.NoData -> Unit
        }
    }

    TransactionScene(
        title = model?.rows?.title?.string(context).orEmpty(),
        sections = model?.sections.orEmpty(),
        headerAction = model?.rows?.headerAction,
        chain = model?.rows?.chain(),
        isLoading = model == null,
        onAction = {
            when (it) {
                TransactionAction.Share -> model?.let { current -> onShare(current.rows.explorer.link, current.rows.explorer.name) }
                TransactionAction.ShowFeeDetails -> isShowFeeDetails = true
                is TransactionAction.Navigation -> onAction(it)
            }
        },
    )

    model?.let { current ->
        FeeDetailsDialog(
            isVisible = isShowFeeDetails,
            feeRow = current.rows.feeRow,
        ) { isShowFeeDetails = false }
    }
}
