<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { verifyPrescription } from '@/api/pharmacy'
import type { VerificationResponse } from '@/api/types'
import { usePharmacyStore } from '@/stores/pharmacy'

const route = useRoute()
const router = useRouter()
const store = usePharmacyStore()

const qrContent = ref('')
const prescriptionId = ref('')
const verifyCode = ref('')
const result = ref<VerificationResponse | null>(null)
const errorMessage = ref('')
const loading = ref(false)

const statusLabel = computed(() => {
  switch (result.value?.dispenseStatus) {
    case 'PENDING':
      return '未取药 · 可核销'
    case 'DISPENSED':
      return '已取药'
    case 'EXPIRED':
      return '已过期'
    case 'REVOKED':
      return '已作废'
    default:
      return ''
  }
})

const statusTone = computed(() => {
  switch (result.value?.dispenseStatus) {
    case 'PENDING':
      return 'ok'
    case 'DISPENSED':
      return 'info'
    default:
      return 'warn'
  }
})

async function run(payload: { qrContent?: string; prescriptionId?: number; verifyCode?: string }) {
  if (loading.value) return
  loading.value = true
  errorMessage.value = ''
  result.value = null
  try {
    const res = await verifyPrescription(payload)
    if (res.code !== 200 || !res.data) {
      errorMessage.value = res.message || '验真失败'
      return
    }
    result.value = res.data
  } catch (error) {
    const status = (error as { response?: { status?: number } })?.response?.status
    if (status === 401 || status === 403) {
      store.clear()
      await router.replace({ name: 'pharmacy-login', query: { redirect: route.fullPath } })
      return
    }
    const message = (error as { response?: { data?: { message?: string } } })?.response?.data
      ?.message
    errorMessage.value = message || '验真失败，请稍后重试'
  } finally {
    loading.value = false
  }
}

async function submitQr(): Promise<void> {
  const content = qrContent.value.trim()
  if (content.length < 8) {
    errorMessage.value = '请粘贴完整二维码内容'
    return
  }
  await run({ qrContent: content })
}

async function submitManual(): Promise<void> {
  const id = Number(prescriptionId.value)
  if (!Number.isInteger(id) || id <= 0) {
    errorMessage.value = '请输入正确的处方号'
    return
  }
  if (!/^[A-Za-z0-9_-]{12}$/.test(verifyCode.value.trim())) {
    errorMessage.value = '验真码为 12 位字母、数字、下划线或连字符'
    return
  }
  await run({ prescriptionId: id, verifyCode: verifyCode.value.trim() })
}

function formatTime(value: string | null): string {
  return value ? value.replace('T', ' ').slice(0, 19) : '—'
}

onMounted(() => {
  // 契约约定 7：手机相机扫到 /pharmacy/verify?t=... 时前端读取 t 自动发起一次验真
  const token = route.query.t
  if (typeof token === 'string' && token.length > 0) {
    qrContent.value = token
    void run({ qrContent: token })
  }
})
</script>

<template>
  <main class="pharmacy-verify">
    <header>
      <h1>处方验真</h1>
      <p v-if="store.hasSession" class="session">{{ store.pharmacyName }} · {{ store.nameMask }}</p>
    </header>

    <section class="panel">
      <h2>扫码验真</h2>
      <p class="hint">扫码后可粘贴二维码内容，或直接由手机相机打开验真链接。</p>
      <textarea v-model="qrContent" rows="3" placeholder="http://…/pharmacy/verify?t=v1.…" />
      <button type="button" :disabled="loading" @click="submitQr">
        {{ loading ? '校验中…' : '扫码内容验真' }}
      </button>
    </section>

    <section class="panel">
      <h2>手工输入</h2>
      <div class="row">
        <label>
          处方号
          <input v-model="prescriptionId" inputmode="numeric" placeholder="例如 1001" />
        </label>
        <label>
          验真码
          <input v-model="verifyCode" maxlength="12" placeholder="12 位验真码" />
        </label>
      </div>
      <button type="button" :disabled="loading" @click="submitManual">手工验真</button>
    </section>

    <p v-if="errorMessage" class="error" role="alert">{{ errorMessage }}</p>

    <article v-if="result" class="result">
      <div class="result-head">
        <strong>处方 #{{ result.prescriptionId }}</strong>
        <span class="badge" :class="statusTone">{{ statusLabel }}</span>
      </div>
      <dl>
        <div>
          <dt>开方医生</dt>
          <dd>{{ result.doctorNameMask || '—' }}</dd>
        </div>
        <div>
          <dt>签名时间</dt>
          <dd>{{ formatTime(result.signedAt) }}</dd>
        </div>
        <div>
          <dt>有效期至</dt>
          <dd>{{ formatTime(result.validUntil) }}</dd>
        </div>
        <div>
          <dt>验真码</dt>
          <dd class="code">{{ result.verifyCode }}</dd>
        </div>
        <div v-if="result.dispensedAt">
          <dt>取药时间</dt>
          <dd>{{ formatTime(result.dispensedAt) }}</dd>
        </div>
        <div v-if="result.dispensedByMask">
          <dt>核销人</dt>
          <dd>{{ result.dispensedByMask }}</dd>
        </div>
      </dl>
      <table>
        <thead>
          <tr>
            <th>药品</th>
            <th>规格</th>
            <th>用法用量</th>
            <th>注意事项</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="item in result.items" :key="item.drugId">
            <td>{{ item.drugName || '—' }}</td>
            <td>{{ item.spec || '—' }}</td>
            <td>{{ item.dosage }}</td>
            <td>{{ item.caution || '—' }}</td>
          </tr>
        </tbody>
      </table>
      <p class="watermark">{{ result.watermark || '示例医院 电子处方' }}</p>
      <p v-if="!result.dispensable" class="blocked">该处方当前不可核销。</p>
    </article>
  </main>
</template>

<style scoped>
.pharmacy-verify {
  max-width: 52rem;
  margin: 2.5rem auto;
  padding: 0 1rem;
  font-family: system-ui, sans-serif;
  color: #0f172a;
}
header {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-bottom: 1.25rem;
}
h1 {
  margin: 0;
  font-size: 1.5rem;
}
.session {
  margin: 0;
  color: #64748b;
  font-size: 0.875rem;
}
.panel {
  margin-bottom: 1.25rem;
  padding: 1.25rem;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
}
h2 {
  margin: 0 0 0.5rem;
  font-size: 1rem;
}
.hint {
  margin: 0 0 0.75rem;
  color: #64748b;
  font-size: 0.875rem;
}
textarea,
input {
  width: 100%;
  padding: 0.55rem 0.7rem;
  border: 1px solid #cbd5e1;
  border-radius: 8px;
  font-size: 0.95rem;
}
.row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0.75rem;
  margin-bottom: 0.75rem;
}
label {
  display: block;
  font-size: 0.875rem;
  color: #334155;
}
button {
  margin-top: 0.75rem;
  padding: 0.6rem 1.1rem;
  border: 0;
  border-radius: 8px;
  background: #0f766e;
  color: #fff;
  cursor: pointer;
}
button:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}
.error {
  color: #b91c1c;
}
.result {
  padding: 1.25rem;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  background: #f8fafc;
}
.result-head {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  margin-bottom: 1rem;
}
.badge {
  padding: 0.15rem 0.6rem;
  border-radius: 999px;
  font-size: 0.8rem;
}
.badge.ok {
  background: #dcfce7;
  color: #166534;
}
.badge.info {
  background: #dbeafe;
  color: #1e40af;
}
.badge.warn {
  background: #fee2e2;
  color: #991b1b;
}
dl {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(12rem, 1fr));
  gap: 0.5rem 1rem;
  margin: 0 0 1rem;
}
dl div {
  display: flex;
  gap: 0.5rem;
}
dt {
  color: #64748b;
  font-size: 0.85rem;
}
dd {
  margin: 0;
  font-size: 0.9rem;
}
.code {
  font-family: ui-monospace, monospace;
  letter-spacing: 0.05em;
}
table {
  width: 100%;
  border-collapse: collapse;
  font-size: 0.9rem;
}
th,
td {
  padding: 0.5rem;
  border-bottom: 1px solid #e2e8f0;
  text-align: left;
}
th {
  color: #475569;
  font-weight: 600;
}
.watermark {
  margin: 1rem 0 0;
  color: #94a3b8;
  font-size: 0.8rem;
  text-align: right;
}
.blocked {
  margin: 0.5rem 0 0;
  color: #991b1b;
  font-size: 0.9rem;
}
</style>
