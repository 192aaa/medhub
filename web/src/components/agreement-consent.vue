<script setup lang="ts">
defineProps<{
  modelValue: boolean
  version: string
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
}>()

function onChange(event: Event): void {
  emit('update:modelValue', (event.target as HTMLInputElement).checked)
}
</script>

<template>
  <label class="agreement" for="agreement-accepted">
    <input id="agreement-accepted" type="checkbox" :checked="modelValue" @change="onChange" />
    <span>我已阅读并同意《患者服务协议》</span>
    <span class="version">{{ version }}</span>
  </label>
</template>

<style scoped>
.agreement {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  font-size: 0.88rem;
  color: var(--sdas-text-soft);
  cursor: pointer;
}

.agreement input {
  width: 1rem;
  height: 1rem;
  accent-color: var(--sdas-primary);
}

.version {
  color: var(--sdas-muted);
  font-size: 0.75rem;
}
</style>
