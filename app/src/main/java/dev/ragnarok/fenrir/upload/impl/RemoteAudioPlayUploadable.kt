package dev.ragnarok.fenrir.upload.impl

import android.content.Context
import dev.ragnarok.fenrir.api.PercentagePublisher
import dev.ragnarok.fenrir.api.interfaces.INetworker
import dev.ragnarok.fenrir.api.model.response.BaseResponse
import dev.ragnarok.fenrir.api.model.server.UploadServer
import dev.ragnarok.fenrir.api.model.server.VkApiAudioUploadServer
import dev.ragnarok.fenrir.exception.NotFoundException
import dev.ragnarok.fenrir.model.Audio
import dev.ragnarok.fenrir.settings.Settings
import dev.ragnarok.fenrir.upload.IUploadable
import dev.ragnarok.fenrir.upload.Upload
import dev.ragnarok.fenrir.upload.UploadResult
import dev.ragnarok.fenrir.upload.UploadUtils
import dev.ragnarok.fenrir.util.RxUtils.safelyCloseAction
import dev.ragnarok.fenrir.util.Utils.firstNonEmptyString
import dev.ragnarok.fenrir.util.Utils.safelyClose
import io.reactivex.rxjava3.core.Single
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.net.URLEncoder

class RemoteAudioPlayUploadable(private val context: Context, private val networker: INetworker) :
    IUploadable<Audio> {
    override fun doUpload(
        upload: Upload,
        initialServer: UploadServer?,
        listener: PercentagePublisher?
    ): Single<UploadResult<Audio>> {
        val `is` = arrayOfNulls<InputStream>(1)
        val local_settings = Settings.get().other().localServer
        return try {
            var server_url = firstNonEmptyString(
                local_settings.url,
                "https://debug.dev"
            ) + "/method/audio.remoteplay"
            if (local_settings.password != null) {
                server_url += "?password=" + URLEncoder.encode(local_settings.password, "utf-8")
            }
            val uri = upload.fileUri
            val file = File(uri!!.path!!)
            if (file.isFile) {
                `is`[0] = FileInputStream(file)
            } else {
                `is`[0] = context.contentResolver.openInputStream(uri)
            }
            if (`is`[0] == null) {
                return Single.error(
                    NotFoundException(
                        "Unable to open InputStream, URI: $uri"
                    )
                )
            }
            val filename = UploadUtils.findFileName(
                context, uri
            )
            val finalServer_url = server_url
            networker.uploads()
                .remotePlayAudioRx(server_url, filename, `is`[0]!!, listener)
                .doFinally(safelyCloseAction(`is`[0]))
                .flatMap { dto: BaseResponse<Int> ->
                    Single.just(
                        UploadResult(
                            VkApiAudioUploadServer(finalServer_url), Audio().setId(
                                dto.response
                            )
                        )
                    )
                }
        } catch (e: Exception) {
            safelyClose(`is`[0])
            Single.error(e)
        }
    }
}