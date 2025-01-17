package dev.ragnarok.fenrir.fragment.fave.favelinks

import dev.ragnarok.fenrir.fragment.base.IAccountDependencyView
import dev.ragnarok.fenrir.fragment.base.core.IErrorView
import dev.ragnarok.fenrir.fragment.base.core.IMvpView
import dev.ragnarok.fenrir.model.FaveLink

interface IFaveLinksView : IAccountDependencyView, IMvpView, IErrorView {
    fun displayLinks(links: List<FaveLink>)
    fun notifyDataSetChanged()
    fun notifyDataAdded(position: Int, count: Int)
    fun displayRefreshing(refreshing: Boolean)
    fun openLink(accountId: Int, link: FaveLink)
    fun notifyItemRemoved(index: Int)
}