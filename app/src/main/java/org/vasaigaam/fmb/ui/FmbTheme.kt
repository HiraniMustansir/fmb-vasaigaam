package org.vasaigaam.fmb.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val Light = lightColorScheme(
 primary=Color(0xFF145E4D),onPrimary=Color.White,primaryContainer=Color(0xFFD5EBDF),onPrimaryContainer=Color(0xFF073D30),
 secondary=Color(0xFF785D20),onSecondary=Color.White,secondaryContainer=Color(0xFFF3E6BF),onSecondaryContainer=Color(0xFF463508),
 tertiary=Color(0xFF795A36),tertiaryContainer=Color(0xFFFFDDB5),onTertiaryContainer=Color(0xFF2C1800),
 background=Color(0xFFFAF8F2),surface=Color(0xFFFAF8F2),onBackground=Color(0xFF202A25),onSurface=Color(0xFF202A25),
 surfaceContainerHighest=Color(0xFFE2E8DF),surfaceContainerLowest=Color.White,surfaceTint=Color(0xFF145E4D),
 surfaceContainer=Color(0xFFF0F0E8),surfaceContainerLow=Color(0xFFF5F3EC),surfaceContainerHigh=Color(0xFFEAECE3),
 surfaceVariant=Color(0xFFE2E8DF),onSurfaceVariant=Color(0xFF48564D),outline=Color(0xFF748177),outlineVariant=Color(0xFFC5CFC4)
)
private val Dark = darkColorScheme(
 primary=Color(0xFF9ED6BF),onPrimary=Color(0xFF00382A),primaryContainer=Color(0xFF174F3F),onPrimaryContainer=Color(0xFFD5EBDF),
 secondary=Color(0xFFE0C583),onSecondary=Color(0xFF3F2E00),secondaryContainer=Color(0xFF58451B),onSecondaryContainer=Color(0xFFF3E6BF),
 tertiary=Color(0xFFE6BE91),tertiaryContainer=Color(0xFF5D4021),onTertiaryContainer=Color(0xFFFFDDB5),
 background=Color(0xFF101612),surface=Color(0xFF101612),onBackground=Color(0xFFE0E6DE),onSurface=Color(0xFFE0E6DE),
 surfaceContainerHighest=Color(0xFF303A31),surfaceContainerLowest=Color(0xFF0B110D),surfaceTint=Color(0xFF9ED6BF),
 surfaceContainer=Color(0xFF1C241E),surfaceContainerLow=Color(0xFF182019),surfaceContainerHigh=Color(0xFF273027),
 surfaceVariant=Color(0xFF3D4B41),onSurfaceVariant=Color(0xFFC5CFC4),outline=Color(0xFF8F9C91),outlineVariant=Color(0xFF3D4B41)
)
@Composable fun FmbTheme(dark:Boolean=isSystemInDarkTheme(),content:@Composable ()->Unit) {
 MaterialTheme(colorScheme=if(dark)Dark else Light,shapes=Shapes(small=RoundedCornerShape(10.dp),medium=RoundedCornerShape(16.dp),large=RoundedCornerShape(24.dp)),content=content)
}
