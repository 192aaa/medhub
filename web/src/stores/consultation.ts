import { defineStore } from 'pinia'
import { ref } from 'vue'

/**
 * 图文问诊单状态（宪法 G2/G5）：store 创建时从 localStorage 恢复。
 */
const STORAGE_KEY = 'sdas-consultation'

export const useConsultationStore = defineStore('consultation', () => {
  const consultationId = ref<number | null>(null)
  const appointmentId = ref<number | null>(null)
  const complaint = ref<string>('')
  const imageUrls = ref<string[]>([])

  function load() {
    try {
      const raw = localStorage.getItem(STORAGE_KEY)
      if (!raw) return
      const data = JSON.parse(raw)
      consultationId.value = data.consultationId ?? null
      appointmentId.value = data.appointmentId ?? null
      complaint.value = data.complaint ?? ''
      imageUrls.value = data.imageUrls ?? []
    } catch {
      /* 忽略损坏的本地存储 */
    }
  }

  function persist() {
    localStorage.setItem(
      STORAGE_KEY,
      JSON.stringify({
        consultationId: consultationId.value,
        appointmentId: appointmentId.value,
        complaint: complaint.value,
        imageUrls: imageUrls.value,
      }),
    )
  }

  function setConsultation(id: number, apptId: number) {
    consultationId.value = id
    appointmentId.value = apptId
    persist()
  }

  function addImage(url: string) {
    if (!imageUrls.value.includes(url)) imageUrls.value.push(url)
    persist()
  }

  function reset() {
    consultationId.value = null
    appointmentId.value = null
    complaint.value = ''
    imageUrls.value = []
    persist()
  }

  load()

  return {
    consultationId,
    appointmentId,
    complaint,
    imageUrls,
    setConsultation,
    addImage,
    reset,
  }
})
