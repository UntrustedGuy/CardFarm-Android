package io.github.untrustedguy.cardfarm.steam

import `in`.dragonbra.javasteam.steam.authentication.IAuthenticator
import java.util.concurrent.CompletableFuture

/**
 * Bridges JavaSteam's Steam Guard callbacks to the Compose UI: publishes a
 * [GuardRequest] on [FarmRepository.guardRequest] and waits until the user
 * submits a code.
 */
class UiAuthenticator : IAuthenticator {

    override fun getDeviceCode(previousCodeWasIncorrect: Boolean): CompletableFuture<String> =
        request(GuardType.DEVICE_CODE, null, previousCodeWasIncorrect)

    override fun getEmailCode(email: String?, previousCodeWasIncorrect: Boolean): CompletableFuture<String> =
        request(GuardType.EMAIL_CODE, email, previousCodeWasIncorrect)

    override fun acceptDeviceConfirmation(): CompletableFuture<Boolean> {
        // Always fall back to manual code entry: polling for a confirmation in
        // the Steam mobile app is awkward when this app is on the same phone.
        return CompletableFuture.completedFuture(false)
    }

    private fun request(
        type: GuardType,
        email: String?,
        previousCodeWasIncorrect: Boolean,
    ): CompletableFuture<String> {
        val guardRequest = GuardRequest(type, email, previousCodeWasIncorrect)
        FarmRepository.guardRequest.value = guardRequest
        return guardRequest.future.whenComplete { _, _ ->
            FarmRepository.guardRequest.value = null
        }
    }
}
