package ru.ffl24.direct.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import android.app.Activity
import android.content.ContextWrapper
import androidx.compose.ui.unit.dp

private val lightColors=lightColorScheme(primary=AppPrimary,onPrimary=AppOnPrimary,primaryContainer=AppPrimarySoft,
    onPrimaryContainer=AppPrimaryDark,secondary=AppPrimary,secondaryContainer=AppSurfaceSoft,
    background=AppBackground,onBackground=AppText,surface=AppSurface,onSurface=AppText,
    surfaceVariant=AppSurfaceSoft,onSurfaceVariant=AppTextMuted,outline=AppGray,outlineVariant=AppGray,
    error=AppNotReady,errorContainer=AppNotReady.copy(alpha=.09f),onErrorContainer=AppText)
private val darkColors=darkColorScheme(primary=AppDarkPrimarySoft,onPrimary=AppTextDark,primaryContainer=AppDarkSurfaceSoft,
    onPrimaryContainer=AppDarkPrimarySoft,secondary=AppDarkPrimarySoft,secondaryContainer=AppDarkSurfaceSoft,
    background=AppDarkBackground,onBackground=AppDarkText,surface=AppDarkSurface,onSurface=AppDarkText,
    surfaceVariant=AppDarkSurfaceSoft,onSurfaceVariant=AppDarkTextMuted,outline=AppDarkSurfaceSoft,outlineVariant=AppDarkSurfaceSoft,
    error=AppNotReady,errorContainer=AppNotReady.copy(alpha=.12f),onErrorContainer=AppDarkText)

@Composable fun DirectTheme(darkTheme:Boolean,content:@Composable ()->Unit) {
    val view=LocalView.current
    if(!view.isInEditMode)SideEffect {
        var context=view.context
        while(context is ContextWrapper && context !is Activity)context=context.baseContext
        (context as? Activity)?.let {activity->
            WindowCompat.getInsetsController(activity.window,view).apply {isAppearanceLightStatusBars=!darkTheme;isAppearanceLightNavigationBars=!darkTheme}
            if(android.os.Build.VERSION.SDK_INT>=29)activity.window.isNavigationBarContrastEnforced=false
        }
    }
    MaterialTheme(colorScheme=if(darkTheme)darkColors else lightColors,typography=Typography,
        shapes=Shapes(extraSmall=RoundedCornerShape(8.dp),small=RoundedCornerShape(12.dp),medium=RoundedCornerShape(16.dp),large=RoundedCornerShape(22.dp),extraLarge=RoundedCornerShape(28.dp)),content=content)
}
