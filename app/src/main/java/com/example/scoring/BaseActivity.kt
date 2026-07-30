package com.example.scoring

import android.os.Bundle
import android.util.TypedValue
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.scoring.ThemeManager.colorize
import com.example.scoring.ThemeManager.colorizeDialog
import com.example.scoring.ThemeManager.createDynamicBuilder
import com.example.scoring.ThemeManager.getSeedColor
import com.google.android.material.dialog.MaterialAlertDialogBuilder

abstract class BaseActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
    }

    protected fun showDynamicDialog(builderAction: MaterialAlertDialogBuilder.() -> Unit): AlertDialog {
        val builder = createDynamicBuilder(this)
        builder.builderAction()
        val dialog = builder.create()
        dialog.show()
        colorizeDialog(dialog)
        return dialog
    }

    protected fun applyDynamicTheme() {
        val seed = getSeedColor(this)
        val isDark = ThemeManager.isDarkMode(this)

        // Take full control of the system window and insets
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)

        try {
            // Disable system-enforced contrast to remove stripes
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                window.isNavigationBarContrastEnforced = false
            }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                window.navigationBarDividerColor = android.graphics.Color.TRANSPARENT
            }

            WindowInsetsControllerCompat(window, window.decorView).apply {
                isAppearanceLightStatusBars = !isDark
                isAppearanceLightNavigationBars = !isDark
            }

            // Ensure BOTH window and content view have the theme background
            val tv = TypedValue()
            val backgroundColor = if (theme.resolveAttribute(com.google.android.material.R.attr.colorSurface, tv, true)) tv.data else android.graphics.Color.BLACK

            // Apply the surface color to system bars and window background to avoid any visible strip
            window.statusBarColor = backgroundColor
            window.navigationBarColor = android.graphics.Color.TRANSPARENT
            window.decorView.setBackgroundColor(backgroundColor)
            
            val contentView = findViewById<View>(android.R.id.content) ?: return
            contentView.setBackgroundColor(backgroundColor)
            
            val extraPaddingTop = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 8f, resources.displayMetrics).toInt()
            
            ViewCompat.setOnApplyWindowInsetsListener(contentView) { v, insets ->
                val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                // Only pad the top for the status bar, let bottom flow edge-to-edge
                v.setPadding(bars.left, bars.top + extraPaddingTop, bars.right, 0)
                insets
            }
        } catch (e: Exception) {
            android.util.Log.e("BASE_ACTIVITY", "Failed to apply system bar theme: ${e.message}")
        }
        
        findViewById<View>(android.R.id.content)?.let { colorize(it) }
    }

    override fun setContentView(layoutResID: Int) {
        super.setContentView(layoutResID)
        try {
            applyDynamicTheme()
        } catch (e: Exception) {
            android.util.Log.e("BASE_ACTIVITY", "Failed to apply dynamic theme in setContentView: ${e.message}")
        }
    }

    override fun onResume() {
        super.onResume()
        try {
            applyDynamicTheme()
        } catch (e: Exception) {
            android.util.Log.e("BASE_ACTIVITY", "Failed to refresh dynamic theme in onResume: ${e.message}")
        }
    }
}
