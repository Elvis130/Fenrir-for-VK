package dev.ragnarok.fenrir.db.serialize

import dev.ragnarok.fenrir.model.Photo
import dev.ragnarok.fenrir.util.serializeble.msgpack.MsgPack

object Serializers {
    val PHOTOS_SERIALIZER: ISerializeAdapter<Photo> = object : ISerializeAdapter<Photo> {
        override fun deserialize(raw: ByteArray): Photo {
            return MsgPack.decodeFromByteArray(Photo.serializer(), raw)
        }

        override fun serialize(data: Photo): ByteArray {
            return MsgPack.encodeToByteArray(Photo.serializer(), data)
        }
    }
}