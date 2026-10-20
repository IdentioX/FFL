package ru.ffl24.direct.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private fun style(size:Int,line:Int,weight:FontWeight=FontWeight.Normal)=TextStyle(
    fontFamily=FontFamily.SansSerif,fontSize=size.sp,lineHeight=line.sp,fontWeight=weight,letterSpacing=0.sp)
val Typography=Typography(
    headlineLarge=style(28,34,FontWeight.Bold),headlineMedium=style(25,31,FontWeight.Bold),headlineSmall=style(23,29,FontWeight.Bold),
    titleLarge=style(21,27,FontWeight.Bold),titleMedium=style(16,22,FontWeight.SemiBold),titleSmall=style(14,20,FontWeight.SemiBold),
    bodyLarge=style(15,21),bodyMedium=style(13,19),bodySmall=style(11,16),
    labelLarge=style(14,20,FontWeight.SemiBold),labelMedium=style(12,17,FontWeight.Medium),labelSmall=style(10,14,FontWeight.Medium))
