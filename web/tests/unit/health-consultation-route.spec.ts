import { describe, expect, it } from 'vitest'
import router from '@/router'

function route(name: string) {
  return router.getRoutes().find((record) => record.name === name)
}

describe('AI health consultation route', () => {
  it('registers /health-consultation with independent stem', () => {
    const record = route('health-consultation')
    expect(record).toBeDefined()
    expect(record?.path).toBe('/health-consultation')
  })

  it('requires patient authentication', () => {
    expect(route('health-consultation')?.meta.requiresAuth).toBe(true)
  })

  it('does not collide with consultation routes', () => {
    const paths = router.getRoutes().map((record) => record.path)
    expect(paths).toContain('/appointment/consultation/:id')
    expect(paths).toContain('/health-consultation')
  })
})
