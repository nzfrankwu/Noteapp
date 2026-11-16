package com.nzfrankwu.noteapp.domain

/** A page's size in page units (1/72 inch, the same as a PDF's points). */
data class PageSize(val width: Float, val height: Float) {
    val isValid: Boolean get() = width.isFinite() && height.isFinite() && width > 0f && height > 0f

    companion object {
        /** The longer side of a page made for a picture: as long as an A4 page's. */
        const val PICTURE_PAGE_LONG_SIDE = 842f

        /** A page shaped like a [widthPx] × [heightPx] picture, its longer side [PICTURE_PAGE_LONG_SIDE]. */
        fun forPicture(widthPx: Int, heightPx: Int): PageSize {
            val aspect = heightPx.toFloat() / widthPx.coerceAtLeast(1)
            return if (aspect >= 1f) PageSize(PICTURE_PAGE_LONG_SIDE / aspect, PICTURE_PAGE_LONG_SIDE)
            else PageSize(PICTURE_PAGE_LONG_SIDE, PICTURE_PAGE_LONG_SIDE * aspect)
        }
    }
}
