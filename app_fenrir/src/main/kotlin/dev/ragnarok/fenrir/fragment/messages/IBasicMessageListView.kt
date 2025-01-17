package dev.ragnarok.fenrir.fragment.messages

import dev.ragnarok.fenrir.fragment.base.IAccountDependencyView
import dev.ragnarok.fenrir.fragment.base.IAttachmentsPlacesView
import dev.ragnarok.fenrir.fragment.base.core.IMvpView
import dev.ragnarok.fenrir.fragment.base.core.IToastView
import dev.ragnarok.fenrir.model.LastReadId
import dev.ragnarok.fenrir.model.Message

interface IBasicMessageListView : IMvpView, IAttachmentsPlacesView, IAccountDependencyView,
    IToastView {
    fun notifyMessagesUpAdded(position: Int, count: Int)
    fun notifyDataChanged()
    fun notifyItemChanged(index: Int)
    fun notifyMessagesDownAdded(count: Int)
    fun configNowVoiceMessagePlaying(
        id: Int,
        progress: Float,
        paused: Boolean,
        amin: Boolean,
        speed: Boolean
    )

    fun bindVoiceHolderById(
        holderId: Int,
        play: Boolean,
        paused: Boolean,
        progress: Float,
        amin: Boolean,
        speed: Boolean
    )

    fun disableVoicePlaying()
    fun showActionMode(
        title: String,
        canEdit: Boolean,
        canPin: Boolean,
        canStar: Boolean,
        doStar: Boolean,
        canSpam: Boolean
    )

    fun finishActionMode()
    fun displayMessages(messages: MutableList<Message>, lastReadId: LastReadId)
}