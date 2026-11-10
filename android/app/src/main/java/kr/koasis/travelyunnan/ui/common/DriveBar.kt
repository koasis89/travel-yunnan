package kr.koasis.travelyunnan.ui.common

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kr.koasis.travelyunnan.data.DriveBackup
import kr.koasis.travelyunnan.ui.AppViewModel
import kr.koasis.travelyunnan.ui.nav.LocalActions

/** 문서함·사진 일기 위: 드라이브 백업 상태와 한꺼번에 올리기. 설정 전이면 설정 안내 */
@Composable
fun DriveBar(vm: AppViewModel, done: Int, total: Int, docs: Boolean, diary: Boolean) {
    val ctx = LocalContext.current
    val actions = LocalActions.current
    val scope = rememberCoroutineScope()
    var run by remember { mutableStateOf<String?>(null) }
    if (!DriveBackup.ready(vm.driveConfig())) {
        TextButton(onClick = actions.openSettings, modifier = Modifier.padding(horizontal = 8.dp)) { Text("구글 드라이브 백업 설정하기 →") }
        return
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("드라이브 백업 $done/$total", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        var ask by remember { mutableStateOf(false) }
        val go = { force: Boolean ->
            scope.launch {
                val (ok, fail) = vm.backupAll(docs, diary, force) { run = it }
                run = null
                Toast.makeText(ctx, if (fail > 0) "${ok}건 보냄 · ${fail}건 실패(인터넷 확인 후 다시)" else "${ok}건을 드라이브로 보냈다", Toast.LENGTH_SHORT).show()
            }
        }
        if (done < total) FilledTonalButton(enabled = run == null, onClick = { go(false) }) {
            Icon(Icons.Filled.CloudUpload, null); Spacer(Modifier.width(6.dp)); Text(run ?: "드라이브로 백업 (${total - done}건)")
        } else OutlinedButton(enabled = run == null && total > 0, onClick = { ask = true }) {
            Icon(Icons.Filled.CloudUpload, null); Spacer(Modifier.width(6.dp)); Text(run ?: "모두 올렸다 · 다시 올리기")
        }
        if (ask) AlertDialog(onDismissRequest = { ask = false },
            text = { Text("${total}건을 드라이브로 다시 보낼까? 드라이브에 이미 있는 것은 건너뛰고, 지워진 것만 다시 만든다.") },
            confirmButton = { TextButton(onClick = { ask = false; go(true) }) { Text("다시 올리기") } },
            dismissButton = { TextButton(onClick = { ask = false }) { Text("그만두기") } })
    }
}
