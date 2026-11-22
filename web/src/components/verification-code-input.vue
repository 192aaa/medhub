<script setup lang="ts">
defineProps<{
  modelValue: string
  secondsLeft: number
  disabled?: boolean
}>()

const emit = defineEmits<{
  'update:modelValue': [value: string]
  send: []
}>()

function onInput(event: Event): void {
  emit('update:modelValue', (event.target as HTMLInputElement).value)
}
</script>

<template>
  <div class="verification-code">
    <label class="field__label" for="verification-code">验证码</label>
    <div class="verification-code__row">
      <input
        id="verification-code"
        :value="modelValue"
        inputmode="numeric"
        maxlength="6"
        placeholder="6 位验证码"
        @input="onInput"
      />
      <button
        type="button"
        class="btn btn--soft"
        :disabled="disabled || secondsLeft > 0"
        @click="emit('send')"
      >
        {{ secondsLeft > 0 ? `${secondsLeft}s 后重发` : '获取验证码' }}
      </button>
    </div>
  </div>
</template>

<style scoped>
.verification-code {
  display: flex;
  flex-direction: column;
  gap: 0.4rem;
}

.field__label {
  font-size: 0.9rem;
  font-weight: 500;
  color: var(--sdas-text-soft);
}

.verification-code__row {
  display: flex;
  gap: 0.5rem;
}

.verification-code__row input {
  flex: 1;
  min-width: 0;
}

.verification-code__row .btn {
  flex-shrink: 0;
  white-space: nowrap;
}
</style>
