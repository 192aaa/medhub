import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { registerGuards } from './guards'
import HomeView from '@/views/home-view.vue'
import SystemHealthView from '@/views/system-health-view.vue'
import NotFoundView from '@/views/not-found-view.vue'
import PatientRegisterView from '@/views/patient-register-view.vue'
import PatientLoginView from '@/views/patient-login-view.vue'
import PatientPasswordResetView from '@/views/patient-password-reset-view.vue'
import PatientProfileView from '@/views/patient-profile-view.vue'
import PatientIdentityVerificationView from '@/views/patient-identity-verification-view.vue'
import ConsultationEntryView from '@/views/consultation-entry-view.vue'
import AppointmentDepartmentsView from '@/views/appointment-departments-view.vue'
import AppointmentDoctorsView from '@/views/appointment-doctors-view.vue'
import AppointmentTriageView from '@/views/appointment-triage-view.vue'
import AppointmentCreateView from '@/views/appointment-create-view.vue'
import AppointmentPayView from '@/views/appointment-pay-view.vue'
import ConsultationSubmitView from '@/views/consultation-submit-view.vue'
import ConsultationDetailView from '@/views/consultation-detail-view.vue'
import WorkbenchLoginView from '@/views/workbench-login-view.vue'
import WorkbenchConsultationsView from '@/views/workbench-consultations-view.vue'
import WorkbenchChatView from '@/views/workbench-chat-view.vue'
import WorkbenchPrescriptionView from '@/views/workbench-prescription-view.vue'
import WorkbenchPrescriptionDetailView from '@/views/workbench-prescription-detail-view.vue'
import PharmacyLoginView from '@/views/pharmacy-login-view.vue'
import PharmacyVerifyView from '@/views/pharmacy-verify-view.vue'
import HealthConsultationView from '@/views/health-consultation-view.vue'

/**
 * 宪法 II：同一层级禁止放置前缀词根相同的路由（如 /doctors/:id 与 /doctor/*）。
 * 此处词干 home / system 明确错开，catch-all 放最后。
 */
const routes: RouteRecordRaw[] = [
  {
    path: '/',
    name: 'home',
    component: HomeView,
    meta: { title: '首页' },
  },
  {
    path: '/system/health',
    name: 'system-health',
    component: SystemHealthView,
    meta: { title: '服务健康检查' },
  },
  {
    path: '/patient/register',
    name: 'patient-register',
    component: PatientRegisterView,
    meta: { title: '患者注册' },
  },
  {
    path: '/patient/login',
    name: 'patient-login',
    component: PatientLoginView,
    meta: { title: '患者登录' },
  },
  {
    path: '/patient/profile',
    name: 'patient-profile',
    component: PatientProfileView,
    meta: { title: '个人中心', requiresAuth: true },
  },
  {
    path: '/patient/password-reset',
    name: 'patient-password-reset',
    component: PatientPasswordResetView,
    meta: { title: '重置密码' },
  },
  {
    path: '/patient/identity-verification',
    name: 'patient-identity-verification',
    component: PatientIdentityVerificationView,
    meta: { title: '实名认证', requiresAuth: true },
  },
  {
    path: '/consultation/entry',
    name: 'consultation-entry',
    component: ConsultationEntryView,
    meta: { title: '线上问诊', requiresAuth: true, requiresVerified: true },
  },
  {
    path: '/appointment/departments',
    name: 'appointment-departments',
    component: AppointmentDepartmentsView,
    meta: { title: '选择科室', requiresAuth: true },
  },
  {
    path: '/appointment/doctors',
    name: 'appointment-doctors',
    component: AppointmentDoctorsView,
    meta: { title: '选择医生', requiresAuth: true },
  },
  {
    path: '/appointment/triage',
    name: 'appointment-triage',
    component: AppointmentTriageView,
    meta: { title: '智能导诊', requiresAuth: false },
  },
  {
    path: '/appointment/create',
    name: 'appointment-create',
    component: AppointmentCreateView,
    meta: { title: '确认预约', requiresAuth: true },
  },
  {
    path: '/appointment/pay',
    name: 'appointment-pay',
    component: AppointmentPayView,
    meta: { title: '支付挂号费', requiresAuth: true },
  },
  {
    path: '/appointment/consultation',
    name: 'consultation-submit',
    component: ConsultationSubmitView,
    meta: { title: '提交问诊单', requiresAuth: true },
  },
  {
    path: '/appointment/consultation/:id',
    name: 'consultation-detail',
    component: ConsultationDetailView,
    meta: { title: '问诊单详情', requiresAuth: true },
  },
  {
    // 005：独立词干，与 /consultation/* 明确错开（宪法 II）
    path: '/health-consultation',
    name: 'health-consultation',
    component: HealthConsultationView,
    meta: { title: 'AI健康咨询', requiresAuth: true },
  },
  {
    path: '/workbench/login',
    name: 'workbench-login',
    component: WorkbenchLoginView,
    meta: { title: '医生登录' },
  },
  {
    path: '/workbench/consultations',
    name: 'workbench-consultations',
    component: WorkbenchConsultationsView,
    meta: { title: '待接诊列表', requiresDoctor: true },
  },
  {
    path: '/workbench/consultations/:id',
    name: 'workbench-chat',
    component: WorkbenchChatView,
    meta: { title: '接诊对话', requiresDoctor: true },
  },
  {
    path: '/workbench/prescriptions/new',
    name: 'workbench-prescription-new',
    component: WorkbenchPrescriptionView,
    meta: { title: '开具处方', requiresDoctor: true },
  },
  {
    path: '/workbench/prescriptions/:id',
    name: 'workbench-prescription-detail',
    component: WorkbenchPrescriptionDetailView,
    meta: { title: '处方详情', requiresDoctor: true },
  },
  {
    path: '/pharmacy/login',
    name: 'pharmacy-login',
    component: PharmacyLoginView,
    meta: { title: '药店登录' },
  },
  {
    // 手机相机扫码直达：?t=<v1 令牌>，未登录时由守卫带回 redirect 并在登录后原样回跳
    path: '/pharmacy/verify',
    name: 'pharmacy-verify',
    component: PharmacyVerifyView,
    meta: { title: '处方验真', requiresPharmacy: true },
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'not-found',
    component: NotFoundView,
    meta: { title: '页面不存在' },
  },
]

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes,
})

registerGuards(router)

export default router
