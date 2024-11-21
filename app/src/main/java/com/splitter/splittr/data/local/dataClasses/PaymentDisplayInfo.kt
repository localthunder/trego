package com.splitter.splittr.data.local.dataClasses

import com.splitter.splittr.data.model.Transaction
import com.splitter.splittr.utils.InstitutionLogoManager

data class PaymentDisplayInfo(
    val logoInfo: InstitutionLogoManager.LogoInfo?,
    val paidByUsername: String?,
    val transaction: Transaction?
)