package dev.ragnarok.fenrir.fragment.absdocumentpreview

import dev.ragnarok.fenrir.fragment.base.IAccountDependencyView
import dev.ragnarok.fenrir.fragment.base.core.IErrorView
import dev.ragnarok.fenrir.fragment.base.core.IMvpView
import dev.ragnarok.fenrir.fragment.base.core.IToastView
import dev.ragnarok.fenrir.model.Document

interface IBasicDocumentView : IMvpView, IAccountDependencyView, IToastView, IErrorView {
    fun shareDocument(accountId: Int, document: Document)
    fun requestWriteExternalStoragePermission()
}