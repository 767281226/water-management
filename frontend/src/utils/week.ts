import dayjs from 'dayjs'

/** 某天所在周的周一（YYYY-MM-DD） */
export function mondayOf(d: string | Date = new Date()): string {
  const x = dayjs(d)
  const dow = (x.day() + 6) % 7 // 周一 = 0
  return x.subtract(dow, 'day').format('YYYY-MM-DD')
}

export function addWeeks(monday: string, n: number): string {
  return dayjs(monday).add(n, 'week').format('YYYY-MM-DD')
}

export function offsetLabel(offset: number): string {
  if (offset === 0) return '本周'
  if (offset === -1) return '上周'
  if (offset === 1) return '下周'
  return offset < 0 ? `${-offset} 周前` : `${offset} 周后`
}

export function fmtDeadline(v: string): string {
  const d = dayjs(v)
  return `周${'日一二三四五六'[d.day()]} ${d.format('MM-DD HH:mm')}`
}

export function fmtDays(v: number | null | undefined): string {
  return v === null || v === undefined ? '—' : `${Number(v)} 天`
}
