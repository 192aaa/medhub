import { execFileSync } from 'node:child_process'
import { createHmac } from 'node:crypto'
import { expect, test, type Page } from '@playwright/test'

/**
 * T084：真实后端实名认证与问诊门禁（需 MySQL / Redis / Spring Boot / Vite 全部启动）。
 *
 * 夹具通过本机 mysql CLI 动态插入一条合成白名单记录（生产白名单由 DBA 受控导入）；
 * 摘要算法与后端 CryptoServiceImpl.idCardDigest 一致：HmacSHA256(secret, idCard) 的 hex。
 * 可用 E2E_DB_* / E2E_ID_CARD_HMAC_SECRET / E2E_MYSQL_CLI 覆盖下列开发默认值。
 * mysql 客户端未加入 PATH 时，用 E2E_MYSQL_CLI 指向其绝对路径。
 */
const realBackend = process.env.E2E_REAL_BACKEND === '1'
const MYSQL_CLI = process.env.E2E_MYSQL_CLI ?? 'mysql'
const HMAC_SECRET = process.env.E2E_ID_CARD_HMAC_SECRET ?? 'dev-only-id-card-hmac-secret-000000'
const DB = {
  host: process.env.E2E_DB_HOST ?? '127.0.0.1',
  port: process.env.E2E_DB_PORT ?? '3306',
  user: process.env.E2E_DB_USER ?? 'root',
  password: process.env.E2E_DB_PASSWORD ?? '',
  database: process.env.E2E_DB_NAME ?? 'cloud_consultation',
}
const WEIGHTS = [7, 9, 10, 5, 8, 4, 2, 1, 6, 3, 7, 9, 10, 5, 8, 4, 2]
const CHECK_CODES = ['1', '0', 'X', '9', '8', '7', '6', '5', '4', '3', '2']

function randomPhone(): string {
  return `13${Math.floor(Math.random() * 1_000_000_000)
    .toString()
    .padStart(9, '0')}`
}

/** 算法构造 18 位证件号，末位符合 GB 11643；不含任何真实身份信息 */
function randomIdCardNumber(): string {
  const prefix =
    '110105' +
    String(1950 + Math.floor(Math.random() * 50)).padStart(4, '0') +
    String(1 + Math.floor(Math.random() * 12)).padStart(2, '0') +
    String(1 + Math.floor(Math.random() * 28)).padStart(2, '0') +
    String(Math.floor(Math.random() * 1000)).padStart(3, '0')
  let sum = 0
  for (let i = 0; i < WEIGHTS.length; i++) {
    sum += Number(prefix[i]) * WEIGHTS[i]
  }
  return prefix + CHECK_CODES[sum % 11]
}

function idCardDigest(idCard: string): string {
  return createHmac('sha256', HMAC_SECRET).update(idCard, 'utf8').digest('hex')
}

function executeSql(statement: string): void {
  execFileSync(
    MYSQL_CLI,
    [
      '-h',
      DB.host,
      '-P',
      DB.port,
      '-u',
      DB.user,
      `--password=${DB.password}`,
      '--default-character-set=utf8mb4',
      DB.database,
      '-e',
      statement,
    ],
    { encoding: 'utf8' },
  )
}

function escapeLiteral(value: string): string {
  return value.replace(/'/g, "''")
}

async function register(page: Page, phone: string): Promise<void> {
  await page.goto('/patient/register')
  await page.fill('#register-phone', phone)
  await page.getByRole('button', { name: '获取验证码' }).click()
  await page.fill('#verification-code', '888888')
  await page.fill('#register-password', 'abc12345')
  await page.check('#agreement-accepted')
  await page.getByRole('button', { name: '注册并登录' }).click()
  await expect(page).toHaveURL(/\/patient\/profile/)
}

async function endpoint8Status(
  page: Page,
): Promise<{ authStatus: string; consultationAllowed: boolean }> {
  return page.evaluate(async () => {
    const token = localStorage.getItem('sdas.patient.token')
    const response = await fetch('/api/v1/patients/current/identity-status', {
      headers: { Authorization: `Bearer ${token}` },
    })
    const body = await response.json()
    if (body.code !== 200) {
      throw new Error(`端点 8 返回非预期 code=${body.code}`)
    }
    return {
      authStatus: body.data.authStatus,
      consultationAllowed: body.data.consultationAllowed,
    }
  })
}

test.describe('真实后端实名认证与门禁（SC-003、SC-004）', () => {
  test.skip(!realBackend, '设置 E2E_REAL_BACKEND=1 后启用')

  test('未认证放行次数为 0；命中白名单后认证成功且门禁放行', async ({ page }) => {
    const phone = randomPhone()
    const name = `测试姓名${Date.now()}`
    const idCard = randomIdCardNumber()
    const digest = idCardDigest(idCard)

    await register(page, phone)

    // 未认证账户不得进入占位门禁（放行次数为 0）
    await page.goto('/consultation/entry')
    await expect(page).toHaveURL(/\/patient\/identity-verification/)
    await expect(page.getByTestId('consultation-placeholder')).toHaveCount(0)
    await expect(page.locator('main[data-status-loaded="true"]')).toBeVisible()

    // 未命中白名单：状态保持 UNVERIFIED
    await page.fill('#verify-real-name', name)
    await page.fill('#verify-id-card', idCard)
    await page.getByRole('button', { name: '提交认证' }).click()
    await expect(page.getByTestId('verification-error')).toHaveText(
      '账号或密码错误，或验证信息无效',
    )
    expect(await endpoint8Status(page)).toEqual({
      authStatus: 'UNVERIFIED',
      consultationAllowed: false,
    })

    executeSql(
      'INSERT INTO hospital_identity_whitelist ' +
        '(real_name, id_card_hash, id_card_cipher, enabled, source, created_at, updated_at, version) ' +
        `VALUES ('${escapeLiteral(name)}', '${digest}', UNHEX('44454D4F'), 1, 'PRESET', NOW(), NOW(), 0)`,
    )
    try {
      await page.getByRole('button', { name: '提交认证' }).click()
      await expect(page).toHaveURL(/\/patient\/profile/)
      await expect(page.getByTestId('auth-status')).toHaveText('已认证')

      expect(await endpoint8Status(page)).toEqual({
        authStatus: 'VERIFIED',
        consultationAllowed: true,
      })

      await page.goto('/consultation/entry')
      await expect(page).toHaveURL(/\/consultation\/entry/)
      await expect(page.getByTestId('consultation-placeholder')).toHaveText('线上问诊建设中')
    } finally {
      executeSql(`DELETE FROM hospital_identity_whitelist WHERE id_card_hash = '${digest}'`)
    }
  })
})
