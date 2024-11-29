package com.splitter.splittr.data.local.dataClasses

import com.google.gson.annotations.SerializedName

data class RequisitionRequest(
    val baseUrl: String,
    @SerializedName("institution_id") val institutionId: String,
    val reference: String,
    @SerializedName("user_language") val userLanguage: String
)