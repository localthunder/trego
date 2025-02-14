package com.splitter.splittr.ui.components

import android.content.Context
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.splitter.splittr.MyApplication
import com.splitter.splittr.data.model.Transaction
import com.splitter.splittr.data.repositories.InstitutionRepository
import com.splitter.splittr.ui.viewmodels.InstitutionViewModel
import com.splitter.splittr.utils.InstitutionLogoManager

@Composable
fun TransactionItem(
    transaction: Transaction,
    context: Context,
    onClick: () -> Unit = {}
) {
    val myApplication = context.applicationContext as MyApplication
    val institutionViewModel: InstitutionViewModel = viewModel(factory = myApplication.viewModelFactory)

    var logoInfo by remember(transaction.institutionId) {
        mutableStateOf<InstitutionLogoManager.LogoInfo?>(null)
    }

    LaunchedEffect(transaction.institutionId) {
        transaction.institutionId?.let { id ->
            institutionViewModel.loadInstitutionLogo(id)
        }
    }

    LaunchedEffect(transaction.institutionId) {
        institutionViewModel.logoInfo.collect { logoMap: Map<String, InstitutionLogoManager.LogoInfo> ->
            transaction.institutionId?.let { id ->
                logoInfo = logoMap[id]
            }
        }
    }

    // LogoInfo.dominantColors is already List<Color>, no need to map
    val gradientColors = if (logoInfo?.dominantColors?.size ?: 0 >= 2) {
        logoInfo?.dominantColors ?: listOf(Color.Gray, Color.LightGray)
    } else {
        listOf(Color.Gray, Color.LightGray)
    }

    PaymentAndTransactionCard(
        logoInfo = logoInfo,
        nameToShow = transaction.creditorName
            ?: transaction.debtorName
            ?: transaction.remittanceInformationUnstructured
            ?: "N/A",
        amount = transaction.getEffectiveAmount(),
        bookingDateTime = transaction.bookingDateTime,
        institutionName = transaction.institutionName ?: "N/A",
        borderSize = 2.dp,
        borderBrush = Brush.linearGradient(gradientColors),
        currency = transaction.getEffectiveCurrency(),
        onClick = onClick
    )
}