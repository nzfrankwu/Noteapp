package com.nzfrankwu.noteapp.domain

/** A page's size in page units (1/72 inch, the same as a PDF's points). */
data class PageSize(val width: Float, val height: Float) {
    val isValid: Boolean get() = width.isFinite() && height.isFinite() && width > 0f && height > 0f
}
