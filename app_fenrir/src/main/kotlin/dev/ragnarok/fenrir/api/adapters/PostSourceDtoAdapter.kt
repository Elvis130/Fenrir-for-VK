package dev.ragnarok.fenrir.api.adapters

import dev.ragnarok.fenrir.api.model.VKApiPostSource
import dev.ragnarok.fenrir.util.serializeble.json.JsonElement

class PostSourceDtoAdapter : AbsAdapter<VKApiPostSource>("VKApiPostSource") {
    @Throws(Exception::class)
    override fun deserialize(
        json: JsonElement
    ): VKApiPostSource {
        val root = json.asJsonObject
        val dto = VKApiPostSource()
        dto.type = VKApiPostSource.Type.parse(optString(root, "type"))
        dto.platform = optString(root, "platform")
        dto.data = VKApiPostSource.Data.parse(optString(root, "data"))
        dto.url = optString(root, "url")
        return dto
    }
}