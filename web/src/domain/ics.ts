import type { TripNotification } from './types'

/** 일정 알림 → iCalendar. 휴대폰 캘린더에 넣으면 현지 시각에 알림이 울린다(웹 절차서 W4-6) */
export function buildIcs(items: TripNotification[], stamp = '20261105T000000Z'): string {
  const esc = (s: string) => s.replace(/\\/g, '\\\\').replace(/[,;]/g, (m) => '\\' + m).replace(/\n/g, '\\n')
  const lines = [
    'BEGIN:VCALENDAR', 'VERSION:2.0', 'PRODID:-//travel-yunnan//web//KO', 'CALSCALE:GREGORIAN', 'X-WR-CALNAME:윈난 여행 알림',
    'BEGIN:VTIMEZONE', 'TZID:Asia/Shanghai', 'BEGIN:STANDARD', 'DTSTART:19700101T000000', 'TZOFFSETFROM:+0800', 'TZOFFSETTO:+0800', 'TZNAME:CST', 'END:STANDARD', 'END:VTIMEZONE',
  ]
  for (const n of items) {
    const dt = n.at.replace(/[-:]/g, '') + '00'
    lines.push('BEGIN:VEVENT', `UID:${n.id}@travel-yunnan`, `DTSTAMP:${stamp}`, `DTSTART;TZID=Asia/Shanghai:${dt}`, 'DURATION:PT15M',
      `SUMMARY:${esc(n.title)}`, `DESCRIPTION:${esc(n.body)}`,
      'BEGIN:VALARM', 'ACTION:DISPLAY', `DESCRIPTION:${esc(n.title)}`, 'TRIGGER:PT0M', 'END:VALARM', 'END:VEVENT')
  }
  lines.push('END:VCALENDAR')
  return lines.join('\r\n') + '\r\n'
}
