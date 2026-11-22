<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { getPrescription } from '@/api/doctor-consultation'
import { API_CODE } from '@/api/error-codes'
import type { PrescriptionDetailResponse } from '@/api/types'

const route = useRoute()
const prescriptionId = Number(route.params.id)
const detail = ref<PrescriptionDetailResponse | null>(null)
const errorMessage = ref('')
const loading = ref(false)

async function load() {
  loading.value = true
  errorMessage.value = ''
  try {
    const res = await getPrescription(prescriptionId)
    if (res.code === API_CODE.SUCCESS && res.data) {
      detail.value = res.data
    } else {
      errorMessage.value = res.message
    }
  } catch {
    errorMessage.value = '网络异常，请稍后重试'
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="page">
    <header class="page__head">
      <h1>处方详情</h1>
    </header>

    <p v-if="loading" class="muted">加载中…</p>
    <p v-if="errorMessage" class="error">{{ errorMessage }}</p>

    <div v-if="detail" class="card detail">
      <div class="detail__head">
        <div>
          <span class="detail__label">处方单号</span>
          <p class="detail__no">{{ detail.prescriptionId }}</p>
        </div>
        <span class="badge" :class="detail.expired ? 'badge--warn' : 'badge--ok'">
          {{ detail.status }}{{ detail.expired ? ' · 已过期' : '' }}
        </span>
      </div>

      <div class="detail__section">
        <h2>药品明细</h2>
        <div class="drugs">
          <div v-for="item in detail.items" :key="item.drugId" class="drug">
            <div class="drug__name">{{ item.drugName }}（{{ item.spec }}）</div>
            <div class="drug__dosage">{{ item.dosage }}</div>
            <div v-if="item.caution" class="drug__caution">注意：{{ item.caution }}</div>
          </div>
        </div>
      </div>

      <div class="detail__meta">
        <span v-if="detail.signedAt">签名时间：{{ detail.signedAt }}</span>
        <span v-if="detail.validUntil">有效期至：{{ detail.validUntil }}</span>
      </div>

      <p v-if="detail.watermark" class="detail__watermark">{{ detail.watermark }}</p>
      <p v-if="detail.qrCode" class="detail__qr">验真链接：{{ detail.qrCode }}</p>
    </div>
  </div>
</template>

<style scoped>
.page {
  max-width: 680px;
  margin: 0 auto;
}

.page__head {
  margin-bottom: 1.25rem;
}

.page__head h1 {
  margin: 0;
  font-size: 1.5rem;
}

.detail {
  padding: 1.5rem;
  display: flex;
  flex-direction: column;
  gap: 1.25rem;
}

.detail__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 1rem;
}

.detail__label {
  color: var(--sdas-muted);
  font-size: 0.82rem;
}

.detail__no {
  margin: 0.1rem 0 0;
  font-size: 1.3rem;
  font-weight: 800;
}

.detail__section h2 {
  margin: 0 0 0.6rem;
  font-size: 0.95rem;
}

.drugs {
  display: flex;
  flex-direction: column;
  gap: 0.6rem;
}

.drug {
  padding: 0.85rem 1rem;
  border-radius: var(--sdas-radius-sm);
  background: var(--sdas-bg-soft);
}

.drug__name {
  font-weight: 700;
}

.drug__dosage {
  margin-top: 0.2rem;
  color: var(--sdas-text-soft);
}

.drug__caution {
  margin-top: 0.2rem;
  color: var(--sdas-warn);
  font-size: 0.85rem;
}

.detail__meta {
  display: flex;
  flex-direction: column;
  gap: 0.3rem;
  color: var(--sdas-muted);
  font-size: 0.85rem;
}

.detail__watermark {
  margin: 0;
  color: var(--sdas-muted-light);
  font-size: 0.8rem;
  text-align: right;
}

.detail__qr {
  margin: 0;
  color: var(--sdas-primary);
  font-size: 0.85rem;
  word-break: break-all;
}

.error {
  color: var(--sdas-down);
}

.muted {
  color: var(--sdas-muted);
}
</style>
