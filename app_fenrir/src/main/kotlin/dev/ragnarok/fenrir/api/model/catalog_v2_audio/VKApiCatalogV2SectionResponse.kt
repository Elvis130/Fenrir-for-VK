package dev.ragnarok.fenrir.api.model.catalog_v2_audio

import dev.ragnarok.fenrir.api.model.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class VKApiCatalogV2SectionResponse {
    @SerialName("audios")
    var audios: List<VKApiAudio>? = null

    @SerialName("playlists")
    var playlists: List<VKApiAudioPlaylist>? = null

    @SerialName("artists")
    var artists: List<VKApiCatalogV2ArtistItem>? = null

    @SerialName("links")
    var links: List<VKApiCatalogV2Link>? = null

    @SerialName("artist_videos")
    var artist_videos: List<VKApiVideo>? = null

    @SerialName("section")
    var section: VKApiCatalogV2Section? = null
}
