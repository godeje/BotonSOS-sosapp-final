/*
 * Copyright 2025 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.sos.app.ui

import android.os.Bundle
import android.util.Patterns
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.sos.app.R
import com.sos.app.data.model.AuthResult
import com.sos.app.data.remote.AuthRepository_old
import com.sos.app.data.remote.AuthServiceProvider
import com.sos.app.databinding.ActivityForgotPasswordBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ForgotPasswordActivity : AppCompatActivity() {

  private lateinit var binding: ActivityForgotPasswordBinding
  private val repository = AuthRepository_old(AuthServiceProvider.authApi)

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    binding = ActivityForgotPasswordBinding.inflate(layoutInflater)
    setContentView(binding.root)

    binding.sendResetButton.setOnClickListener { attemptSendLink() }
  }

  private fun attemptSendLink() {
    val email = binding.forgotEmailInput.text?.toString()?.trim().orEmpty()
    if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
      binding.forgotEmailInputLayout.error = getString(R.string.error_invalid_email)
      return
    }
    binding.forgotEmailInputLayout.error = null
    sendResetLink(email)
  }

  private fun sendResetLink(email: String) {
    setLoading(true)
    lifecycleScope.launch {
      val result = withContext(Dispatchers.IO) {
          repository.forgotPassword(email)
      }
      setLoading(false)

      when (result) {
        is AuthResult.Success -> {
          Toast.makeText(this@ForgotPasswordActivity, getString(R.string.forgot_success), Toast.LENGTH_LONG).show()
          finish()
        }
        is AuthResult.Error -> {
          val message = result.message.ifBlank { getString(R.string.generic_error) }
          Toast.makeText(this@ForgotPasswordActivity, message, Toast.LENGTH_LONG).show()
        }
      }
    }
  }

  private fun setLoading(isLoading: Boolean) {
    binding.forgotProgress.visibility = if (isLoading) View.VISIBLE else View.GONE
    binding.sendResetButton.isEnabled = !isLoading
    binding.sendResetButton.text = if (isLoading) {
      getString(R.string.sending_link)
    } else {
      getString(R.string.forgot_button)
    }
  }
}