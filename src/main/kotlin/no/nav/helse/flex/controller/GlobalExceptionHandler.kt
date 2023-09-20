package no.nav.helse.flex.controller

import jakarta.servlet.http.HttpServletRequest
import no.nav.helse.flex.exception.AbstractApiError
import no.nav.helse.flex.exception.FinnesInnsendtSoknadException
import no.nav.helse.flex.exception.IkkeTilgangException
import no.nav.helse.flex.exception.LogLevel
import no.nav.helse.flex.logger
import no.nav.helse.flex.repository.SykepengesoknadDAO.SoknadIkkeFunnetException
import no.nav.security.token.support.core.exceptions.JwtTokenInvalidClaimException
import no.nav.security.token.support.spring.validation.interceptor.JwtTokenUnauthorizedException
import org.apache.catalina.connector.ClientAbortException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.HttpMediaTypeNotAcceptableException
import org.springframework.web.bind.MissingRequestHeaderException
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler

@ControllerAdvice
class GlobalExceptionHandler {

    private val log = logger()

    @ExceptionHandler(java.lang.Exception::class)
    fun handleException(ex: Exception, request: HttpServletRequest): ResponseEntity<Any> {
        return when (ex) {
            is AbstractApiError -> {
                when (ex.loglevel) {
                    LogLevel.WARN -> log.warn(ex.message, ex)
                    LogLevel.ERROR -> log.error(ex.message, ex)
                    LogLevel.OFF -> {
                    }
                }

                ResponseEntity(ApiError(ex.reason), ex.httpStatus)
            }
            is MissingRequestHeaderException -> skapResponseEntity(HttpStatus.BAD_REQUEST)
            is IngenTilgangException -> skapResponseEntity(HttpStatus.FORBIDDEN)
            is FinnesInnsendtSoknadException -> skapResponseEntity(HttpStatus.FORBIDDEN)
            is IkkeTilgangException -> skapResponseEntity(HttpStatus.FORBIDDEN)
            is SoknadIkkeFunnetException -> skapResponseEntity(HttpStatus.NOT_FOUND)
            is JwtTokenInvalidClaimException -> skapResponseEntity(HttpStatus.UNAUTHORIZED)
            is JwtTokenUnauthorizedException -> skapResponseEntity(HttpStatus.UNAUTHORIZED)
            is HttpMediaTypeNotAcceptableException -> skapResponseEntity(HttpStatus.NOT_ACCEPTABLE)
            is ClientAbortException -> {
                log.warn("ClientAbortException - ${request.method}: ${request.requestURI}", ex)
                // The 4xx (Client Error) class of status code indicates that the client seems to have erred.
                // Klient vil aldri få denne siden dette er en ClientAbortException, men noe må vi returnere.
                skapResponseEntity(HttpStatus.I_AM_A_TEAPOT)
            }
            else -> {
                log.error("Internal server error - ${ex.message} - ${request.method}: ${request.requestURI}", ex)
                skapResponseEntity(HttpStatus.INTERNAL_SERVER_ERROR)
            }
        }
    }
}

private fun skapResponseEntity(status: HttpStatus): ResponseEntity<Any> =
    ResponseEntity(ApiError(status.reasonPhrase), status)

private data class ApiError(val reason: String)
