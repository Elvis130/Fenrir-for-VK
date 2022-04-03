package dev.ragnarok.fenrir.adapter

import android.animation.Animator
import android.animation.ObjectAnimator
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.DialogInterface
import android.graphics.Color
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.core.content.res.ResourcesCompat
import androidx.fragment.app.FragmentActivity
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.BaseTransientBottomBar
import com.squareup.picasso3.Transformation
import dev.ragnarok.fenrir.AccountType
import dev.ragnarok.fenrir.Constants
import dev.ragnarok.fenrir.R
import dev.ragnarok.fenrir.activity.SendAttachmentsActivity.Companion.startForSendAttachments
import dev.ragnarok.fenrir.adapter.AttachmentsViewBinder.OnAttachmentsActionCallback
import dev.ragnarok.fenrir.domain.InteractorFactory
import dev.ragnarok.fenrir.fragment.search.SearchContentType
import dev.ragnarok.fenrir.fragment.search.criteria.AudioSearchCriteria
import dev.ragnarok.fenrir.media.music.MusicPlaybackController.canPlayAfterCurrent
import dev.ragnarok.fenrir.media.music.MusicPlaybackController.currentAudio
import dev.ragnarok.fenrir.media.music.MusicPlaybackController.isNowPlayingOrPreparingOrPaused
import dev.ragnarok.fenrir.media.music.MusicPlaybackController.observeServiceBinding
import dev.ragnarok.fenrir.media.music.MusicPlaybackController.playAfterCurrent
import dev.ragnarok.fenrir.media.music.MusicPlaybackController.playOrPause
import dev.ragnarok.fenrir.media.music.MusicPlaybackController.playerStatus
import dev.ragnarok.fenrir.media.music.MusicPlaybackController.stop
import dev.ragnarok.fenrir.media.music.PlayerStatus
import dev.ragnarok.fenrir.modalbottomsheetdialogfragment.ModalBottomSheetDialogFragment
import dev.ragnarok.fenrir.modalbottomsheetdialogfragment.Option
import dev.ragnarok.fenrir.modalbottomsheetdialogfragment.OptionRequest
import dev.ragnarok.fenrir.model.Audio
import dev.ragnarok.fenrir.model.menu.options.AudioOption
import dev.ragnarok.fenrir.nonNullNoEmpty
import dev.ragnarok.fenrir.picasso.PicassoInstance.Companion.with
import dev.ragnarok.fenrir.picasso.transforms.PolyTransformation
import dev.ragnarok.fenrir.picasso.transforms.RoundTransformation
import dev.ragnarok.fenrir.place.PlaceFactory.SearchByAudioPlace
import dev.ragnarok.fenrir.place.PlaceFactory.getArtistPlace
import dev.ragnarok.fenrir.place.PlaceFactory.getAudiosInAlbumPlace
import dev.ragnarok.fenrir.place.PlaceFactory.getPlayerPlace
import dev.ragnarok.fenrir.place.PlaceFactory.getSingleTabSearchPlace
import dev.ragnarok.fenrir.settings.CurrentTheme
import dev.ragnarok.fenrir.settings.Settings
import dev.ragnarok.fenrir.util.AppPerms.hasReadWriteStoragePermission
import dev.ragnarok.fenrir.util.AppTextUtils
import dev.ragnarok.fenrir.util.CustomToast.Companion.CreateCustomToast
import dev.ragnarok.fenrir.util.DownloadWorkUtils.doDownloadAudio
import dev.ragnarok.fenrir.util.Mp3InfoHelper.getBitrate
import dev.ragnarok.fenrir.util.Mp3InfoHelper.getLength
import dev.ragnarok.fenrir.util.RxUtils.applyCompletableIOToMainSchedulers
import dev.ragnarok.fenrir.util.RxUtils.applyObservableIOToMainSchedulers
import dev.ragnarok.fenrir.util.RxUtils.applySingleIOToMainSchedulers
import dev.ragnarok.fenrir.util.Utils
import dev.ragnarok.fenrir.util.hls.M3U8
import dev.ragnarok.fenrir.view.WeakViewAnimatorAdapter
import dev.ragnarok.fenrir.view.natives.rlottie.RLottieImageView
import io.reactivex.rxjava3.disposables.Disposable

class AudioContainer : LinearLayout {
    private val mAudioInteractor = InteractorFactory.createAudioInteractor()
    private var mPlayerDisposable = Disposable.disposed()
    private var audioListDisposable = Disposable.disposed()
    private var audios: List<Audio> = emptyList()
    private var currAudio = currentAudio

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    )

    constructor(
        context: Context,
        attrs: AttributeSet?,
        defStyleAttr: Int,
        defStyleRes: Int
    ) : super(context, attrs, defStyleAttr, defStyleRes)

    @get:DrawableRes
    private val audioCoverSimple: Int
        get() = if (Settings.get()
                .main().isAudio_round_icon
        ) R.drawable.audio_button else R.drawable.audio_button_material

    private fun TransformCover(): Transformation {
        return if (Settings.get()
                .main().isAudio_round_icon
        ) RoundTransformation() else PolyTransformation()
    }

    private fun updateAudioStatus(holder: AudioHolder, audio: Audio) {
        if (audio != currAudio) {
            holder.visual.setImageResource(audio.songIcon)
            holder.play_cover.clearColorFilter()
            return
        }
        when (playerStatus()) {
            1 -> {
                Utils.doWavesLottie(holder.visual, true)
                holder.play_cover.setColorFilter(Color.parseColor("#44000000"))
            }
            2 -> {
                Utils.doWavesLottie(holder.visual, false)
                holder.play_cover.setColorFilter(Color.parseColor("#44000000"))
            }
        }
    }

    private fun deleteTrack(accountId: Int, audio: Audio) {
        audioListDisposable = mAudioInteractor.delete(accountId, audio.id, audio.ownerId).compose(
            applyCompletableIOToMainSchedulers()
        ).subscribe(
            { CreateCustomToast(context).showToast(R.string.deleted) }) { t: Throwable? ->
            Utils.showErrorInAdapter(
                context as Activity, t
            )
        }
    }

    private fun addTrack(accountId: Int, audio: Audio) {
        audioListDisposable = mAudioInteractor.add(accountId, audio, null).compose(
            applyCompletableIOToMainSchedulers()
        ).subscribe(
            { CreateCustomToast(context).showToast(R.string.added) }) { t: Throwable? ->
            Utils.showErrorInAdapter(
                context as Activity, t
            )
        }
    }

    private fun getMp3AndBitrate(accountId: Int, audio: Audio) {
        val mode = audio.needRefresh()
        if (mode.first) {
            audioListDisposable =
                mAudioInteractor.getByIdOld(accountId, listOf(audio), mode.second).compose(
                    applySingleIOToMainSchedulers()
                )
                    .subscribe({ t: List<Audio> -> getBitrate(t[0]) }) {
                        getBitrate(
                            audio
                        )
                    }
        } else {
            getBitrate(audio)
        }
    }

    private fun getBitrate(audio: Audio) {
        if (audio.url.isNullOrEmpty()) {
            return
        }
        audioListDisposable = if (audio.isHLS) {
            M3U8(audio.url).length.compose(applySingleIOToMainSchedulers())
                .subscribe(
                    { r: Long ->
                        CreateCustomToast(context).showToast(
                            getBitrate(
                                context, audio.duration, r
                            )
                        )
                    }
                ) { e: Throwable? ->
                    Utils.showErrorInAdapter(
                        context as Activity, e
                    )
                }
        } else {
            getLength(audio.url).compose(applySingleIOToMainSchedulers())
                .subscribe(
                    { r: Long ->
                        CreateCustomToast(context).showToast(
                            getBitrate(
                                context, audio.duration, r
                            )
                        )
                    }
                ) { e: Throwable? ->
                    Utils.showErrorInAdapter(
                        context as Activity, e
                    )
                }
        }
    }

    private fun get_lyrics(audio: Audio) {
        audioListDisposable =
            mAudioInteractor.getLyrics(Settings.get().accounts().current, audio.lyricsId)
                .compose(applySingleIOToMainSchedulers())
                .subscribe({ t: String -> onAudioLyricsReceived(t, audio) }) { t: Throwable? ->
                    Utils.showErrorInAdapter(
                        context as Activity, t
                    )
                }
    }

    private fun onAudioLyricsReceived(Text: String, audio: Audio) {
        val title = audio.artistAndTitle
        MaterialAlertDialogBuilder(context)
            .setIcon(R.drawable.dir_song)
            .setMessage(Text)
            .setTitle(title ?: context.getString(R.string.get_lyrics))
            .setPositiveButton(R.string.button_ok, null)
            .setNeutralButton(R.string.copy_text) { _: DialogInterface?, _: Int ->
                val clipboard = context.getSystemService(
                    Context.CLIPBOARD_SERVICE
                ) as ClipboardManager?
                val clip = ClipData.newPlainText("response", Text)
                clipboard?.setPrimaryClip(clip)
                CreateCustomToast(context).showToast(R.string.copied_to_clipboard)
            }
            .setCancelable(true)
            .show()
    }

    fun dispose() {
        mPlayerDisposable.dispose()
        audios = emptyList()
    }

    private fun updateDownloadState(holder: AudioHolder, audio: Audio) {
        if (audio.downloadIndicator == 2) {
            holder.saved.setImageResource(R.drawable.remote_cloud)
            Utils.setColorFilter(
                holder.saved, CurrentTheme.getColorSecondary(
                    context
                )
            )
        } else {
            holder.saved.setImageResource(R.drawable.save)
            Utils.setColorFilter(
                holder.saved, CurrentTheme.getColorPrimary(
                    context
                )
            )
        }
        holder.saved.visibility =
            if (audio.downloadIndicator != 0) VISIBLE else GONE
    }

    private fun doMenu(
        holder: AudioHolder,
        mAttachmentsActionCallback: OnAttachmentsActionCallback?,
        position: Int,
        view: View,
        audio: Audio,
        audios: ArrayList<Audio>
    ) {
        val menus = ModalBottomSheetDialogFragment.Builder()
        menus.add(
            OptionRequest(
                AudioOption.play_item_audio,
                context.getString(R.string.play),
                R.drawable.play,
                true
            )
        )
        if (canPlayAfterCurrent(audio)) {
            menus.add(
                OptionRequest(
                    AudioOption.play_item_after_current_audio,
                    context.getString(R.string.play_audio_after_current),
                    R.drawable.play_next,
                    false
                )
            )
        }
        if (audio.ownerId != Settings.get().accounts().current) {
            menus.add(
                OptionRequest(
                    AudioOption.add_item_audio,
                    context.getString(R.string.action_add),
                    R.drawable.list_add,
                    true
                )
            )
            menus.add(
                OptionRequest(
                    AudioOption.add_and_download_button,
                    context.getString(R.string.add_and_download_button),
                    R.drawable.add_download,
                    false
                )
            )
        } else menus.add(
            OptionRequest(
                AudioOption.add_item_audio,
                context.getString(R.string.delete),
                R.drawable.ic_outline_delete,
                true
            )
        )
        menus.add(
            OptionRequest(
                AudioOption.share_button,
                context.getString(R.string.share),
                R.drawable.ic_outline_share,
                true
            )
        )
        menus.add(
            OptionRequest(
                AudioOption.save_item_audio,
                context.getString(R.string.save),
                R.drawable.save,
                true
            )
        )
        if (audio.albumId != 0) menus.add(
            OptionRequest(
                AudioOption.open_album,
                context.getString(R.string.open_album),
                R.drawable.audio_album,
                false
            )
        )
        menus.add(
            OptionRequest(
                AudioOption.get_recommendation_by_audio,
                context.getString(R.string.get_recommendation_by_audio),
                R.drawable.music_mic,
                false
            )
        )
        if (audio.main_artists.nonNullNoEmpty()) menus.add(
            OptionRequest(
                AudioOption.goto_artist,
                context.getString(R.string.audio_goto_artist),
                R.drawable.artist_icon,
                false
            )
        )
        if (audio.lyricsId != 0) menus.add(
            OptionRequest(
                AudioOption.get_lyrics_menu,
                context.getString(R.string.get_lyrics_menu),
                R.drawable.lyric,
                false
            )
        )
        menus.add(
            OptionRequest(
                AudioOption.bitrate_item_audio,
                context.getString(R.string.get_bitrate),
                R.drawable.high_quality,
                false
            )
        )
        menus.add(
            OptionRequest(
                AudioOption.search_by_artist,
                context.getString(R.string.search_by_artist),
                R.drawable.magnify,
                true
            )
        )
        menus.add(
            OptionRequest(
                AudioOption.copy_url,
                context.getString(R.string.copy_url),
                R.drawable.content_copy,
                false
            )
        )
        menus.header(
            Utils.firstNonEmptyString(audio.artist, " ") + " - " + audio.title,
            R.drawable.song,
            audio.thumb_image_little
        )
        menus.columns(2)
        menus.show(
            (context as FragmentActivity).supportFragmentManager,
            "audio_options",
            object : ModalBottomSheetDialogFragment.Listener {
                override fun onModalOptionSelected(option: Option) {
                    when (option.id) {
                        AudioOption.play_item_audio -> {
                            mAttachmentsActionCallback?.onAudioPlay(position, audios)
                            getPlayerPlace(Settings.get().accounts().current).tryOpenWith(
                                context
                            )
                        }
                        AudioOption.play_item_after_current_audio -> playAfterCurrent(audio)
                        AudioOption.share_button -> startForSendAttachments(
                            context,
                            Settings.get().accounts().current,
                            audio
                        )
                        AudioOption.search_by_artist -> getSingleTabSearchPlace(
                            Settings.get().accounts().current,
                            SearchContentType.AUDIOS,
                            AudioSearchCriteria(
                                audio.artist,
                                by_artist = true,
                                in_main_page = false
                            )
                        ).tryOpenWith(
                            context
                        )
                        AudioOption.get_lyrics_menu -> get_lyrics(audio)
                        AudioOption.get_recommendation_by_audio -> SearchByAudioPlace(
                            Settings.get().accounts().current, audio.ownerId, audio.id
                        ).tryOpenWith(
                            context
                        )
                        AudioOption.open_album -> getAudiosInAlbumPlace(
                            Settings.get().accounts().current,
                            audio.album_owner_id,
                            audio.albumId,
                            audio.album_access_key
                        ).tryOpenWith(
                            context
                        )
                        AudioOption.copy_url -> {
                            val clipboard =
                                context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager?
                            val clip = ClipData.newPlainText("response", audio.url)
                            clipboard?.setPrimaryClip(clip)
                            CreateCustomToast(context).showToast(R.string.copied)
                        }
                        AudioOption.add_item_audio -> {
                            val myAudio = audio.ownerId == Settings.get().accounts().current
                            if (myAudio) {
                                deleteTrack(Settings.get().accounts().current, audio)
                            } else {
                                addTrack(Settings.get().accounts().current, audio)
                            }
                        }
                        AudioOption.add_and_download_button -> {
                            addTrack(Settings.get().accounts().current, audio)
                            if (!hasReadWriteStoragePermission(context)) {
                                mAttachmentsActionCallback?.onRequestWritePermissions()
                                return
                            }
                            audio.downloadIndicator = 1
                            updateDownloadState(holder, audio)
                            val ret = doDownloadAudio(
                                context,
                                audio,
                                Settings.get().accounts().current,
                                Force = false,
                                isLocal = false
                            )
                            if (ret == 0) CreateCustomToast(context).showToastBottom(R.string.saved_audio) else if (ret == 1 || ret == 2) {
                                Utils.ThemedSnack(
                                    view,
                                    if (ret == 1) R.string.audio_force_download else R.string.audio_force_download_pc,
                                    BaseTransientBottomBar.LENGTH_LONG
                                ).setAction(
                                    R.string.button_yes
                                ) {
                                    doDownloadAudio(
                                        context,
                                        audio,
                                        Settings.get().accounts().current,
                                        Force = true,
                                        isLocal = false
                                    )
                                }
                                    .show()
                            } else {
                                audio.downloadIndicator = 0
                                updateDownloadState(holder, audio)
                                CreateCustomToast(context).showToastBottom(R.string.error_audio)
                            }
                        }
                        AudioOption.save_item_audio -> {
                            if (!hasReadWriteStoragePermission(context)) {
                                mAttachmentsActionCallback?.onRequestWritePermissions()
                                return
                            }
                            audio.downloadIndicator = 1
                            updateDownloadState(holder, audio)
                            val ret = doDownloadAudio(
                                context,
                                audio,
                                Settings.get().accounts().current,
                                Force = false,
                                isLocal = false
                            )
                            if (ret == 0) CreateCustomToast(context).showToastBottom(R.string.saved_audio) else if (ret == 1 || ret == 2) {
                                Utils.ThemedSnack(
                                    view,
                                    if (ret == 1) R.string.audio_force_download else R.string.audio_force_download_pc,
                                    BaseTransientBottomBar.LENGTH_LONG
                                ).setAction(
                                    R.string.button_yes
                                ) {
                                    doDownloadAudio(
                                        context,
                                        audio,
                                        Settings.get().accounts().current,
                                        Force = true,
                                        isLocal = false
                                    )
                                }
                                    .show()
                            } else {
                                audio.downloadIndicator = 0
                                updateDownloadState(holder, audio)
                                CreateCustomToast(context).showToastBottom(R.string.error_audio)
                            }
                        }
                        AudioOption.bitrate_item_audio -> getMp3AndBitrate(
                            Settings.get().accounts().current, audio
                        )
                        AudioOption.goto_artist -> {
                            val artists = Utils.getArrayFromHash(audio.main_artists)
                            if (audio.main_artists.keys.size > 1) {
                                MaterialAlertDialogBuilder(context)
                                    .setItems(artists[1]) { _: DialogInterface?, which: Int ->
                                        getArtistPlace(
                                            Settings.get().accounts().current,
                                            artists[0][which],
                                            false
                                        ).tryOpenWith(
                                            context
                                        )
                                    }.show()
                            } else {
                                getArtistPlace(
                                    Settings.get().accounts().current,
                                    artists[0][0],
                                    false
                                ).tryOpenWith(
                                    context
                                )
                            }
                        }
                    }
                }
            })
    }

    private fun doPlay(
        holder: AudioHolder,
        mAttachmentsActionCallback: OnAttachmentsActionCallback?,
        position: Int,
        audio: Audio,
        audios: ArrayList<Audio>
    ) {
        if (isNowPlayingOrPreparingOrPaused(audio)) {
            if (!Settings.get().other().isUse_stop_audio) {
                updateAudioStatus(holder, audio)
                playOrPause()
            } else {
                updateAudioStatus(holder, audio)
                stop()
            }
        } else {
            updateAudioStatus(holder, audio)
            mAttachmentsActionCallback?.onAudioPlay(position, audios)
        }
    }

    fun displayAudios(
        audios: ArrayList<Audio>?,
        mAttachmentsActionCallback: OnAttachmentsActionCallback?
    ) {
        if (audios == null || audios.isEmpty()) {
            visibility = View.GONE
            dispose()
            return
        }
        visibility = View.VISIBLE
        this.audios = audios
        val i = audios.size - childCount
        for (j in 0 until i) {
            val itemView = LayoutInflater.from(context).inflate(R.layout.item_audio, this, false)
            val holder = AudioHolder(itemView)
            itemView.tag = holder
            addView(itemView)
        }
        for (g in 0 until childCount) {
            val root = getChildAt(g) as ViewGroup? ?: continue
            if (g < audios.size) {
                val check = root.tag as AudioHolder? ?: continue
                val audio = audios[g]
                check.tvTitle.text = audio.artist
                check.tvSubtitle.text = audio.title
                if (!audio.isLocal && !audio.isLocalServer && Constants.DEFAULT_ACCOUNT_TYPE == AccountType.VK_ANDROID && !audio.isHLS) {
                    check.quality.visibility = VISIBLE
                    if (audio.isHq) {
                        check.quality.setImageResource(R.drawable.high_quality)
                    } else {
                        check.quality.setImageResource(R.drawable.low_quality)
                    }
                } else {
                    check.quality.visibility = GONE
                }
                updateAudioStatus(check, audio)
                if (audio.thumb_image_little.nonNullNoEmpty()) {
                    with()
                        .load(audio.thumb_image_little)
                        .placeholder(
                            ResourcesCompat.getDrawable(
                                context.resources, audioCoverSimple, context.theme
                            ) ?: return

                        )
                        .transform(TransformCover())
                        .tag(Constants.PICASSO_TAG)
                        .into(check.play_cover)
                } else {
                    with().cancelRequest(check.play_cover)
                    check.play_cover.setImageResource(audioCoverSimple)
                }
                check.ibPlay.setOnLongClickListener {
                    if (audio.thumb_image_very_big.nonNullNoEmpty()
                        || audio.thumb_image_big.nonNullNoEmpty() || audio.thumb_image_little.nonNullNoEmpty()
                    ) {
                        mAttachmentsActionCallback?.onUrlPhotoOpen(
                            Utils.firstNonEmptyString(
                                audio.thumb_image_very_big,
                                audio.thumb_image_big, audio.thumb_image_little
                            )!!, audio.artist, audio.title
                        )
                    }
                    true
                }
                check.ibPlay.setOnClickListener { v: View ->
                    if (Settings.get().main().isRevert_play_audio) {
                        doMenu(check, mAttachmentsActionCallback, g, v, audio, audios)
                    } else {
                        doPlay(check, mAttachmentsActionCallback, g, audio, audios)
                    }
                }
                if (audio.duration <= 0) check.time.visibility = INVISIBLE else {
                    check.time.visibility = VISIBLE
                    check.time.text = AppTextUtils.getDurationString(audio.duration)
                }
                updateDownloadState(check, audio)
                check.lyric.visibility = if (audio.lyricsId != 0) VISIBLE else GONE
                check.my.visibility =
                    if (audio.ownerId == Settings.get().accounts().current) VISIBLE else GONE
                check.Track.setOnLongClickListener { v: View? ->
                    if (!hasReadWriteStoragePermission(context)) {
                        mAttachmentsActionCallback?.onRequestWritePermissions()
                        return@setOnLongClickListener false
                    }
                    audio.downloadIndicator = 1
                    updateDownloadState(check, audio)
                    val ret = doDownloadAudio(
                        context,
                        audio,
                        Settings.get().accounts().current,
                        Force = false,
                        isLocal = false
                    )
                    if (ret == 0) CreateCustomToast(context).showToastBottom(R.string.saved_audio) else if (ret == 1 || ret == 2) {
                        v?.let {
                            Utils.ThemedSnack(
                                it,
                                if (ret == 1) R.string.audio_force_download else R.string.audio_force_download_pc,
                                BaseTransientBottomBar.LENGTH_LONG
                            ).setAction(
                                R.string.button_yes
                            ) {
                                doDownloadAudio(
                                    context,
                                    audio,
                                    Settings.get().accounts().current,
                                    Force = true,
                                    isLocal = false
                                )
                            }
                                .show()
                        }
                    } else {
                        audio.downloadIndicator = 0
                        updateDownloadState(check, audio)
                        CreateCustomToast(context).showToastBottom(R.string.error_audio)
                    }
                    true
                }
                check.Track.setOnClickListener { view: View ->
                    check.cancelSelectionAnimation()
                    check.startSomeAnimation()
                    if (Settings.get().main().isRevert_play_audio) {
                        doPlay(check, mAttachmentsActionCallback, g, audio, audios)
                    } else {
                        doMenu(check, mAttachmentsActionCallback, g, view, audio, audios)
                    }
                }
                root.visibility = VISIBLE
            } else {
                root.visibility = GONE
            }
        }
        mPlayerDisposable.dispose()
        mPlayerDisposable = observeServiceBinding()
            .compose(applyObservableIOToMainSchedulers())
            .subscribe { status: Int -> onServiceBindEvent(status) }
    }

    private fun onServiceBindEvent(@PlayerStatus status: Int) {
        when (status) {
            PlayerStatus.UPDATE_TRACK_INFO, PlayerStatus.UPDATE_PLAY_PAUSE, PlayerStatus.SERVICE_KILLED -> {
                currAudio = currentAudio
                if (childCount < audios.size) return
                var g = 0
                while (g < audios.size) {
                    val root = getChildAt(g) as ViewGroup? ?: continue
                    val holder = root.tag as AudioHolder? ?: continue
                    updateAudioStatus(holder, audios[g])
                    g++
                }
            }
            PlayerStatus.REPEATMODE_CHANGED, PlayerStatus.SHUFFLEMODE_CHANGED, PlayerStatus.UPDATE_PLAY_LIST -> {}
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        currAudio = currentAudio
        if (audios.nonNullNoEmpty()) {
            mPlayerDisposable = observeServiceBinding()
                .compose(applyObservableIOToMainSchedulers())
                .subscribe { status: Int -> onServiceBindEvent(status) }
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        mPlayerDisposable.dispose()
        audioListDisposable.dispose()
    }

    private inner class AudioHolder(root: View) {
        val tvTitle: TextView = root.findViewById(R.id.dialog_title)
        val tvSubtitle: TextView = root.findViewById(R.id.dialog_message)
        val ibPlay: View = root.findViewById(R.id.item_audio_play)
        val play_cover: ImageView = root.findViewById(R.id.item_audio_play_cover)
        val time: TextView = root.findViewById(R.id.item_audio_time)
        val saved: ImageView = root.findViewById(R.id.saved)
        val lyric: ImageView = root.findViewById(R.id.lyric)
        val my: ImageView = root.findViewById(R.id.my)
        val quality: ImageView
        val Track: View = root.findViewById(R.id.track_option)
        val selectionView: MaterialCardView = root.findViewById(R.id.item_audio_selection)
        val isSelectedView: MaterialCardView = root.findViewById(R.id.item_audio_select_add)
        val animationAdapter: Animator.AnimatorListener
        val visual: RLottieImageView
        var animator: ObjectAnimator? = null
        fun startSomeAnimation() {
            selectionView.setCardBackgroundColor(CurrentTheme.getColorSecondary(context))
            selectionView.alpha = 0.5f
            animator = ObjectAnimator.ofFloat(selectionView, ALPHA, 0.0f)
            animator?.duration = 500
            animator?.addListener(animationAdapter)
            animator?.start()
        }

        fun cancelSelectionAnimation() {
            animator?.cancel()
            animator = null
            selectionView.visibility = INVISIBLE
        }

        init {
            isSelectedView.visibility = GONE
            quality = root.findViewById(R.id.quality)
            visual = root.findViewById(R.id.item_audio_visual)
            animationAdapter = object : WeakViewAnimatorAdapter<View>(selectionView) {
                override fun onAnimationEnd(view: View) {
                    view.visibility = GONE
                }

                override fun onAnimationStart(view: View) {
                    view.visibility = VISIBLE
                }

                override fun onAnimationCancel(view: View) {
                    view.visibility = GONE
                }
            }
        }
    }
}