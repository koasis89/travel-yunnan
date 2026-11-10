// 웹 절차서 W6-2: ../data/web/*.json → public/data.enc (AES-256-GCM), 허용 이메일별 키 포장 → public/keys.json
// 허용 이메일은 환경 변수 ALLOWED_EMAILS(쉼표 구분)로만 받는다. 로컬은 .env.local 에서 읽는다.
import { webcrypto as c } from 'node:crypto'
import { existsSync, mkdirSync, readFileSync, readdirSync, writeFileSync } from 'node:fs'

if (!process.env.ALLOWED_EMAILS && existsSync('.env.local')) {
  for (const line of readFileSync('.env.local', 'utf8').split('\n')) {
    const m = line.match(/^\s*ALLOWED_EMAILS\s*=\s*(.+)\s*$/)
    if (m) process.env.ALLOWED_EMAILS = m[1].replace(/^["']|["']$/g, '')
  }
}
const emails = (process.env.ALLOWED_EMAILS ?? '').split(',').map((e) => e.trim().toLowerCase()).filter(Boolean)
if (emails.length === 0) {
  console.error('ALLOWED_EMAILS(접속 아이디, 쉼표 구분 · 이메일이 아니어도 된다)가 비어 있다. Cloudflare 빌드 변수 또는 web/.env.local 에 설정한다.')
  process.exit(1)
}
const ITER = 600000
const enc = new TextEncoder()
const b64 = (u8) => Buffer.from(u8).toString('base64')

const dir = '../data/web' // 평문은 web/ 밖에 있다
const bundle = Object.fromEntries(readdirSync(dir).filter((f) => f.endsWith('.json')).sort()
  .map((f) => [f, JSON.parse(readFileSync(`${dir}/${f}`, 'utf8'))]))
// 개인 정보(가이드 예약자·예약 번호 등)는 저장소에 두지 않고 빌드 때만 넣는다:
// 환경 변수 PRIVATE_JSON(JSON 문자열, Cloudflare 빌드 변수) 또는 PRIVATE_JSON_FILE(저장소 밖 파일 경로)
const priv = process.env.PRIVATE_JSON ?? (process.env.PRIVATE_JSON_FILE && existsSync(process.env.PRIVATE_JSON_FILE) ? readFileSync(process.env.PRIVATE_JSON_FILE, 'utf8') : '')
if (priv.trim()) {
  try { bundle['private.json'] = JSON.parse(priv); console.log('개인 정보 포함: ' + Object.keys(bundle['private.json']).join(', ')) } catch { console.error('PRIVATE_JSON 이 올바른 JSON 이 아니다(넣지 않음)') }
}

const dek = await c.subtle.generateKey({ name: 'AES-GCM', length: 256 }, true, ['encrypt', 'decrypt'])
const iv = c.getRandomValues(new Uint8Array(12))
const cipher = new Uint8Array(await c.subtle.encrypt({ name: 'AES-GCM', iv }, dek, enc.encode(JSON.stringify(bundle))))
const rawDek = new Uint8Array(await c.subtle.exportKey('raw', dek))

const keys = []
for (const email of emails) {
  const salt = c.getRandomValues(new Uint8Array(16))
  const base = await c.subtle.importKey('raw', enc.encode(email), 'PBKDF2', false, ['deriveKey'])
  const kek = await c.subtle.deriveKey({ name: 'PBKDF2', hash: 'SHA-256', salt, iterations: ITER }, base, { name: 'AES-GCM', length: 256 }, false, ['encrypt'])
  const kiv = c.getRandomValues(new Uint8Array(12))
  keys.push({ salt: b64(salt), iv: b64(kiv), wrapped: b64(new Uint8Array(await c.subtle.encrypt({ name: 'AES-GCM', iv: kiv }, kek, rawDek))) })
}
// 키 순서로 이메일 순서를 추정하지 못하게 섞는다
keys.sort(() => c.getRandomValues(new Uint8Array(1))[0] - 128)
mkdirSync('public', { recursive: true })
writeFileSync('public/data.enc', JSON.stringify({ v: 1, iv: b64(iv), data: b64(cipher) }))
writeFileSync('public/keys.json', JSON.stringify({ v: 1, iterations: ITER, keys }))
console.log(`암호화 완료: 이메일 ${emails.length}개 · 데이터 파일 ${Object.keys(bundle).length}개 · ${(cipher.length / 1024).toFixed(0)}KB`)
