package no.nav.syfo.client.azuread

import java.time.LocalDateTime
@Suppress("ConstructorParameterNaming")
data class AzureAdTokenResponse(
    val access_token: String?,
    val expires_in: Long,
    val token_type: String?,
)

fun AzureAdTokenResponse.toAzureAdToken(): AzureAdToken {
    val expiresOn = LocalDateTime.now().plusSeconds(this.expires_in)
    return AzureAdToken(
        accessToken = this.access_token,
        expires = expiresOn,
    )
}
