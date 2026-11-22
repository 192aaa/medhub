<script setup lang="ts">
import { RouterLink } from 'vue-router'

defineOptions({ name: 'NotFoundView' })
</script>

<template>
  <section class="page">
    <p class="page__code">404</p>
    <h1>页面不存在</h1>
    <p class="page__msg">您访问的页面可能已被移除或链接有误，请返回首页。</p>
    <RouterLink class="btn btn--primary" to="/">返回首页</RouterLink>
  </section>
</template>

<style scoped>
.page {
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  padding: 4rem 1rem;
}

.page__code {
  margin: 0;
  font-size: 5rem;
  font-weight: 800;
  line-height: 1;
  background: var(--sdas-grad);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
}

.page h1 {
  margin: 0.5rem 0 0.5rem;
  font-size: 1.5rem;
}

.page__msg {
  margin: 0 0 1.5rem;
  color: var(--sdas-muted);
}
</style>
