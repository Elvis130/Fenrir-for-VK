package dev.ragnarok.fenrir.api.model.feedback

import dev.ragnarok.fenrir.api.model.Commentable
import dev.ragnarok.fenrir.api.model.VKApiComment

class VKApiCommentFeedback : VKApiBaseFeedback() {
    var comment_of: Commentable? = null
    var comment: VKApiComment? = null
}