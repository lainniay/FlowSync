export function getUsernameValidationError(
  value: string,
): string | undefined {
  if (value.length < 1 || value.length > 50) {
    return '用户名长度为 1 到 50 个字符'
  }

  return undefined
}
