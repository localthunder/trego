package com.helgolabs.trego.data.local.dataClasses

import com.google.gson.annotations.SerializedName

data class RequisitionRequest(
    val baseUrl: String,
    @SerializedName("institution_id") val institutionId: String,
    @SerializedName("user_language") val userLanguage: String,
    @SerializedName("return_route") val returnRoute: String,
    )