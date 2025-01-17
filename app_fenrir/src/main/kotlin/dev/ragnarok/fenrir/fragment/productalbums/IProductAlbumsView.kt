package dev.ragnarok.fenrir.fragment.productalbums

import dev.ragnarok.fenrir.fragment.base.IAccountDependencyView
import dev.ragnarok.fenrir.fragment.base.core.IErrorView
import dev.ragnarok.fenrir.fragment.base.core.IMvpView
import dev.ragnarok.fenrir.model.MarketAlbum

interface IProductAlbumsView : IAccountDependencyView, IMvpView, IErrorView {
    fun displayData(market_albums: List<MarketAlbum>)
    fun notifyDataSetChanged()
    fun notifyDataAdded(position: Int, count: Int)
    fun showRefreshing(refreshing: Boolean)
    fun onMarketAlbumOpen(accountId: Int, market_album: MarketAlbum)
}