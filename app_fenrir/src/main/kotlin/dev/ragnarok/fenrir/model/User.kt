package dev.ragnarok.fenrir.model

import android.os.Parcel
import android.os.Parcelable
import androidx.annotation.Keep
import dev.ragnarok.fenrir.*
import dev.ragnarok.fenrir.api.model.Identificable
import dev.ragnarok.fenrir.module.parcel.ParcelNative
import dev.ragnarok.fenrir.settings.Settings.get
import dev.ragnarok.fenrir.util.Utils.firstNonEmptyString
import dev.ragnarok.fenrir.util.Utils.isValueAssigned
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.math.abs

@Keep
@Serializable
@SerialName("user")
class User : Owner, Identificable {
    private val id: Int
    var firstName: String? = null
        private set
    var lastName: String? = null
        private set
    var isOnline = false
        private set
    var isOnlineMobile = false
        private set
    var onlineApp = 0
        private set
    var photo50: String? = null
        private set
    var photo100: String? = null
        private set
    var photo200: String? = null
        private set
    var photoMax: String? = null
        private set
    var lastSeen: Long = 0
        private set
    var bdate: String? = null
        private set

    @UserPlatform
    var platform = 0
        private set
    var status: String? = null
        private set

    @Sex
    var sex = 0
        private set
    override var domain: String? = null
        private set
    var maiden_name: String? = null
        private set
    var isFriend = false
        private set
    var friendStatus = 0
        private set
    var canWritePrivateMessage = false
        private set
    var blacklisted_by_me = false
        private set
    var blacklisted = false
        private set
    private var verified = false
    var isCan_access_closed = false
        private set

    constructor(id: Int) : super(OwnerType.USER) {
        this.id = id
    }

    internal constructor(`in`: Parcel) : super(`in`) {
        id = `in`.readInt()
        firstName = `in`.readString()
        lastName = `in`.readString()
        isOnline = `in`.getBoolean()
        isOnlineMobile = `in`.getBoolean()
        onlineApp = `in`.readInt()
        photo50 = `in`.readString()
        photo100 = `in`.readString()
        photo200 = `in`.readString()
        photoMax = `in`.readString()
        lastSeen = `in`.readLong()
        platform = `in`.readInt()
        status = `in`.readString()
        sex = `in`.readInt()
        domain = `in`.readString()
        maiden_name = `in`.readString()
        isFriend = `in`.getBoolean()
        friendStatus = `in`.readInt()
        canWritePrivateMessage = `in`.getBoolean()
        blacklisted_by_me = `in`.getBoolean()
        blacklisted = `in`.getBoolean()
        verified = `in`.getBoolean()
        isCan_access_closed = `in`.getBoolean()
        bdate = `in`.readString()
    }

    internal constructor(`in`: ParcelNative) : super(`in`) {
        id = `in`.readInt()
        firstName = `in`.readString()
        lastName = `in`.readString()
        isOnline = `in`.readBoolean()
        isOnlineMobile = `in`.readBoolean()
        onlineApp = `in`.readInt()
        photo50 = `in`.readString()
        photo100 = `in`.readString()
        photo200 = `in`.readString()
        photoMax = `in`.readString()
        lastSeen = `in`.readLong()
        platform = `in`.readInt()
        status = `in`.readString()
        sex = `in`.readInt()
        domain = `in`.readString()
        maiden_name = `in`.readString()
        isFriend = `in`.readBoolean()
        friendStatus = `in`.readInt()
        canWritePrivateMessage = `in`.readBoolean()
        blacklisted_by_me = `in`.readBoolean()
        blacklisted = `in`.readBoolean()
        verified = `in`.readBoolean()
        isCan_access_closed = `in`.readBoolean()
        bdate = `in`.readString()
    }

    @AbsModelType
    override fun getModelType(): Int {
        return AbsModelType.MODEL_USER
    }

    override val fullName: String
        get() {
            val custom = get().other().getUserNameChanges(id)
            return if (custom.nonNullNoEmpty()) {
                custom
            } else "$firstName $lastName"
        }

    fun setFirstName(firstName: String?): User {
        this.firstName = firstName
        return this
    }

    fun setLastName(lastName: String?): User {
        this.lastName = lastName
        return this
    }

    fun setOnline(online: Boolean): User {
        isOnline = online
        return this
    }

    fun setOnlineMobile(onlineMobile: Boolean): User {
        isOnlineMobile = onlineMobile
        return this
    }

    fun setOnlineApp(onlineApp: Int): User {
        this.onlineApp = onlineApp
        return this
    }

    fun setPhoto50(photo50: String?): User {
        this.photo50 = photo50
        return this
    }

    fun setPhoto100(photo100: String?): User {
        this.photo100 = photo100
        return this
    }

    fun setPhoto200(photo200: String?): User {
        this.photo200 = photo200
        return this
    }

    fun setPhotoMax(photoMax: String?): User {
        this.photoMax = photoMax
        return this
    }

    fun setLastSeen(lastSeen: Long): User {
        this.lastSeen = lastSeen
        return this
    }

    fun setPlatform(@UserPlatform platform: Int): User {
        this.platform = platform
        return this
    }

    fun setStatus(status: String?): User {
        this.status = status
        return this
    }

    fun setSex(@Sex sex: Int): User {
        this.sex = sex
        return this
    }

    fun setDomain(domain: String?): User {
        this.domain = domain
        return this
    }

    fun setFriend(friend: Boolean): User {
        isFriend = friend
        return this
    }

    fun setBdate(bdate: String?): User {
        this.bdate = bdate
        return this
    }

    fun setFriendStatus(friendStatus: Int): User {
        this.friendStatus = friendStatus
        return this
    }

    fun setCanWritePrivateMessage(can_write_private_message: Boolean): User {
        canWritePrivateMessage = can_write_private_message
        return this
    }

    fun setBlacklisted_by_me(blacklisted_by_me: Boolean): User {
        this.blacklisted_by_me = blacklisted_by_me
        return this
    }

    fun setBlacklisted(blacklisted: Boolean): User {
        this.blacklisted = blacklisted
        return this
    }

    override val isDonated: Boolean
        get() = isValueAssigned(ownerId, CheckDonate.donatedOwnersLocal)

    override val isVerified: Boolean
        get() = verified || isDonated

    fun setVerified(verified: Boolean): User {
        this.verified = verified
        return this
    }

    fun setCan_access_closed(can_access_closed: Boolean): User {
        isCan_access_closed = can_access_closed
        return this
    }

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        super.writeToParcel(parcel, flags)
        parcel.writeInt(id)
        parcel.writeString(firstName)
        parcel.writeString(lastName)
        parcel.putBoolean(isOnline)
        parcel.putBoolean(isOnlineMobile)
        parcel.writeInt(onlineApp)
        parcel.writeString(photo50)
        parcel.writeString(photo100)
        parcel.writeString(photo200)
        parcel.writeString(photoMax)
        parcel.writeLong(lastSeen)
        parcel.writeInt(platform)
        parcel.writeString(status)
        parcel.writeInt(sex)
        parcel.writeString(domain)
        parcel.writeString(maiden_name)
        parcel.putBoolean(isFriend)
        parcel.writeInt(friendStatus)
        parcel.putBoolean(canWritePrivateMessage)
        parcel.putBoolean(blacklisted_by_me)
        parcel.putBoolean(blacklisted)
        parcel.putBoolean(verified)
        parcel.putBoolean(isCan_access_closed)
        parcel.writeString(bdate)
    }

    override fun writeToParcelNative(dest: ParcelNative) {
        super.writeToParcelNative(dest)
        dest.writeInt(id)
        dest.writeString(firstName)
        dest.writeString(lastName)
        dest.writeBoolean(isOnline)
        dest.writeBoolean(isOnlineMobile)
        dest.writeInt(onlineApp)
        dest.writeString(photo50)
        dest.writeString(photo100)
        dest.writeString(photo200)
        dest.writeString(photoMax)
        dest.writeLong(lastSeen)
        dest.writeInt(platform)
        dest.writeString(status)
        dest.writeInt(sex)
        dest.writeString(domain)
        dest.writeString(maiden_name)
        dest.writeBoolean(isFriend)
        dest.writeInt(friendStatus)
        dest.writeBoolean(canWritePrivateMessage)
        dest.writeBoolean(blacklisted_by_me)
        dest.writeBoolean(blacklisted)
        dest.writeBoolean(verified)
        dest.writeBoolean(isCan_access_closed)
        dest.writeString(bdate)
    }

    override val ownerId: Int
        get() = abs(id)

    override fun describeContents(): Int {
        return 0
    }

    override fun getObjectId(): Int {
        return id
    }

    override fun get100photoOrSmaller(): String? {
        return firstNonEmptyString(photo100, photo50)
    }

    override val maxSquareAvatar: String?
        get() = firstNonEmptyString(photo200, photo100, photo50)
    override val originalAvatar: String?
        get() = firstNonEmptyString(photoMax, photo200, photo100, photo50)

    fun setMaiden_name(maiden_name: String?): User {
        this.maiden_name = maiden_name
        return this
    }

    companion object {
        @JvmField
        val CREATOR: Parcelable.Creator<User> = object : Parcelable.Creator<User> {
            override fun createFromParcel(`in`: Parcel): User {
                return User(`in`)
            }

            override fun newArray(size: Int): Array<User?> {
                return arrayOfNulls(size)
            }
        }
        val NativeCreator: ParcelNative.Creator<User> =
            object : ParcelNative.Creator<User> {
                override fun readFromParcelNative(dest: ParcelNative): User {
                    return User(dest)
                }

            }
    }
}