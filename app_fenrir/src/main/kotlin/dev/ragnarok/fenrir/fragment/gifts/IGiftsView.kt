package dev.ragnarok.fenrir.fragment.gifts

import dev.ragnarok.fenrir.fragment.base.IAccountDependencyView
import dev.ragnarok.fenrir.fragment.base.core.IErrorView
import dev.ragnarok.fenrir.fragment.base.core.IMvpView
import dev.ragnarok.fenrir.model.Gift

interface IGiftsView : IAccountDependencyView, IMvpView, IErrorView {
    fun displayData(gifts: List<Gift>)
    fun notifyDataSetChanged()
    fun notifyDataAdded(position: Int, count: Int)
    fun showRefreshing(refreshing: Boolean)
    fun onOpenWall(accountId: Int, ownerId: Int)
}