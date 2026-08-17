import { BrowserWindow } from 'electron'
import { Auth, tokenUtils, validate } from 'msmc'
import type { AccountData, AppConfig, MclcAuth } from '../shared/types'
import { saveAccount, clearAccount, loadAccount } from './config'
import { randomUUID } from 'crypto'

let authInFlight: Promise<AccountData> | null = null

type MclcUser = {
  access_token: string
  client_token?: string
  uuid: string
  name?: string
  meta?: {
    refresh?: string
    exp?: number
    type: 'mojang' | 'msa' | 'legacy'
    xuid?: string
    demo?: boolean
  }
  user_properties?: unknown
}

function friendlyAuthError(err: unknown): string {
  const raw = err instanceof Error ? err.message : String(err)
  const code = raw.trim()
  const map: Record<string, string> = {
    'error.gui.closed': 'Microsoft login window was closed before finishing.',
    'error.auth.minecraft.profile':
      'Could not load Minecraft profile. Make sure this Microsoft account owns Java Edition.',
    'error.auth.minecraft.token': 'Minecraft login failed. Try again in a minute.',
    'error.auth.xbox.xbl': 'Xbox Live login failed. Check the account and try again.',
    'error.auth.xbox.xsts': 'Xbox authorization failed. Try signing in again.',
    'error.auth.microsoft': 'Microsoft login failed. Try again.',
    'error.state.invalid.gui': 'Login UI failed to start.'
  }
  if (map[code]) return map[code]
  if (/429|too many requests/i.test(code)) {
    return 'Microsoft is rate-limiting logins. Wait a few minutes, then try again.'
  }
  return code || 'Microsoft login failed.'
}

function toAccount(mclc: MclcUser, refreshFallback = ''): AccountData {
  const refresh = mclc.meta?.refresh || refreshFallback || ''
  if (!mclc.access_token || !mclc.uuid || !mclc.name) {
    throw new Error('Incomplete Microsoft / Minecraft session.')
  }
  return {
    id: mclc.uuid,
    name: mclc.name,
    access_token: mclc.access_token,
    refresh_token: refresh,
    mclc: mclc as MclcAuth
  }
}

/** Decode XUID embedded in a Minecraft services JWT (no signature verify). */
function xuidFromAccessToken(accessToken: string): string | undefined {
  try {
    const parts = accessToken.split('.')
    if (parts.length < 2) return undefined
    const b64 = parts[1].replace(/-/g, '+').replace(/_/g, '/')
    const padded = b64 + '='.repeat((4 - (b64.length % 4)) % 4)
    const json = JSON.parse(Buffer.from(padded, 'base64').toString('utf8')) as { xuid?: string }
    return typeof json.xuid === 'string' && json.xuid ? json.xuid : undefined
  } catch {
    return undefined
  }
}

/** Rebuild a launchable MCLC object from a legacy account.json (pre-mclc storage). */
function reconstructMclc(account: AccountData): MclcUser | null {
  if (!account.access_token || !account.id || !account.name) return null
  const xuid = account.mclc?.meta?.xuid || xuidFromAccessToken(account.access_token)
  if (!xuid) return null
  return {
    access_token: account.access_token,
    client_token: account.mclc?.client_token || randomUUID().replace(/-/g, ''),
    uuid: account.id.replace(/-/g, ''),
    name: account.name,
    meta: {
      type: 'msa',
      xuid,
      refresh: account.mclc?.meta?.refresh || account.refresh_token || undefined,
      exp: account.mclc?.meta?.exp,
      demo: account.mclc?.meta?.demo
    },
    user_properties: account.mclc?.user_properties ?? {}
  }
}

function hasUsableSession(account: AccountData): boolean {
  const mclc = account.mclc || reconstructMclc(account)
  if (!mclc?.access_token || !mclc.meta?.xuid) return false
  // msmc validate checks meta.exp; if missing, treat as possibly stale
  if (mclc.meta.exp != null) return validate(mclc as never)
  return true
}

/**
 * Microsoft login via msmc Electron popup (more reliable than system-browser "raw").
 */
export async function microsoftLoginInteractive(_config?: AppConfig): Promise<AccountData> {
  if (authInFlight) return authInFlight

  authInFlight = (async () => {
    try {
      const auth = new Auth('select_account')
      const parent = BrowserWindow.getFocusedWindow() || BrowserWindow.getAllWindows()[0] || undefined
      const xbox = await auth.launch('electron', {
        width: 520,
        height: 720,
        title: 'Deepslate — Microsoft login',
        autoHideMenuBar: true,
        resizable: true,
        parent: parent || undefined,
        modal: Boolean(parent),
        webPreferences: {
          nodeIntegration: false,
          contextIsolation: true
        }
      })
      const mc = await xbox.getMinecraft()
      if (!mc?.profile?.id || !mc.profile.name) {
        throw new Error('Could not load Minecraft profile (does this account own Java Edition?).')
      }
      const mclc = mc.mclc(true) as MclcUser
      if (!mclc.meta?.xuid) {
        const fromJwt = xuidFromAccessToken(mclc.access_token)
        if (fromJwt) {
          mclc.meta = { ...mclc.meta, type: 'msa', xuid: fromJwt }
        } else {
          throw new Error('Microsoft login succeeded but Xbox user id (XUID) is missing.')
        }
      }
      const account = toAccount(mclc, mc.refreshTkn || '')
      // Refresh is ideal, but don't hard-fail a valid session if MS omits it once
      if (!account.refresh_token) {
        console.warn('[auth] Microsoft login returned no refresh token; session may expire sooner')
      }
      saveAccount(account)
      return account
    } catch (err) {
      throw new Error(friendlyAuthError(err))
    } finally {
      authInFlight = null
    }
  })()

  return authInFlight
}

export function microsoftLogout(): void {
  clearAccount()
}

export function currentMicrosoftAccount(): AccountData | null {
  return loadAccount()
}

/**
 * Refresh / restore the stored MSA session for launch.
 * Skips network refresh when the cached token is still valid (avoids 429 / auth flakiness).
 */
export async function refreshMicrosoftAccount(): Promise<AccountData> {
  const account = loadAccount()
  if (!account) throw new Error('Not logged in with Microsoft.')

  const auth = new Auth('select_account')

  let working = account
  if (!working.mclc?.meta?.xuid) {
    const rebuilt = reconstructMclc(working)
    if (rebuilt) {
      working = toAccount(rebuilt, working.refresh_token)
      saveAccount(working)
    }
  }

  // Fast path: still-valid token — do not hit Microsoft every launch
  if (working.mclc && hasUsableSession(working)) {
    return working
  }

  if (working.mclc?.access_token) {
    try {
      const shouldRefresh = !validate(working.mclc as never)
      const restored = await tokenUtils.fromMclcToken(auth, working.mclc as never, shouldRefresh)
      if (restored) {
        const mclc = restored.mclc(true) as MclcUser
        const updated = toAccount(mclc, working.refresh_token || mclc.meta?.refresh || '')
        saveAccount(updated)
        return updated
      }
    } catch {
      /* fall through */
    }
  }

  const refresh = working.refresh_token || working.mclc?.meta?.refresh
  if (refresh) {
    try {
      const xbox = await auth.refresh(refresh)
      const mc = await xbox.getMinecraft()
      if (!mc?.profile?.id || !mc.profile.name) {
        throw new Error('Could not refresh Minecraft profile.')
      }
      const mclc = mc.mclc(true) as MclcUser
      if (!mclc.meta?.xuid) {
        const fromJwt = xuidFromAccessToken(mclc.access_token)
        if (fromJwt) mclc.meta = { ...mclc.meta, type: 'msa', xuid: fromJwt }
      }
      const updated = toAccount(mclc, mc.refreshTkn || refresh)
      saveAccount(updated)
      return updated
    } catch (err) {
      const fallback = reconstructMclc(working)
      if (fallback?.meta?.xuid) {
        const updated = toAccount(fallback, working.refresh_token)
        saveAccount(updated)
        return updated
      }
      throw new Error(
        `Microsoft login expired: ${friendlyAuthError(err)} Sign in again from the account menu.`
      )
    }
  }

  const fallback = reconstructMclc(working)
  if (fallback?.meta?.xuid) {
    const updated = toAccount(fallback, '')
    saveAccount(updated)
    return updated
  }

  clearAccount()
  throw new Error('Microsoft session incomplete — please sign in again with Microsoft.')
}

/** Authorization object for minecraft-launcher-core (must include meta.xuid for MSA). */
export async function microsoftLaunchAuth(): Promise<{
  account: AccountData
  authorization: MclcAuth
}> {
  const account = await refreshMicrosoftAccount()
  const authObj = account.mclc || reconstructMclc(account)
  if (!authObj?.meta?.xuid) {
    clearAccount()
    throw new Error('Microsoft session is missing XUID. Please sign in again.')
  }
  if (!account.mclc) {
    const updated = toAccount(authObj, account.refresh_token)
    saveAccount(updated)
    return { account: updated, authorization: updated.mclc! }
  }
  return { account, authorization: account.mclc }
}
