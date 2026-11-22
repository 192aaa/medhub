<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { submitConsultation, uploadImage } from '@/api/appointment-consultation'
import { useAppointmentStore } from '@/stores/appointment'
import { useConsultationStore } from '@/stores/consultation'

const router = useRouter()
const appStore = useAppointmentStore()
const conStore = useConsultationStore()
const complaint = ref('')
const description = ref('')
const images = ref<{ url: string }[]>([])
const error = ref('')
const loading = ref(false)

async function onFile(e: Event) {
  const input = e.target as HTMLInputElement
  const files = input.files
  if (!files) return
  for (const f of Array.from(files)) {
    if (images.value.length >= 9) {
      error.value = '最多 9 张图片'
      break
    }
    try {
      const res = await uploadImage(f)
      if (res.code === 200 && res.data) {
        images.value.push({ url: res.data.url })
        conStore.addImage(res.data.url)
      } else {
        error.value = res.message
      }
    } catch {
      error.value = '上传失败'
    }
  }
  input.value = ''
}

async function submit() {
  if (!appStore.appointmentId) {
    error.value = '请先完成挂号支付'
    return
  }
  const text = complaint.value.trim()
  if (text.length < 10 || text.length > 200) {
    error.value = '主诉须为 10-200 字'
    return
  }
  loading.value = true
  error.value = ''
  try {
    const res = await submitConsultation({
      appointmentId: appStore.appointmentId,
      complaint: text,
      description: description.value.trim() || null,
      imageUrls: images.value.map((i) => i.url),
    })
    if (res.code === 200 && res.data) {
      conStore.setConsultation(res.data.consultationId, res.data.appointmentId)
      router.push(`/appointment/consultation/${res.data.consultationId}`)
    } else {
      error.value = res.message
    }
  } catch {
    error.value = '网络异常'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="page">
    <header class="page__head">
      <h1>提交问诊单</h1>
      <p class="muted">填写症状信息并上传相关图片，医生接诊后会尽快回复</p>
    </header>

    <p v-if="error" class="error">{{ error }}</p>

    <div class="card form">
      <div class="field">
        <label for="complaint">主诉（10-200 字）</label>
        <textarea
          id="complaint"
          v-model="complaint"
          rows="3"
          placeholder="描述症状"
        />
      </div>

      <div class="field">
        <label for="description">详细描述（选填）</label>
        <textarea id="description" v-model="description" rows="3" />
      </div>

      <div class="field">
        <label for="upload">图片（≤9 张，单张≤5MB）</label>
        <input
          id="upload"
          type="file"
          accept="image/jpeg,image/png,image/webp"
          multiple
          @change="onFile"
        />
        <div v-if="images.length" class="thumbs">
          <img v-for="(img, i) in images" :key="i" :src="img.url" class="thumb" alt="上传图片" />
        </div>
      </div>

      <button class="btn btn--primary btn--block" :disabled="loading" @click="submit">
        {{ loading ? '提交中…' : '提交问诊单' }}
      </button>
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
  margin: 0 0 0.25rem;
  font-size: 1.5rem;
}

.page__head p {
  margin: 0;
}

.form {
  padding: 1.5rem;
  display: flex;
  flex-direction: column;
  gap: 1.1rem;
}

.field {
  display: flex;
  flex-direction: column;
  gap: 0.4rem;
}

.thumbs {
  display: flex;
  gap: 0.5rem;
  flex-wrap: wrap;
}

.thumb {
  width: 68px;
  height: 68px;
  object-fit: cover;
  border-radius: 0.6rem;
  border: 1px solid var(--sdas-border);
}
</style>
