package dev.ragnarok.fenrir.model

import android.os.Parcel
import android.os.Parcelable
import dev.ragnarok.fenrir.readTypedObjectCompat
import dev.ragnarok.fenrir.writeTypedObjectCompat

class Link : AbsModel {
    var url: String? = null
        private set
    var title: String? = null
        private set
    var caption: String? = null
        private set
    var description: String? = null
        private set
    var photo: Photo? = null
        private set
    var previewPhoto: String? = null
        private set
    var msgId = 0
        private set
    var msgPeerId = 0
        private set

    constructor()
    internal constructor(`in`: Parcel) {
        url = `in`.readString()
        title = `in`.readString()
        caption = `in`.readString()
        description = `in`.readString()
        previewPhoto = `in`.readString()
        photo = `in`.readTypedObjectCompat(Photo.CREATOR)
        msgId = `in`.readInt()
        msgPeerId = `in`.readInt()
    }

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(url)
        parcel.writeString(title)
        parcel.writeString(caption)
        parcel.writeString(description)
        parcel.writeString(previewPhoto)
        parcel.writeTypedObjectCompat(photo, flags)
        parcel.writeInt(msgId)
        parcel.writeInt(msgPeerId)
    }

    @AbsModelType
    override fun getModelType(): Int {
        return AbsModelType.MODEL_LINK
    }

    fun setUrl(url: String?): Link {
        this.url = url
        return this
    }

    fun setTitle(title: String?): Link {
        this.title = title
        return this
    }

    fun setCaption(caption: String?): Link {
        this.caption = caption
        return this
    }

    fun setDescription(description: String?): Link {
        this.description = description
        return this
    }

    fun setPhoto(photo: Photo?): Link {
        this.photo = photo
        return this
    }

    fun setPreviewPhoto(photo: String?): Link {
        previewPhoto = photo
        return this
    }

    fun setMsgId(msgId: Int): Link {
        this.msgId = msgId
        return this
    }

    fun setMsgPeerId(msgPeerId: Int): Link {
        this.msgPeerId = msgPeerId
        return this
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<Link> {
        override fun createFromParcel(parcel: Parcel): Link {
            return Link(parcel)
        }

        override fun newArray(size: Int): Array<Link?> {
            return arrayOfNulls(size)
        }
    }
}