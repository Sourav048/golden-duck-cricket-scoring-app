package com.example.scoring

import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.Toolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.card.MaterialCardView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.tabs.TabLayout
import com.google.android.material.textfield.TextInputLayout
import androidx.appcompat.app.AlertDialog

object ThemeManager {
    // Fixed seed colors for the app
    private const val SEED_LIGHT = 0xFF00695C.toInt() // Deep Teal
    private const val SEED_DARK = 0xFFFFFFFF.toInt()  // Total White

    @JvmStatic
    fun createDynamicBuilder(context: Context): MaterialAlertDialogBuilder {
        return MaterialAlertDialogBuilder(context)
    }

    @JvmStatic
    fun colorizeDialog(dialog: AlertDialog) {
        val seed = getSeedColor(dialog.context)
        applyToViewRecursive(dialog.window?.decorView, seed)

        // Increase dialog width to 95% of screen width
        dialog.window?.let { window ->
            val width = (dialog.context.resources.displayMetrics.widthPixels * 0.95).toInt()
            window.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)

            // Fix for some devices where background doesn't respect theme
            val bg = getThemeColor(dialog.context, com.google.android.material.R.attr.colorSurface)
            window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            val shape = GradientDrawable().apply {
                setColor(bg)
                cornerRadius = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 28f, dialog.context.resources.displayMetrics)
            }
            window.decorView.findViewById<View>(android.R.id.content)?.background = shape
        }

        // Specifically handle Material Buttons in Dialogs
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.let { colorize(it) }
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.let { colorize(it) }
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL)?.let { colorize(it) }
    }

    @JvmStatic
    fun isDarkMode(context: Context): Boolean {
        val mode = AppCompatDelegate.getDefaultNightMode()
        return when (mode) {
            AppCompatDelegate.MODE_NIGHT_YES -> true
            AppCompatDelegate.MODE_NIGHT_NO -> false
            else -> {
                val uiMode = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
                uiMode == Configuration.UI_MODE_NIGHT_YES
            }
        }
    }

    @JvmStatic
    @ColorInt
    fun getSeedColor(context: Context): Int {
        return if (isDarkMode(context)) SEED_DARK else SEED_LIGHT
    }

    @JvmStatic
    fun colorize(view: View?) {
        val v = view ?: return
        val seed = getSeedColor(v.context)
        applyToViewRecursive(v, seed, false)
    }

    private fun applyToViewRecursive(view: View?, seed: Int, forceContrast: Boolean = false) {
        val v = view ?: return
        if (v.tag == "custom_color") return
        
        val contrast = getContrastColor(seed)
        val ctx = v.context
        var nextForceContrast = forceContrast

        when (v) {
            is Toolbar -> {
                v.setBackgroundColor(seed)
                v.setTitleTextColor(contrast)
            }
            is MaterialButton -> {
                if (v.tag == "custom_color") return
                val isToggleChild = v.parent is MaterialButtonToggleGroup
                if (!isToggleChild) {
                    v.backgroundTintList = ColorStateList.valueOf(seed)
                    v.setTextColor(contrast)
                    v.icon?.let { v.setIconTint(ColorStateList.valueOf(contrast)) }
                } else {
                    v.strokeColor = ColorStateList.valueOf(seed)
                    val surfaceColor = getThemeColor(ctx, com.google.android.material.R.attr.colorSurface)
                    val onSurfaceColor = getThemeColor(ctx, com.google.android.material.R.attr.colorOnSurface)
                    v.backgroundTintList = ColorStateList(
                        arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                        intArrayOf(seed, surfaceColor)
                    )
                    v.setTextColor(ColorStateList(
                        arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                        intArrayOf(contrast, onSurfaceColor)
                    ))
                }
            }
            is FloatingActionButton -> {
                v.backgroundTintList = ColorStateList.valueOf(seed)
                v.imageTintList = ColorStateList.valueOf(contrast)
            }
            is TabLayout -> {
                v.setSelectedTabIndicatorColor(seed)
                val unselectedColor = getThemeColor(ctx, com.google.android.material.R.attr.colorOnSurfaceVariant)
                v.setTabTextColors(unselectedColor, seed)
            }
            is ProgressBar -> {
                v.progressTintList = ColorStateList.valueOf(seed)
                v.indeterminateTintList = ColorStateList.valueOf(seed)
            }
            is MaterialSwitch -> {
                if (isDarkMode(ctx)) {
                    // Let XML handle it in Dark Mode to preserve custom styles
                } else {
                    val onSurface = if (forceContrast) contrast else getThemeColor(ctx, com.google.android.material.R.attr.colorOnSurface)
                    val outline = if (forceContrast) contrast else getThemeColor(ctx, com.google.android.material.R.attr.colorOutline)
                    val thumbSeed = if (forceContrast) contrast else seed

                    v.backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)

                    // RESTORED: Both states use teal (thumbSeed) in Light Mode
                    v.thumbTintList = ColorStateList(
                        arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                        intArrayOf(thumbSeed, thumbSeed)
                    )

                    val trackAlpha = (0.35 * 255).toInt()
                    val checkedTrack = Color.argb(trackAlpha, Color.red(thumbSeed), Color.green(thumbSeed), Color.blue(thumbSeed))

                    // Keep the off-track transparent, letting the track decoration (border) do the visual work
                    v.trackTintList = ColorStateList(
                        arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                        intArrayOf(checkedTrack, Color.TRANSPARENT)
                    )

                    // Draws the outline border around the switch when it is off
                    v.trackDecorationTintList = ColorStateList(
                        arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                        intArrayOf(Color.TRANSPARENT, outline)
                    )

                    v.setTextColor(onSurface)
                }
            }
            is TextInputLayout -> {
                val strokeColor = if (forceContrast) contrast else seed
                val neutralHint = getThemeColor(ctx, com.google.android.material.R.attr.colorOnSurfaceVariant)
                
                v.setBoxStrokeColorStateList(ColorStateList(
                    arrayOf(intArrayOf(android.R.attr.state_focused), intArrayOf()),
                    intArrayOf(strokeColor, getThemeColor(ctx, com.google.android.material.R.attr.colorOutline))
                ))
                v.hintTextColor = ColorStateList(
                    arrayOf(intArrayOf(android.R.attr.state_focused), intArrayOf()),
                    intArrayOf(strokeColor, neutralHint)
                )
                v.defaultHintTextColor = ColorStateList.valueOf(neutralHint)
                v.placeholderTextColor = ColorStateList.valueOf(strokeColor)
                v.setStartIconTintList(ColorStateList.valueOf(strokeColor))
                v.setEndIconTintList(ColorStateList.valueOf(strokeColor))
                if (forceContrast) {
                    v.editText?.setTextColor(contrast)
                    v.editText?.setHintTextColor(Color.argb(150, Color.red(contrast), Color.green(contrast), Color.blue(contrast)))
                } else {
                    val onSurf = getThemeColor(ctx, com.google.android.material.R.attr.colorOnSurface)
                    v.editText?.setTextColor(onSurf)
                }
            }
            is Spinner -> {
                if (forceContrast) {
                    v.backgroundTintList = ColorStateList.valueOf(contrast)
                } else {
                    v.backgroundTintList = ColorStateList.valueOf(seed)
                }
            }
            is MaterialCardView -> {
                // EXCLUDE cardPhotoFrame so it keeps its specific background (usually white) for the photo
                if (v.id == R.id.cardPhotoFrame) return

                // Only force primary coloring for specific Header IDs
                if (v.id == R.id.cardScoringHeader || v.id == R.id.layoutPlayerHeader ||
                    v.id == R.id.cardNewMatch || v.id == R.id.cardHistory ||
                    v.id == R.id.cardPlayers || v.id == R.id.cardStats ||
                    v.id == R.id.layoutSelectPlayer1 || v.id == R.id.layoutSelectPlayer2) {
                    v.setCardBackgroundColor(seed)
                    v.preventCornerOverlap = false // Fix for corner spilling
                    nextForceContrast = true
                }
                // If we ARE forcing contrast (nested), we might need to adjust.
                else if (forceContrast) {
                    v.setCardBackgroundColor(seed)
                }
            }
            is ImageView -> {
                if (v.tag == "custom_color") return
                if (v.id == R.id.btnBackProfile || v.id == R.id.btnPlayerDetailsMenu) {
                    v.imageTintList = ColorStateList.valueOf(contrast)
                }
                else if (v.id == R.id.btnHomeMenu) {
                    v.imageTintList = ColorStateList.valueOf(seed)
                }
                else if (forceContrast) {
                    v.imageTintList = ColorStateList.valueOf(contrast)
                }
            }
            is ImageButton -> {
                if (v.id == R.id.btnHomeMenu) {
                    v.imageTintList = ColorStateList.valueOf(seed)
                }
                else if (forceContrast) {
                    v.imageTintList = ColorStateList.valueOf(contrast)
                }
            }
            is TextView -> {
                if (v.tag == "custom_color") return
                val currentColor = v.textColors.defaultColor
                val isPrestige = RankingRegistry.isPrestigeColor(currentColor)

                if (v.id == R.id.tvPlayerListTitle || v.id == R.id.tvFinalResultBanner || v.id == R.id.tvMatchConfigTitle) {
                    if (forceContrast) v.setTextColor(contrast) else v.setTextColor(seed)
                } else if (v.id == R.id.tvDetailNameDisplay || v.id == R.id.tvDetailJerseyDisplay ||
                    v.id == R.id.tvRankOverall || v.id == R.id.tvRankBatting || v.id == R.id.tvRankBowling) {
                    // Force contrast for details header, unless it's a prestige color
                    if (!isPrestige) v.setTextColor(contrast)
                } else if (forceContrast) {
                    if (!isPrestige) v.setTextColor(contrast)
                } else {
                    if (currentColor == SEED_LIGHT || currentColor == SEED_DARK ||
                        v.id == R.id.tvCoinResult || v.id == R.id.tvTossTitle || v.id == R.id.tvHomeTitle ||
                        v.id == R.id.tvStatRank || v.id == R.id.tvStatPlayerName || v.id == R.id.tvStatValue ||
                        v.id == R.id.tvStatTitle) {

                        if (!isPrestige) v.setTextColor(seed)
                    }
                }
            }
        }

        if (v is ViewGroup) {
            if (v.id == R.id.layoutPlayerHeader || v.id == R.id.layoutLiveHeader || v.id == R.id.cardScoringHeader) {
                v.setBackgroundColor(seed)
                nextForceContrast = true
            }
            for (i in 0 until v.childCount) {
                applyToViewRecursive(v.getChildAt(i), seed, nextForceContrast)
            }
        }
    }

    @JvmStatic
    fun getThemeColor(context: Context, attr: Int): Int {
        val tv = TypedValue()
        return if (context.theme.resolveAttribute(attr, tv, true)) tv.data else Color.LTGRAY
    }

    fun getContrastColor(color: Int): Int {
        val luminance = (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)) / 255.0
        return if (luminance > 0.5) Color.BLACK else Color.WHITE
    }
}