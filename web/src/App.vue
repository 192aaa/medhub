<script setup lang="ts">
import { RouterLink, RouterView } from 'vue-router'
import { useAppStore } from '@/stores/app'

defineOptions({ name: 'App' })

const appStore = useAppStore()
</script>

<template>
  <div class="app-shell" :class="{ 'is-dark': appStore.isDark }">
    <header class="app-header">
      <div class="app-header__inner">
        <RouterLink class="app-brand" to="/">
          <span class="app-brand__mark" aria-hidden="true">+</span>
          <span class="app-brand__text">
            <strong>MedHub</strong>
            <em>医枢 · 在线诊疗平台</em>
          </span>
        </RouterLink>
        <nav class="app-nav" aria-label="主导航">
          <RouterLink to="/">首页</RouterLink>
          <RouterLink to="/appointment/departments">预约挂号</RouterLink>
          <RouterLink to="/health-consultation">AI 健康咨询</RouterLink>
          <RouterLink to="/system/health">健康检查</RouterLink>
        </nav>
        <button type="button" class="app-theme" @click="appStore.toggleTheme">
          {{ appStore.isDark ? '☀ 浅色' : '🌙 深色' }}
        </button>
      </div>
    </header>

    <main class="app-main">
      <RouterView />
    </main>

    <footer class="app-footer">
      <span>MedHub 医枢 · 在线诊疗平台 · 演示环境</span>
      <span>AI 建议仅供参考，不作为诊疗结论</span>
    </footer>
  </div>
</template>

<style scoped>
.app-shell {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
}

.app-header {
  position: sticky;
  top: 0;
  z-index: 30;
  background: var(--sdas-surface);
  border-bottom: 1px solid var(--sdas-border);
  box-shadow: var(--sdas-shadow-sm);
}

.app-header__inner {
  display: flex;
  align-items: center;
  gap: 1.5rem;
  max-width: 1180px;
  margin: 0 auto;
  padding: 0.625rem 1.25rem;
}

.app-brand {
  display: flex;
  align-items: center;
  gap: 0.6rem;
  color: var(--sdas-text);
}

.app-brand__mark {
  display: grid;
  place-items: center;
  width: 2.15rem;
  height: 2.15rem;
  border-radius: 0.6rem;
  background: var(--sdas-grad);
  color: #fff;
  font-size: 1.3rem;
  font-weight: 700;
  line-height: 1;
  box-shadow: 0 4px 12px rgba(37, 99, 235, 0.3);
}

.app-brand__text {
  display: flex;
  flex-direction: column;
  line-height: 1.1;
}

.app-brand__text strong {
  font-size: 1.05rem;
  letter-spacing: 0.04em;
}

.app-brand__text em {
  font-style: normal;
  font-size: 0.72rem;
  color: var(--sdas-muted);
}

.app-nav {
  display: flex;
  flex: 1;
  gap: 0.25rem;
}

.app-nav a {
  padding: 0.45rem 0.85rem;
  border-radius: 0.5rem;
  color: var(--sdas-text-soft);
  font-size: 0.9rem;
  font-weight: 500;
  transition:
    background-color 0.15s ease,
    color 0.15s ease;
}

.app-nav a:hover {
  background: var(--sdas-bg-soft);
  color: var(--sdas-primary);
}

.app-nav a.router-link-exact-active {
  background: var(--sdas-primary-soft);
  color: var(--sdas-primary);
  font-weight: 600;
}

.app-theme {
  flex-shrink: 0;
  padding: 0.4rem 0.85rem;
  border: 1px solid var(--sdas-border);
  border-radius: 999px;
  background: var(--sdas-surface);
  color: var(--sdas-text-soft);
  font-size: 0.85rem;
  transition:
    border-color 0.15s ease,
    color 0.15s ease;
}

.app-theme:hover {
  border-color: var(--sdas-primary);
  color: var(--sdas-primary);
}

.app-main {
  flex: 1;
  width: 100%;
  max-width: 1180px;
  margin: 0 auto;
  padding: 1.75rem 1.25rem 3rem;
}

.app-footer {
  display: flex;
  justify-content: space-between;
  gap: 1rem;
  padding: 1.25rem;
  border-top: 1px solid var(--sdas-border);
  color: var(--sdas-muted);
  font-size: 0.8rem;
}

@media (max-width: 720px) {
  .app-header__inner {
    flex-wrap: wrap;
    gap: 0.6rem 0.75rem;
  }

  .app-nav {
    order: 3;
    width: 100%;
    overflow-x: auto;
    gap: 0.25rem;
  }

  .app-nav a {
    white-space: nowrap;
  }

  .app-main {
    padding: 1.25rem 0.9rem 2.5rem;
  }

  .app-footer {
    flex-direction: column;
    text-align: center;
  }
}
</style>
