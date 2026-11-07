package com.maejung.server.api

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MissingRequestHeaderException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

/** PROTOCOL.md 2절 오류 형식: HTTP 상태 + {code, message} */
class ApiException(val status: HttpStatus, val code: String, override val message: String) : RuntimeException(message)

data class ErrorResponse(val code: String, val message: String)

@RestControllerAdvice
class ApiExceptionHandler {

    @ExceptionHandler(ApiException::class)
    fun handle(e: ApiException) = ResponseEntity.status(e.status).body(ErrorResponse(e.code, e.message))

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleBadBody(e: HttpMessageNotReadableException) =
        ResponseEntity.badRequest().body(ErrorResponse("BAD_REQUEST", "요청 본문을 읽을 수 없습니다"))

    @ExceptionHandler(MissingRequestHeaderException::class)
    fun handleMissingHeader(e: MissingRequestHeaderException) =
        ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ErrorResponse("INVALID_TOKEN", "인증 토큰이 없습니다"))
}
