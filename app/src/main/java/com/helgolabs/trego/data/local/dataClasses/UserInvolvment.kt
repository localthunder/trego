package com.helgolabs.trego.data.local.dataClasses

// Sealed class to represent the different ways a user can be involved in a payment
sealed class UserInvolvement {
    // SPENT scenarios
    data class YouPaidAndAreOwed(val amount: Double) : UserInvolvement() // The user spent money and others owe them
    data class SomeoneElsePaidAndYouOwe(val amount: Double) : UserInvolvement() // Another user paid and you owe them

    // RECEIVED scenarios
    data class YouReceivedAndAreSharing(val amount: Double) : UserInvolvement() // You received money and are sharing it
    data class SomeoneElseReceivedAndIsSharing(val amount: Double) : UserInvolvement() // Another received money and is sharing with you

    // TRANSFERRED scenarios
    data class YouSentMoney(val amount: Double) : UserInvolvement() // You transferred money to another user
    data class YouReceivedMoney(val amount: Double) : UserInvolvement() // Another user transferred money to you

    // Common scenario for all types
    object NotInvolved : UserInvolvement() // You're not involved in this payment
}