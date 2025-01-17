package dev.ragnarok.fenrir.fragment.communitycontrol.communityinfolinks

import dev.ragnarok.fenrir.api.model.VKApiCommunity
import dev.ragnarok.fenrir.fragment.base.IAccountDependencyView
import dev.ragnarok.fenrir.fragment.base.core.IErrorView
import dev.ragnarok.fenrir.fragment.base.core.IMvpView

interface ICommunityInfoLinksView : IAccountDependencyView, IErrorView, IMvpView {
    fun displayRefreshing(loadingNow: Boolean)
    fun notifyDataSetChanged()
    fun displayData(links: List<VKApiCommunity.Link>)
    fun openLink(link: String?)
}