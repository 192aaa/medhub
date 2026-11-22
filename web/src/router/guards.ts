import type { Router } from 'vue-router'
import { useAppStore } from '@/stores/app'
import { usePatientStore } from '@/stores/patient'
import { useDoctorStore } from '@/stores/doctor'
import { usePharmacyStore } from '@/stores/pharmacy'
import { AUTH_STATUS } from '@/api/types'

declare module 'vue-router' {
  interface RouteMeta {
    title?: string
    requiresAuth?: boolean
    /** 线上问诊门禁：认证状态或门禁标记任一不满足即重定认证页 */
    requiresVerified?: boolean
    /** 医生工作台门禁：无医生会话即重定医生登录页 */
    requiresDoctor?: boolean
    /** 药店门禁：无药店会话即重定药店登录页（并带回 redirect 保留 ?t=） */
    requiresPharmacy?: boolean
  }
}

/** 注册全局守卫；requiresVerified 供后续问诊页面统一复用 */
export function registerGuards(router: Router): void {
  router.beforeEach((to) => {
    const appStore = useAppStore()
    const patientStore = usePatientStore()
    const doctorStore = useDoctorStore()
    const pharmacyStore = usePharmacyStore()
    // store 创建时已从 localStorage 恢复会话，刷新后不会因内存态丢失而误拦截；
    // 患者模块使用独立 token key，注册自动登录后须能被 requiresAuth 识别
    const hasSession = appStore.hasSession || patientStore.hasSession
    if (to.meta.requiresAuth && !hasSession) {
      // 未登录统一进患者登录页，并带回原目标以便登录后返回
      return { name: 'patient-login', query: { redirect: to.fullPath } }
    }
    if (to.meta.requiresDoctor && !doctorStore.hasSession) {
      // 医生端使用独立会话，无医生会话进医生登录页
      return { name: 'workbench-login', query: { redirect: to.fullPath } }
    }
    if (to.meta.requiresPharmacy && !pharmacyStore.hasSession) {
      // 药店端独立会话；redirect 保留 ?t=，登录后自动回跳并发起验真
      return { name: 'pharmacy-login', query: { redirect: to.fullPath } }
    }
    if (
      to.meta.requiresVerified &&
      (patientStore.authStatus !== AUTH_STATUS.VERIFIED ||
        patientStore.consultationAllowed !== true)
    ) {
      // 端点 8 的判定结果落库在 patient store；两个字段任一不满足均视为门禁关闭
      return { name: 'patient-identity-verification', query: { redirect: to.fullPath } }
    }
    return true
  })

  router.afterEach((to) => {
    const appTitle = import.meta.env.VITE_APP_TITLE
    document.title = to.meta.title ? `${to.meta.title} - ${appTitle}` : appTitle
  })
}
