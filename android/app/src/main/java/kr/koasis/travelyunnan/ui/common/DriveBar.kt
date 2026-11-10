package kr.koasis.travelyunnan.ui.common

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
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

/** 문서함·사진 일기 위: 드라이브 백업 상태와 한꺼번에 올리기·(문서함) 가져오기. 설정 전이면 설정 안내 */
@OptIn(ExperimentalLayoutApi::class)
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
    FlowRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("드라이브 백업 $done/$total", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.align(Alignment.CenterVertically))
        var ask by remember { mutableStateOf(false) }
        val go = { force: Boolean ->
            scope.launch {
                val (ok, fail) = vm.backupAll(docs, diary, force) { run = it }
                run = null
                Toast.makeText(ctx, if (fail > 0) "${ok}건 업로드 · ${fail}건 실패(인터넷 연결 후 다시 시도하세요)" else "${ok}건 업로드 완료", Toast.LENGTH_SHORT).show()
            }
        }
        if (done < total) FilledTonalButton(enabled = run == null, onClick = { go(false) }) {
            Icon(Icons.Filled.CloudUpload, null); Spacer(Modifier.width(6.dp)); Text(run ?: "업로드하기 (${total - done}건)")
        } else OutlinedButton(enabled = run == null && total > 0, onClick = { ask = true }) {
            Icon(Icons.Filled.CloudUpload, null); Spacer(Modifier.width(6.dp)); Text(run ?: "업로드하기")
        }
        if (docs) {
            var pull by remember { mutableStateOf<List<DriveBackup.DriveFile>?>(null) }
            TextButton(enabled = run == null, onClick = {
                scope.launch {
                    run = "드라이브 목록을 읽는 중…"
                    val r = runCatching { vm.driveToPull() }
                    run = null
                    r.onSuccess { if (it.isEmpty()) Toast.makeText(ctx, "가져올 새 문서 없음", Toast.LENGTH_SHORT).show() else pull = it }
                        .onFailure { Toast.makeText(ctx, "가져오기 실패: ${it.message}", Toast.LENGTH_LONG).show() }
                }
            }) { Icon(Icons.Filled.CloudDownload, null); Spacer(Modifier.width(4.dp)); Text("가져오기") }
            pull?.let { files ->
                AlertDialog(onDismissRequest = { pull = null },
                    text = { Text("드라이브에서 ${files.size}건을 이 휴대폰 문서함으로 가져올까요?") },
                    confirmButton = { TextButton(onClick = {
                        pull = null
                        scope.launch {
                            val (ok, fail) = vm.pullFromDrive(files) { run = it }
                            run = null
                            Toast.makeText(ctx, if (fail > 0) "${ok}건 가져오기 · ${fail}건 실패" else "${ok}건 가져오기 완료", Toast.LENGTH_SHORT).show()
                        }
                    }) { Text("가져오기") } },
                    dismissButton = { TextButton(onClick = { pull = null }) { Text("그만두기") } })
            }
        }
        if (ask) AlertDialog(onDismissRequest = { ask = false },
            text = { Text("${total}건을 드라이브에 다시 업로드할까요? 드라이브에 이미 있는 파일은 건너뛰고, 지워진 파일만 다시 만듭니다..") },
            confirmButton = { TextButton(onClick = { ask = false; go(true) }) { Text("업로드") } },
            dismissButton = { TextButton(onClick = { ask = false }) { Text("그만두기") } })
    }
}
