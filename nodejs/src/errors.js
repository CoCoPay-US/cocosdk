'use strict'

/** 网关返回的业务错误（HTTP 200 且 code=200 之外的情况） */
class ApiError extends Error {
  constructor({code, reason, message}) {
    super(`cocopay: 请求失败 code=${code} reason=${reason || ''} message=${message || ''}`)
    this.name = 'ApiError'
    this.code = code
    this.reason = reason || ''
  }
}

module.exports = {ApiError}
