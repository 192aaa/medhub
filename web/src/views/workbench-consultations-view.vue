<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { listConsultations } from '@/api/doctor-consultation'
import { API_CODE } from '@/api/error-codes'
import type { ConsultationListItem } from '@/api/types'

const router = useRouter()
const activeTab = ref<'PENDING' | 'IN_PROGRESS'>('PENDING')
const items = ref<ConsultationListItem[]>([])
const loading = ref(false)
const errorMessage = ref('')

async function load() {
  loading.value = true
  errorMessage.value = ''
  try {
    const res = await listConsultations(activeTab.value)
    if (res.code === API_CODE.SUCCESS && res.data) {
      items.value = res.data
    } else {
      errorMessage.value = res.message
    }
  } catch {
    errorMessage.value = '网络异常，请稍后重试'
  } finally {
    loading.value = false
  }
}

function open(consultationId: number) {
  router.push(`/workbench/consultations/${consultationId}`)
}

function switchTab(tab: 'PENDING' | 'IN_PROGRESS') {
  activeTab.value = tab
  load()
}

onMounted(load)
</script>

<template>
  <div class="page">
    <header class="page__head">
      <h1>医生工作台</h1>
      <p class="muted">接诊患者图文问诊，处理待接诊与进行中的问诊单</p>
    </header>

    <div class="tabs">
      <button
        type="button"
        class="tab"
        :class="{ 'tab--active': activeTab === 'PENDING' }"
        @click="switchTab('PENDING')"
      >
        待接诊
      </button>
      <button
        type="button"
        class="tab"
        :class="{ 'tab--active': activeTab === 'IN_PROGRESS' }"
        @click="switchTab('IN_PROGRESS')"
      >
        已接诊
      </button>
    </div>

    <p v-if="loading" class="muted">加载中…</p>
    <p v-if="errorMessage" class="error">{{ errorMessage }}</p>
    <p v-if="!loading && !errorMessage && items.length === 0" class="empty">暂无问诊单</p>

    <div class="list">
      <button
        v-for="item in items"
        :key="item.consultationId"
        type="button"
        class="card row-card"
        @click="open(item.consultationId)"
      >
        <div class="row-card__top">
          <span class="row-card__name">{{ item.patientName }}</span>
          <span class="badge badge--info">{{ item.status }}</span>
        </div>
        <div class="row-card__complaint">{{ item.complaint }}</div>
        <div class="row-card__time">{{ item.submittedAt }}</div>
      </button>
    </div>
  </div>
</template>

<style scoped>
.page {
  max-width: 760px;
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

.tabs {
  display: inline-flex;
  gap: 0.25rem;
  padding: 0.25rem;
  margin-bottom: 1.25rem;
  border-radius: 999px;
  background: var(--sdas-bg-soft);
  border: 1px solid var(--sdas-border);
}

.tab {
  padding: 0.45rem 1.1rem;
  border: 0;
  border-radius: 999px;
  background: transparent;
  color: var(--sdas-muted);
  font-weight: 600;
  transition:
    background-color 0.15s ease,
    color 0.15s ease;
}

.tab--active {
  background: var(--sdas-surface);
  color: var(--sdas-primary);
  box-shadow: var(--sdas-shadow-sm);
}

.list {
  display: flex;
  flex-direction: column;
  gap: 0.8rem;
}

.row-card {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
  padding: 1.1rem 1.3rem;
  text-align: left;
  cursor: pointer;
  transition:
    transform 0.15s ease,
    box-shadow 0.15s ease,
    border-color 0.15s ease;
}

.row-card:hover {
  transform: translateY(-2px);
  box-shadow: var(--sdas-shadow-lg);
  border-color: var(--sdas-primary);
}

.row-card__top {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.row-card__name {
  font-weight: 700;
  font-size: 1rem;
}

.row-card__complaint {
  color: var(--sdas-text-soft);
}

.row-card__time {
  color: var(--sdas-muted);
  font-size: 0.8rem;
}

.empty {
  color: var(--sdas-muted);
  text-align: center;
  padding: 2rem 0;
}
</style>
