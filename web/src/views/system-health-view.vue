<script setup lang="ts">
import { onMounted } from 'vue'
import HealthPanel from '@/components/health-panel.vue'
import { useHealth } from '@/composables/use-health'

defineOptions({ name: 'SystemHealthView' })

// views 不直接调用 axios；请求统一经 composable → src/api/ 封装
const { report, loading, error, getHealth } = useHealth()

onMounted(() => {
  void getHealth()
})
</script>

<template>
  <section class="page">
    <header class="page__head">
      <h1>服务健康检查</h1>
      <p class="page__lead">调用链：views → composables → src/api → Vite 代理 → Spring Boot</p>
    </header>
    <HealthPanel :report="report" :loading="loading" :error="error" @refresh="getHealth" />
  </section>
</template>

<style scoped>
.page {
  max-width: 820px;
  margin: 0 auto;
}

.page__head {
  margin-bottom: 1.25rem;
}

.page__head h1 {
  margin: 0 0 0.25rem;
  font-size: 1.5rem;
}

.page__lead {
  margin: 0;
  color: var(--sdas-muted);
  font-size: 0.88rem;
}
</style>
