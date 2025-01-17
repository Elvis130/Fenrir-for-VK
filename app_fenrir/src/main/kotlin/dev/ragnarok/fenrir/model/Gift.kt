package dev.ragnarok.fenrir.model

import android.os.Parcel
import android.os.Parcelable
import dev.ragnarok.fenrir.readTypedObjectCompat
import dev.ragnarok.fenrir.writeTypedObjectCompat

class Gift : AbsModel {
    var id: Int
        private set
    var fromId = 0
        private set
    var message: String? = null
        private set
    var date: Long = 0
        private set
    var giftItem: GiftItem? = null
        private set
    var privacy = 0
        private set

    internal constructor(`in`: Parcel) {
        id = `in`.readInt()
        fromId = `in`.readInt()
        message = `in`.readString()
        date = `in`.readLong()
        giftItem = `in`.readTypedObjectCompat(GiftItem.CREATOR)
        privacy = `in`.readInt()
    }

    @AbsModelType
    override fun getModelType(): Int {
        return AbsModelType.MODEL_GIFT
    }

    constructor(id: Int) {
        this.id = id
    }

    fun setId(id: Int): Gift {
        this.id = id
        return this
    }

    fun setFromId(fromId: Int): Gift {
        this.fromId = fromId
        return this
    }

    val thumb: String?
        get() = giftItem?.thumb

    fun setMessage(message: String?): Gift {
        this.message = message
        return this
    }

    fun setDate(date: Long): Gift {
        this.date = date
        return this
    }

    fun setGiftItem(giftItem: GiftItem?): Gift {
        this.giftItem = giftItem
        return this
    }

    fun setPrivacy(privacy: Int): Gift {
        this.privacy = privacy
        return this
    }

    override fun describeContents(): Int {
        return 0
    }

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeInt(id)
        parcel.writeInt(fromId)
        parcel.writeString(message)
        parcel.writeLong(date)
        parcel.writeTypedObjectCompat(giftItem, flags)
        parcel.writeInt(privacy)
    }

    companion object CREATOR : Parcelable.Creator<Gift> {
        override fun createFromParcel(parcel: Parcel): Gift {
            return Gift(parcel)
        }

        override fun newArray(size: Int): Array<Gift?> {
            return arrayOfNulls(size)
        }
    }
}