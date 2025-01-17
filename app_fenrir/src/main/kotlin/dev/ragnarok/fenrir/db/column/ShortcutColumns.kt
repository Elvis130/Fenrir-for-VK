package dev.ragnarok.fenrir.db.column

import android.provider.BaseColumns

object ShortcutColumns : BaseColumns {
    const val TABLENAME = "shortcuts"
    const val ACTION = "action"
    const val NAME = "name"
    const val COVER = "cover"
}