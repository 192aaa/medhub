<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { getConsultation } from '@/api/appointment-consultation'
import type { ConsultationDetailResponse } from '@/api/types'

const route = useRoute()
const detail = ref<ConsultationDetailResponse | null>(null)
const error = ref('')
const loading = ref(false)

onMounted(async () => {
  const id = Number(route.params.id)
  if (!id) {
    error.value = '缺少问诊单 ID'
    return
  }
  loading.value = true
  try {
    const res = await getConsultation(id)
    if (res.code === 200 && res.data) detail.value = res.data
    else error.value = res.message
  } catch {
    error.value = '网络异常'
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <div class="page">
    <header class="page__head">
      <h1>问诊单详情</h1>
    </header>

    <p v-if="error" class="error">{{ error }}</p>
    <p v-if="loading" class="muted">加载中...</p>

    <div v-if="detail" class="card detail">
      <p class="detail__line">状态：{{ detail.status }}</p>
      <p class="detail__line">主诉：{{ detail.complaint }}</p>
      <p v-if="detail.description" class="detail__line">描述：{{ detail.description }}</p>
      <p v-if="detail.summary" class="summary">AI 摘要：{{ detail.summary }}</p>
      <div v-if="detail.images.length" class="thumbs">
        <img v-for="img in detail.images" :key="img.imageId" :src="img.url" class="thumb" alt="问诊图片" />
      </div>
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
  gap: 0.9rem;
}

.detail__line {
  margin: 0;
  padding-bottom: 0.85rem;
  border-bottom: 1px solid var(--sdas-border);
}

.summary {
  margin: 0;
  padding: 0.9rem 1rem;
  border-radius: var(--sdas-radius-sm);
  background: var(--sdas-bg-soft);
  color: var(--sdas-text-soft);
}

.thumbs {
  display: flex;
  gap: 0.5rem;
  flex-wrap: wrap;
  margin-top: 0.25rem;
}

.thumb {
  width: 68px;
  height: 68px;
  object-fit: cover;
  border-radius: 0.6rem;
  border: 1px solid var(--sdas-border);
}
</style>
