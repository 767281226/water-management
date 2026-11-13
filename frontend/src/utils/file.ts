export function fmtSize(bytes: number | null | undefined): string {
  if (bytes === null || bytes === undefined) return '—'
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  if (bytes < 1024 * 1024 * 1024) return `${(bytes / 1024 / 1024).toFixed(1)} MB`
  return `${(bytes / 1024 / 1024 / 1024).toFixed(2)} GB`
}

/** 与后端 DocService.ALLOWED_EXT 一致 */
export const ACCEPT_EXT = '.pdf,.doc,.docx,.xls,.xlsx,.ppt,.pptx,.wps,.et,.txt,.dwg,.dxf,.zip,.rar,.7z,.jpg,.jpeg,.png,.gif,.bmp,.webp,.tif,.tiff'
