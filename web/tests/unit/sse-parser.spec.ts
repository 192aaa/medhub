import { describe, expect, it } from 'vitest'
import { createSseParser } from '@/api/sse'

describe('sse frame parser', () => {
  it('splits complete frames in one chunk', () => {
    const parse = createSseParser()
    const messages = parse(
      'event: accepted\ndata: {"messageId":1}\n\nevent: delta\ndata: {"text":"你"}\n\n',
    )
    expect(messages).toEqual([
      { event: 'accepted', data: '{"messageId":1}' },
      { event: 'delta', data: '{"text":"你"}' },
    ])
  })

  it('buffers partial frames across chunks', () => {
    const parse = createSseParser()
    expect(parse('event: delta\ndata: {"text":"咳')).toEqual([])
    expect(parse('嗽"}\n\n')).toEqual([{ event: 'delta', data: '{"text":"咳嗽"}' }])
  })

  it('joins multi-line data and skips comments', () => {
    const parse = createSseParser()
    const messages = parse(': keep-alive\nevent: delta\ndata: line1\ndata: line2\n\n')
    expect(messages).toEqual([{ event: 'delta', data: 'line1\nline2' }])
  })

  it('ignores frames without data', () => {
    const parse = createSseParser()
    expect(parse(': heartbeat\n\n')).toEqual([])
  })
})
