package dev.ragnarok.fenrir.fragment.poll

import dev.ragnarok.fenrir.fragment.base.IAccountDependencyView
import dev.ragnarok.fenrir.fragment.base.core.IErrorView
import dev.ragnarok.fenrir.fragment.base.core.IMvpView
import dev.ragnarok.fenrir.model.Poll

interface IPollView : IAccountDependencyView, IMvpView, IErrorView {
    fun displayQuestion(title: String?)
    fun displayPhoto(photo_url: String?)
    fun displayType(anonymous: Boolean)
    fun displayCreationTime(unixtime: Long)
    fun displayVoteCount(count: Int)
    fun displayVotesList(
        answers: MutableList<Poll.Answer>?,
        canCheck: Boolean,
        multiply: Boolean,
        checked: MutableSet<Long>
    )

    fun displayLoading(loading: Boolean)
    fun setupButton(voted: Boolean)

    fun openVoters(
        accountId: Int,
        ownerId: Int,
        pollId: Int,
        board: Boolean,
        answer: Long
    )
}