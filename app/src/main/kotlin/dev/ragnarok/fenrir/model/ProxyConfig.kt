package dev.ragnarok.fenrir.model

import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName
import dev.ragnarok.fenrir.api.model.Identificable

@Keep
class ProxyConfig : Identificable {
    private var id = 0

    @SerializedName("address")
    private var address: String? = null

    @SerializedName("port")
    private var port = 0

    @SerializedName("authEnabled")
    private var authEnabled = false

    @SerializedName("user")
    private var user: String? = null

    @SerializedName("pass")
    private var pass: String? = null
    operator fun set(id: Int, address: String?, port: Int): ProxyConfig {
        this.id = id
        this.address = address
        this.port = port
        return this
    }

    fun setAuth(user: String?, pass: String?): ProxyConfig {
        authEnabled = true
        this.user = user
        this.pass = pass
        return this
    }

    fun isAuthEnabled(): Boolean {
        return authEnabled
    }

    fun getPass(): String? {
        return pass
    }

    fun getUser(): String? {
        return user
    }

    fun getAddress(): String? {
        return address
    }

    fun getPort(): Int {
        return port
    }

    override fun getObjectId(): Int {
        return id
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false
        val config = other as ProxyConfig
        return id == config.id
    }

    override fun hashCode(): Int {
        return id
    }
}