/**
 * 윈난 여행 앱 → 구글 드라이브 백업(Google Apps Script 웹 앱).
 *
 * 앱(웹·안드로이드)이 문서함 파일·사진 일기 사진을 이 주소로 보내면, 내 구글 드라이브 폴더에 저장한다.
 * 설정 방법: docs/구글드라이브_백업_설정.md
 *
 *  1) script.google.com → 새 프로젝트 → 이 코드를 통째로 붙여 넣는다.
 *  2) 아래 TOKEN 을 앱에 넣을 비밀 키로 바꾼다(앱 설정의 '백업 키'와 같아야 한다).
 *  3) FOLDER 에 저장할 드라이브 폴더 링크(또는 ID)를 넣는다. 비워 두면 내 드라이브에 '윈난 여행 앱 백업' 폴더를 만든다.
 *  4) 배포 → 새 배포 → 유형 '웹 앱' → 실행 사용자 '나' · 액세스 '모든 사용자' → 배포 → 권한 허용 → 웹 앱 URL 복사.
 *
 * 폴더 구조: <FOLDER>/문서함/D3/…, <FOLDER>/사진일기/D2/…
 * 같은 이름의 파일이 이미 있으면 다시 만들지 않는다(여러 번 백업해도 겹치지 않는다).
 */
const TOKEN = 'CHANGE-ME';
const FOLDER = '';   // 예: 'https://drive.google.com/drive/folders/1AbC...' 또는 '1AbC...'
const DEFAULT_NAME = '윈난 여행 앱 백업';

function rootFolder_() {
  const m = String(FOLDER || '').match(/[-\w]{20,}/);
  if (m) return DriveApp.getFolderById(m[0]);
  const it = DriveApp.getRootFolder().getFoldersByName(DEFAULT_NAME);
  return it.hasNext() ? it.next() : DriveApp.getRootFolder().createFolder(DEFAULT_NAME);
}

function sub_(parent, name) {
  const it = parent.getFoldersByName(name);
  return it.hasNext() ? it.next() : parent.createFolder(name);
}

function json_(o) {
  return ContentService.createTextOutput(JSON.stringify(o)).setMimeType(ContentService.MimeType.JSON);
}

/** 연결 확인: GET ?token=... → 폴더 이름 */
function doGet(e) {
  if (!e || !e.parameter || e.parameter.token !== TOKEN) return json_({ ok: false, error: 'token' });
  const root = rootFolder_();
  return json_({ ok: true, folder: root.getName(), url: root.getUrl() });
}

/** 저장: POST(text/plain) {token, folder, sub, name, mime, data(base64), memo} */
function doPost(e) {
  try {
    const b = JSON.parse(e.postData.contents);
    if (b.token !== TOKEN) return json_({ ok: false, error: 'token' });
    let dir = sub_(rootFolder_(), String(b.folder || '기타').slice(0, 40));
    if (b.sub) dir = sub_(dir, String(b.sub).slice(0, 40));
    const name = String(b.name || ('file-' + Date.now())).replace(/[\\/:*?"<>|]/g, '_').slice(0, 150);
    const same = dir.getFilesByName(name);
    if (same.hasNext()) { const f = same.next(); return json_({ ok: true, skipped: true, id: f.getId(), url: f.getUrl() }); }
    const blob = b.data
      ? Utilities.newBlob(Utilities.base64Decode(b.data), b.mime || 'application/octet-stream', name)
      : Utilities.newBlob(String(b.text || ''), 'text/plain', name);
    const f = dir.createFile(blob);
    if (b.memo) f.setDescription(String(b.memo).slice(0, 4000));
    return json_({ ok: true, id: f.getId(), url: f.getUrl() });
  } catch (err) {
    return json_({ ok: false, error: String(err) });
  }
}
