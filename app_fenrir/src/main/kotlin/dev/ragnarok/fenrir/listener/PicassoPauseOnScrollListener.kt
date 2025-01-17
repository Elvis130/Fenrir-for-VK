package dev.ragnarok.fenrir.listener

import androidx.recyclerview.widget.RecyclerView
import dev.ragnarok.fenrir.picasso.PicassoInstance.Companion.with

class PicassoPauseOnScrollListener(private val tag: String) : RecyclerView.OnScrollListener() {
    override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
        if (newState == RecyclerView.SCROLL_STATE_IDLE || newState == RecyclerView.SCROLL_STATE_DRAGGING) {
            with().resumeTag(tag)
        } else {
            with().pauseTag(tag)
        }
    }
}