package com.helgolabs.trego.data.local.dataClasses

// Sealed class to represent the different ways a user can be involved in a payment
sealed class UserInvolvement {
    data class Borrowed(val amount: Double) : UserInvolvement()
    data class Lent(val amount: Double) : UserInvolvement()
    data class Paid(val amount: Double) : UserInvolvement()
    object NotInvolved : UserInvolvement()
}