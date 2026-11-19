package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

val OrganizerShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(4.dp),
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(8.dp),
    extraLarge = RoundedCornerShape(12.dp)
)

@Composable
fun OrganizerTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = OrganizerDarkColorScheme,
        typography = OrganizerTypography,
        shapes = OrganizerShapes,
        content = content
    )
}
