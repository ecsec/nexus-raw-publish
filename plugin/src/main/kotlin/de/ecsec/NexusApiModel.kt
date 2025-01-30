package de.ecsec

import kotlinx.serialization.Serializable

@Serializable
data class AssetSearchResult(
    val items: List<Asset>,
    val continuationToken: String?
)

@Serializable
data class Asset(
    val id: String,
    val downloadUrl: String,
    val path: String,
    val repository: String,
    val format: String,
    val contentType: String,
)
