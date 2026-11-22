import { defineStore } from 'pinia'
import { ref } from 'vue'

/**
 * 预约流程状态（宪法 G2/G5）：store 创建时从 localStorage 恢复，
 * 保证刷新后预约/支付状态不丢失。
 */
const STORAGE_KEY = 'sdas-appointment'

export const useAppointmentStore = defineStore('appointment', () => {
  const appointmentId = ref<number | null>(null)
  const paymentStatus = ref<string>('UNPAID')
  const selectedDoctorId = ref<number | null>(null)
  const selectedScheduleId = ref<number | null>(null)
  const feeAmount = ref<number>(0)

  function load() {
    try {
      const raw = localStorage.getItem(STORAGE_KEY)
      if (!raw) return
      const data = JSON.parse(raw)
      appointmentId.value = data.appointmentId ?? null
      paymentStatus.value = data.paymentStatus ?? 'UNPAID'
      selectedDoctorId.value = data.selectedDoctorId ?? null
      selectedScheduleId.value = data.selectedScheduleId ?? null
      feeAmount.value = data.feeAmount ?? 0
    } catch {
      /* 忽略损坏的本地存储 */
    }
  }

  function persist() {
    localStorage.setItem(
      STORAGE_KEY,
      JSON.stringify({
        appointmentId: appointmentId.value,
        paymentStatus: paymentStatus.value,
        selectedDoctorId: selectedDoctorId.value,
        selectedScheduleId: selectedScheduleId.value,
        feeAmount: feeAmount.value,
      }),
    )
  }

  function selectDoctor(doctorId: number, scheduleId: number, fee: number) {
    selectedDoctorId.value = doctorId
    selectedScheduleId.value = scheduleId
    feeAmount.value = fee
    persist()
  }

  function setAppointment(id: number) {
    appointmentId.value = id
    persist()
  }

  function setPaymentStatus(status: string) {
    paymentStatus.value = status
    persist()
  }

  function reset() {
    appointmentId.value = null
    paymentStatus.value = 'UNPAID'
    selectedDoctorId.value = null
    selectedScheduleId.value = null
    feeAmount.value = 0
    persist()
  }

  load()

  return {
    appointmentId,
    paymentStatus,
    selectedDoctorId,
    selectedScheduleId,
    feeAmount,
    selectDoctor,
    setAppointment,
    setPaymentStatus,
    reset,
  }
})
