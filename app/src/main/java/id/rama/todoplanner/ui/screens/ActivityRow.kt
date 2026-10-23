package id.rama.todoplanner.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import id.rama.todoplanner.domain.Dates
import id.rama.todoplanner.domain.Occurrence
import id.rama.todoplanner.ui.components.CategoryColors

/** Satu baris kegiatan: titik warna kategori + judul + jam + centang selesai. Dipakai semua daftar. */
@Composable
fun ActivityRow(occ: Occurrence, onToggleDone: (Boolean) -> Unit, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp).clickable { onClick() }) {
        Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(10.dp).clip(CircleShape)
                    .background(CategoryColors[occ.activity.category] ?: androidx.compose.ui.graphics.Color.Gray)
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = occ.activity.title,
                modifier = Modifier.weight(1f),
                textDecoration = if (occ.done) TextDecoration.LineThrough else null
            )
            Text(text = Dates.formatTime(occ.activity.startMinutes))
            Checkbox(checked = occ.done, onCheckedChange = { onToggleDone(it) })
        }
    }
}
