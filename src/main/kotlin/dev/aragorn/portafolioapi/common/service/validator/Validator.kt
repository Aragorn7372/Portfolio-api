package dev.aragorn.portafolioapi.common.service.validator

interface Validator<T> {
    fun validate(value: T)
}