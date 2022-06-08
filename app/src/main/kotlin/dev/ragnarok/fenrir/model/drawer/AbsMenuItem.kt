package dev.ragnarok.fenrir.model.drawer

import android.os.Parcel
import android.os.Parcelable
import com.google.gson.annotations.SerializedName

open class AbsMenuItem : Parcelable {
    @SerializedName("type")
    val type: Int

    @SerializedName("selected")
    var isSelected = false

    constructor(type: Int) {
        this.type = type
    }

    protected constructor(`in`: Parcel) {
        type = `in`.readInt()
        isSelected = `in`.readInt() == 1
    }

    override fun describeContents(): Int {
        return 0
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeInt(type)
        dest.writeInt(if (isSelected) 1 else 0)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false
        val that = other as AbsMenuItem
        return type == that.type
    }

    override fun hashCode(): Int {
        return type
    }

    companion object {
        const val TYPE_ICON = 0
        const val TYPE_WITHOUT_ICON = 1
        const val TYPE_DIVIDER = 2
        const val TYPE_RECENT_CHAT = 3

        @JvmField
        var CREATOR: Parcelable.Creator<AbsMenuItem> = object : Parcelable.Creator<AbsMenuItem> {
            override fun createFromParcel(source: Parcel): AbsMenuItem {
                return AbsMenuItem(source)
            }

            override fun newArray(size: Int): Array<AbsMenuItem?> {
                return arrayOfNulls(size)
            }
        }
    }
}