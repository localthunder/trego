package com.helgolabs.trego.data.local.dataClasses

data class RequisitionResponseWithRedirect(
    val id: String,
    val link: String,
    val redirectUrl: String
)