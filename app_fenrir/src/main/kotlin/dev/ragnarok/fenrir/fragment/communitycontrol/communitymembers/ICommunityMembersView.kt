package dev.ragnarok.fenrir.fragment.communitycontrol.communitymembers

import dev.ragnarok.fenrir.fragment.base.IAccountDependencyView
import dev.ragnarok.fenrir.fragment.base.core.IErrorView
import dev.ragnarok.fenrir.fragment.base.core.IMvpView
import dev.ragnarok.fenrir.model.Owner

interface ICommunityMembersView : IAccountDependencyView, IMvpView, IErrorView {
    fun displayData(users: List<Owner>)
    fun notifyItemRemoved(position: Int)
    fun notifyDataSetChanged()
    fun notifyDataAdded(position: Int, count: Int)
    fun openUserWall(accountId: Int, user: Owner)
    fun showRefreshing(refreshing: Boolean)
    fun onSearch(accountId: Int, groupId: Int)
    fun onOptions(filter: String?)
    fun showModMembers(add: List<Owner>, remove: List<Owner>, accountId: Int, ownerId: Int)
}