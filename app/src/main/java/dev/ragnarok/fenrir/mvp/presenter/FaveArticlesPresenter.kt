package dev.ragnarok.fenrir.mvp.presenter

import android.os.Bundle
import dev.ragnarok.fenrir.domain.IFaveInteractor
import dev.ragnarok.fenrir.domain.InteractorFactory
import dev.ragnarok.fenrir.model.Article
import dev.ragnarok.fenrir.model.Photo
import dev.ragnarok.fenrir.mvp.presenter.base.AccountDependencyPresenter
import dev.ragnarok.fenrir.mvp.view.IFaveArticlesView
import dev.ragnarok.fenrir.util.RxUtils.applySingleIOToMainSchedulers
import io.reactivex.rxjava3.disposables.CompositeDisposable

class FaveArticlesPresenter(accountId: Int, savedInstanceState: Bundle?) :
    AccountDependencyPresenter<IFaveArticlesView>(accountId, savedInstanceState) {
    private val faveInteractor: IFaveInteractor = InteractorFactory.createFaveInteractor()
    private val mArticles: ArrayList<Article> = ArrayList()
    private val cacheDisposable = CompositeDisposable()
    private val netDisposable = CompositeDisposable()
    private var mEndOfContent = false
    private var cacheLoadingNow = false
    private var netLoadingNow = false
    private var doLoadTabs = false
    private fun resolveRefreshingView() {
        view?.showRefreshing(
            netLoadingNow
        )
    }

    public override fun onGuiResumed() {
        super.onGuiResumed()
        resolveRefreshingView()
        doLoadTabs = if (doLoadTabs) {
            return
        } else {
            true
        }
        requestAtLast()
    }

    private fun loadCachedData() {
        cacheLoadingNow = true
        val accountId = accountId
        cacheDisposable.add(faveInteractor.getCachedArticles(accountId)
            .compose(applySingleIOToMainSchedulers())
            .subscribe({ articles: List<Article> -> onCachedDataReceived(articles) }) { t: Throwable ->
                onCacheGetError(
                    t
                )
            })
    }

    private fun onCacheGetError(t: Throwable) {
        cacheLoadingNow = false
        showError(t)
    }

    private fun onCachedDataReceived(articles: List<Article>) {
        cacheLoadingNow = false
        mArticles.clear()
        mArticles.addAll(articles)
        view?.notifyDataSetChanged()
    }

    override fun onDestroyed() {
        cacheDisposable.dispose()
        netDisposable.dispose()
        super.onDestroyed()
    }

    private fun request(offset: Int) {
        netLoadingNow = true
        resolveRefreshingView()
        val accountId = accountId
        netDisposable.add(faveInteractor.getArticles(accountId, COUNT_PER_REQUEST, offset)
            .compose(applySingleIOToMainSchedulers())
            .subscribe({ articles: List<Article> ->
                onNetDataReceived(
                    offset,
                    articles
                )
            }) { t: Throwable -> onNetDataGetError(t) })
    }

    private fun onNetDataGetError(t: Throwable) {
        netLoadingNow = false
        resolveRefreshingView()
        showError(t)
    }

    private fun onNetDataReceived(offset: Int, articles: List<Article>) {
        cacheDisposable.clear()
        cacheLoadingNow = false
        mEndOfContent = articles.isEmpty()
        netLoadingNow = false
        if (offset == 0) {
            mArticles.clear()
            mArticles.addAll(articles)
            view?.notifyDataSetChanged()
        } else {
            val startSize = mArticles.size
            mArticles.addAll(articles)
            view?.notifyDataAdded(
                startSize,
                articles.size
            )
        }
        resolveRefreshingView()
    }

    private fun requestAtLast() {
        request(0)
    }

    private fun requestNext() {
        request(mArticles.size)
    }

    override fun onGuiCreated(viewHost: IFaveArticlesView) {
        super.onGuiCreated(viewHost)
        viewHost.displayData(mArticles)
    }

    private fun canLoadMore(): Boolean {
        return mArticles.isNotEmpty() && !cacheLoadingNow && !netLoadingNow && !mEndOfContent
    }

    fun fireRefresh() {
        cacheDisposable.clear()
        netDisposable.clear()
        netLoadingNow = false
        requestAtLast()
    }

    fun fireArticleDelete(index: Int, article: Article) {
        appendDisposable(faveInteractor.removeArticle(accountId, article.ownerId, article.id)
            .compose(applySingleIOToMainSchedulers())
            .subscribe({
                mArticles.removeAt(index)
                view?.notifyDataSetChanged()
            }) { t: Throwable -> onNetDataGetError(t) })
    }

    fun fireArticleClick(url: String) {
        view?.goToArticle(
            accountId,
            url
        )
    }

    fun firePhotoClick(photo: Photo) {
        view?.goToPhoto(
            accountId,
            photo
        )
    }

    fun fireScrollToEnd() {
        if (canLoadMore()) {
            requestNext()
        }
    }

    companion object {
        private const val COUNT_PER_REQUEST = 25
    }

    init {
        loadCachedData()
    }
}