// La tarjeta para compartir, en pequeño dentro de su diálogo.

package com.nidaria.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import com.nidaria.app.share.Card

@Composable
fun CardPreview(image: ImageBitmap, description: String) {
    // Como en la web: como mucho la mitad larga de la pantalla o 420 dp.
    val screen = LocalConfiguration.current.screenHeightDp
    Image(
        image,
        contentDescription = description,
        modifier = Modifier
            .heightIn(max = minOf(420, (screen * 0.48f).toInt()).dp)
            .aspectRatio(Card.WIDTH.toFloat() / Card.HEIGHT)
            .softShadow(Elevation.Sm, Nd.rMd)
            .clip(RoundedCornerShape(Nd.rMd)),
        contentScale = ContentScale.Fit,
    )
}
