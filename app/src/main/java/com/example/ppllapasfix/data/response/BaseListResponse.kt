package com.example.ppllapasfix.data.response

data class BaseListResponse<T>(
    val code: Int? = null,
    val message: String? = null,
    val data: List<T> = emptyList()
)