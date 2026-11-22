<script setup lang="ts">
import { RouterLink } from 'vue-router'

defineOptions({ name: 'HomeView' })

interface Entry {
  label: string
  to: string
  note: string
}

interface Group {
  title: string
  icon: string
  entries: Entry[]
}

const groups: Group[] = [
  {
    title: '账号与认证',
    icon: '🪪',
    entries: [
      { label: '患者注册', to: '/patient/register', note: '手机号注册并实名认证' },
      { label: '患者登录', to: '/patient/login', note: '患者端入口' },
      { label: '医生工作台登录', to: '/workbench/login', note: '医生端入口' },
      { label: '药店登录', to: '/pharmacy/login', note: '药店端入口' },
    ],
  },
  {
    title: '患者问诊',
    icon: '🩺',
    entries: [
      { label: '预约挂号', to: '/appointment/departments', note: '选科室 · 选医生' },
      { label: 'AI 智能导诊', to: '/appointment/triage', note: '无需登录' },
      { label: '提交问诊单', to: '/appointment/consultation', note: '图文 + 图片上传' },
    ],
  },
  {
    title: '医生接诊与开药',
    icon: '💊',
    entries: [
      { label: '待接诊列表', to: '/workbench/consultations', note: '需医生登录' },
      {
        label: '开具处方（AI 辅助）',
        to: '/workbench/prescriptions/new?consultationId=501',
        note: 'AI 摘要 / 建议 / 用药草稿',
      },
    ],
  },
  {
    title: '处方取药',
    icon: '🏥',
    entries: [{ label: '处方验真', to: '/pharmacy/verify', note: '扫码直达或手工输入验真码' }],
  },
  {
    title: '健康咨询智能体',
    icon: '🤖',
    entries: [
      { label: 'AI 健康咨询', to: '/health-consultation', note: '流式回答与紧急症状提示' },
    ],
  },
]
</script>

<template>
  <section class="page">
    <div class="hero">
      <p class="hero__badge">MedHub 医枢 · 在线诊疗平台</p>
      <h1 class="hero__title">患者问诊 · 医生接诊 · 处方取药</h1>
      <p class="hero__lead">
        从预约挂号、图文问诊到电子处方核销的全流程闭环，融合 AI 导诊、AI 辅助治疗决策与健康咨询智能体。
      </p>
      <div class="hero__actions">
        <RouterLink class="btn btn--primary" to="/appointment/departments">立即预约挂号</RouterLink>
        <RouterLink class="btn btn--ghost" to="/health-consultation">AI 健康咨询</RouterLink>
      </div>
    </div>

    <div class="grid">
      <div v-for="group in groups" :key="group.title" class="card group">
        <h2 class="group__title">
          <span class="group__icon">{{ group.icon }}</span>
          {{ group.title }}
        </h2>
        <ul class="group__list">
          <li v-for="entry in group.entries" :key="entry.to">
            <RouterLink class="group__link" :to="entry.to">
              <span class="group__label">{{ entry.label }}</span>
              <span class="group__note">{{ entry.note }}</span>
              <span class="group__arrow" aria-hidden="true">→</span>
            </RouterLink>
          </li>
        </ul>
      </div>
    </div>
  </section>
</template>

<style scoped>
.page {
  max-width: 1080px;
  margin: 0 auto;
}

.hero {
  padding: 2.5rem 2rem;
  border-radius: var(--sdas-radius-lg);
  background:
    radial-gradient(120% 140% at 100% 0%, rgba(14, 165, 233, 0.22), transparent 55%),
    radial-gradient(120% 140% at 0% 100%, rgba(37, 99, 235, 0.18), transparent 55%),
    var(--sdas-surface);
  border: 1px solid var(--sdas-border);
  box-shadow: var(--sdas-shadow);
  margin-bottom: 1.5rem;
}

.hero__badge {
  display: inline-block;
  margin: 0 0 0.75rem;
  padding: 0.25rem 0.75rem;
  border-radius: 999px;
  background: var(--sdas-primary-soft);
  color: var(--sdas-primary);
  font-size: 0.78rem;
  font-weight: 600;
}

.hero__title {
  margin: 0 0 0.6rem;
  font-size: 2rem;
  letter-spacing: -0.01em;
}

.hero__lead {
  margin: 0 0 1.5rem;
  max-width: 40rem;
  color: var(--sdas-muted);
  font-size: 1rem;
}

.hero__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 0.75rem;
}

.grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: 1rem;
}

.group {
  padding: 1.25rem 1.4rem;
  transition:
    transform 0.15s ease,
    box-shadow 0.15s ease;
}

.group:hover {
  transform: translateY(-2px);
  box-shadow: var(--sdas-shadow-lg);
}

.group__title {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  margin: 0 0 0.9rem;
  font-size: 1.05rem;
}

.group__icon {
  font-size: 1.2rem;
}

.group__list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 0.4rem;
}

.group__link {
  display: flex;
  align-items: center;
  gap: 0.6rem;
  padding: 0.6rem 0.75rem;
  border-radius: var(--sdas-radius-sm);
  color: var(--sdas-text);
  transition: background-color 0.15s ease;
}

.group__link:hover {
  background: var(--sdas-primary-soft);
}

.group__label {
  font-weight: 600;
  font-size: 0.92rem;
  color: var(--sdas-primary);
}

.group__note {
  flex: 1;
  font-size: 0.8rem;
  color: var(--sdas-muted);
  text-align: right;
}

.group__arrow {
  color: var(--sdas-muted-light);
  transition: transform 0.15s ease;
}

.group__link:hover .group__arrow {
  transform: translateX(2px);
  color: var(--sdas-primary);
}

@media (max-width: 640px) {
  .hero {
    padding: 1.75rem 1.25rem;
  }

  .hero__title {
    font-size: 1.5rem;
  }

  .group__note {
    display: none;
  }
}
</style>
