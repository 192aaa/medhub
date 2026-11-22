<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { createPrescription } from '@/api/doctor-consultation'
import { API_CODE } from '@/api/error-codes'
import type { PrescriptionItemRequest } from '@/api/types'
import TreatmentDecisionPanel from '@/components/treatment-decision-panel.vue'
import { useTreatmentDecisionStore } from '@/stores/treatment-decision'

/**
 * 演示药品目录（MVP 占位）。契约 9 端点未含「药品目录查询」端点，
 * 此处为前端演示数据；后端以 drugId 校验目录存在且启用（DRUG_NOT_FOUND）。
 */
interface DemoDrug {
  drugId: number
  name: string
  spec: string
}
const DEMO_DRUGS: DemoDrug[] = [
  { drugId: 901, name: '阿莫西林胶囊', spec: '0.25g*20' },
  { drugId: 902, name: '布洛芬缓释胶囊', spec: '0.3g*20' },
  { drugId: 903, name: '对乙酰氨基酚片', spec: '0.5g*10' },
  { drugId: 904, name: '感冒灵颗粒', spec: '10g*9' },
  { drugId: 905, name: '头孢克洛胶囊', spec: '0.25g*12' },
]

const route = useRoute()
const router = useRouter()
const consultationId = Number(route.query.consultationId)

const items = ref<PrescriptionItemRequest[]>([{ drugId: 901, dosage: '', caution: '' }])
const errorMessage = ref('')
const loading = ref(false)

const treatmentDecisionStore = useTreatmentDecisionStore()

// FR-001：进入开方页自动触发一次；降级不改变表单默认值与校验
onMounted(() => {
  void treatmentDecisionStore.load(consultationId)
})

function addItem() {
  items.value.push({ drugId: 901, dosage: '', caution: '' })
}

function removeItem(index: number) {
  items.value.splice(index, 1)
}

async function submit() {
  errorMessage.value = ''
  if (items.value.length === 0) {
    errorMessage.value = '请至少添加一项药品'
    return
  }
  for (const item of items.value) {
    if (!item.dosage.trim()) {
      errorMessage.value = '请填写用法用量'
      return
    }
  }
  loading.value = true
  try {
    const res = await createPrescription(consultationId, { items: items.value })
    if (res.code === API_CODE.SUCCESS && res.data) {
      await router.replace(`/workbench/prescriptions/${res.data.prescriptionId}`)
    } else {
      errorMessage.value = res.message
    }
  } catch {
    errorMessage.value = '网络异常，请稍后重试'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="page">
    <header class="page__head">
      <h1>开具电子处方</h1>
      <p class="muted">选择药品并填写用法用量，AI 面板提供辅助建议</p>
    </header>

    <div class="layout">
      <div class="card form">
        <p v-if="errorMessage" class="error">{{ errorMessage }}</p>

        <div v-for="(item, index) in items" :key="index" class="item">
          <div class="item__head">
            <span class="item__index">药品 {{ index + 1 }}</span>
            <button
              type="button"
              class="item__remove"
              :disabled="items.length <= 1"
              @click="removeItem(index)"
            >
              删除
            </button>
          </div>
          <select v-model.number="item.drugId" aria-label="选择药品">
            <option v-for="drug in DEMO_DRUGS" :key="drug.drugId" :value="drug.drugId">
              {{ drug.name }}（{{ drug.spec }}）
            </option>
          </select>
          <input
            v-model="item.dosage"
            type="text"
            placeholder="用法用量（如：每日三次，每次一片）"
          />
          <input v-model="item.caution" type="text" placeholder="注意事项（可选）" />
        </div>

        <div class="actions">
          <button type="button" class="btn btn--ghost" @click="addItem">+ 添加药品</button>
          <button type="button" class="btn btn--primary" :disabled="loading" @click="submit">
            {{ loading ? '开具中…' : '确认签名并开具' }}
          </button>
        </div>
      </div>

      <TreatmentDecisionPanel class="aside" :consultation-id="consultationId" />
    </div>
  </div>
</template>

<style scoped>
.page {
  max-width: 1120px;
  margin: 0 auto;
}

.page__head {
  margin-bottom: 1.25rem;
}

.page__head h1 {
  margin: 0 0 0.25rem;
  font-size: 1.5rem;
}

.page__head p {
  margin: 0;
}

.layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 380px;
  gap: 1.25rem;
  align-items: start;
}

@media (max-width: 960px) {
  .layout {
    grid-template-columns: 1fr;
  }
}

.form {
  padding: 1.4rem;
  min-width: 0;
}

.item {
  display: flex;
  flex-direction: column;
  gap: 0.6rem;
  padding: 1rem;
  margin-bottom: 1rem;
  border: 1px solid var(--sdas-border);
  border-radius: var(--sdas-radius);
  background: var(--sdas-surface-2);
}

.item__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.item__index {
  font-weight: 700;
  font-size: 0.9rem;
}

.item__remove {
  border: 0;
  background: transparent;
  color: var(--sdas-down);
  font-size: 0.82rem;
  font-weight: 600;
}

.actions {
  display: flex;
  gap: 0.6rem;
  justify-content: flex-end;
  margin-top: 0.5rem;
}
</style>
