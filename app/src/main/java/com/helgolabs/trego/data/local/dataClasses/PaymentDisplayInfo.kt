package com.helgolabs.trego.data.local.dataClasses

import com.helgolabs.trego.data.model.Transaction
import com.helgolabs.trego.utils.InstitutionLogoManager

data class PaymentDisplayInfo(
    val logoInfo: InstitutionLogoManager.LogoInfo?,
    val paidByUsername: String?,
    val transaction: Transaction?
)