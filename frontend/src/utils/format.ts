import dayjs from 'dayjs'

export function fmtTime(v?: string | null, pattern = 'YYYY-MM-DD HH:mm'): string {
  return v ? dayjs(v).format(pattern) : '—'
}

/** 密码规则与后端一致：8~32 位，同时包含字母和数字 */
export function checkPassword(pwd: string): string | null {
  if (!pwd || pwd.length < 8 || pwd.length > 32) return '密码长度应为 8~32 位'
  if (!/[A-Za-z]/.test(pwd) || !/\d/.test(pwd)) return '密码必须同时包含字母和数字'
  if (/\s/.test(pwd)) return '密码不能包含空格'
  return null
}

/** 头像文字：两字名显示全名，三字名显示名，更长显示前两个字（避免单字"三"之类被误认为图标） */
export function avatarText(name?: string | null): string {
  const n = (name || '').trim()
  if (n.length <= 2) return n
  return n.length === 3 ? n.slice(1) : n.slice(0, 2)
}
