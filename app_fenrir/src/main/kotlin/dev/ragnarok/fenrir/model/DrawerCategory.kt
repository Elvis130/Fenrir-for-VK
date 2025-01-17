package dev.ragnarok.fenrir.model

import android.os.Parcel
import android.os.Parcelable
import androidx.annotation.StringRes
import dev.ragnarok.fenrir.getBoolean
import dev.ragnarok.fenrir.putBoolean

class DrawerCategory : Parcelable {
    @StringRes
    private val title: Int

    @SwitchableCategory
    private val key: Int
    private var checked = false

    constructor(@SwitchableCategory key: Int, @StringRes title: Int) {
        this.title = title
        this.key = key
    }

    internal constructor(`in`: Parcel) {
        title = `in`.readInt()
        key = `in`.readInt()
        checked = `in`.getBoolean()
    }

    @StringRes
    fun getTitle(): Int {
        return title
    }

    @SwitchableCategory
    fun getKey(): Int {
        return key
    }

    fun isChecked(): Boolean {
        return checked
    }

    fun setChecked(checked: Boolean): DrawerCategory {
        this.checked = checked
        return this
    }

    override fun describeContents(): Int {
        return 0
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeInt(title)
        dest.writeInt(key)
        dest.putBoolean(checked)
    }

    companion object CREATOR : Parcelable.Creator<DrawerCategory> {
        override fun createFromParcel(parcel: Parcel): DrawerCategory {
            return DrawerCategory(parcel)
        }

        override fun newArray(size: Int): Array<DrawerCategory?> {
            return arrayOfNulls(size)
        }
    }
}