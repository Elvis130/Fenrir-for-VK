package dev.ragnarok.fenrir.fragment.communitycontrol.communitylinks

import dev.ragnarok.fenrir.api.model.VKApiCommunity
import dev.ragnarok.fenrir.fragment.base.IAccountDependencyView
import dev.ragnarok.fenrir.fragment.base.core.IErrorView
import dev.ragnarok.fenrir.fragment.base.core.IMvpView

interface ICommunityLinksView : IAccountDependencyView, IErrorView, IMvpView {
    fun displayRefreshing(loadingNow: Boolean)
    fun notifyDataSetChanged()
    fun displayData(links: List<VKApiCommunity.Link>)
    fun openLink(link: String?)
}