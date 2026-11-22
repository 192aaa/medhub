import { createRequest } from './create-request'

/** 药房端独立 axios 实例（宪法 II：pharmacyRequest） */
export const pharmacyRequest = createRequest({
  baseURL: import.meta.env.VITE_API_BASE_URL,
  timeout: Number(import.meta.env.VITE_REQUEST_TIMEOUT),
  tokenStorageKey: 'sdas.pharmacy.token',
})
