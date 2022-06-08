package dev.ragnarok.fenrir.api.adapters

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonParseException
import dev.ragnarok.fenrir.api.model.VKApiAudio
import dev.ragnarok.fenrir.nonNullNoEmpty
import java.lang.reflect.Type

class AudioDtoAdapter : AbsAdapter(), JsonDeserializer<VKApiAudio> {
    @Throws(JsonParseException::class)
    override fun deserialize(
        json: JsonElement,
        typeOfT: Type,
        context: JsonDeserializationContext
    ): VKApiAudio {
        if (!checkObject(json)) {
            throw JsonParseException("$TAG error parse object")
        }
        val dto = VKApiAudio()
        val root = json.asJsonObject
        dto.id = optInt(root, "id")
        dto.owner_id = optInt(root, "owner_id")
        dto.artist = optString(root, "artist")
        dto.title = optString(root, "title")
        dto.duration = optInt(root, "duration")
        dto.url = optString(root, "url")
        dto.lyrics_id = optInt(root, "lyrics_id")
        dto.genre_id = optInt(root, "genre_id")
        dto.access_key = optString(root, "access_key")
        dto.isHq = optBoolean(root, "is_hq")
        if (hasArray(root, "main_artists")) {
            val arr = root.getAsJsonArray("main_artists")
            val main_artists: HashMap<String, String> = HashMap(arr.size())
            for (i in arr) {
                if (!checkObject(i)) {
                    continue
                }
                val artist = i.asJsonObject
                val name = optString(artist, "name")
                val id = optString(artist, "id")
                if (id.nonNullNoEmpty() && name.nonNullNoEmpty()) {
                    main_artists[id] = name
                }
            }
            dto.main_artists = main_artists
        }
        if (hasObject(root, "album")) {
            var thmb = root.getAsJsonObject("album")
            dto.album_id = optInt(thmb, "id")
            dto.album_owner_id = optInt(thmb, "owner_id")
            dto.album_access_key = optString(thmb, "access_key")
            dto.album_title = optString(thmb, "title")
            if (hasObject(thmb, "thumb")) {
                thmb = thmb.getAsJsonObject("thumb")
                when {
                    thmb.has("photo_135") -> dto.thumb_image_little = optString(
                        thmb,
                        "photo_135"
                    )
                    thmb.has("photo_68") -> dto.thumb_image_little = optString(
                        thmb,
                        "photo_68"
                    )
                    thmb.has("photo_34") -> dto.thumb_image_little =
                        optString(thmb, "photo_34")
                }
                dto.thumb_image_very_big = optString(thmb, "photo_1200")
                if (thmb.has("photo_600")) {
                    dto.thumb_image_big = optString(thmb, "photo_600")
                    if (dto.thumb_image_very_big == null) dto.thumb_image_very_big =
                        optString(thmb, "photo_600")
                } else if (thmb.has("photo_300")) {
                    dto.thumb_image_big = optString(thmb, "photo_300")
                    if (dto.thumb_image_very_big == null) dto.thumb_image_very_big =
                        optString(thmb, "photo_300")
                }
            }
        }
        return dto
    }

    companion object {
        private val TAG = AudioDtoAdapter::class.java.simpleName
    }
}