package com.helgolabs.trego.data.local.dataClasses

import com.helgolabs.trego.data.model.Institution

//Used to group institution entities under a single umbrella, e.g. Personal or Business bank from same provider
data class InstitutionGroup(
    val mainName: String,
    val institutions: List<Institution>,
    val mainInstitution: Institution, // To get ID, logo, etc. from the main institution
    val isPopular: Boolean = false
)