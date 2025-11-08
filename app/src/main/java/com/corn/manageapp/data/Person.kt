package com.corn.manageapp.data

import java.util.UUID

data class Person(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val gender: String,
    val idNumber: String,
    val birthDate: String,
    val phone: String,
    val email: String,
    val avatarPath: String? = null
)
