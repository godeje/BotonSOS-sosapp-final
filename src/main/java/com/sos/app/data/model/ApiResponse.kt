package com.sos.app.data.model

data class ApiResponse(
  val ok: Boolean,
  val message: String? = null,
  val userId: Long? = null,
  val id: Long? = null
)

