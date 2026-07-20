export type UpdateProfileRequest = {
  readonly displayName: string
  readonly phone: string | null
  readonly email: string | null
}

export type ChangePasswordRequest = {
  readonly currentPassword: string
  readonly newPassword: string
}
