package no.nav.syfo.consumer.azuread

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.call.body
import io.ktor.client.engine.apache.Apache
import io.ktor.client.engine.apache.ApacheEngineConfig
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.accept
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.serialization.jackson.jackson
import no.nav.syfo.environment.AuthEnv
import org.apache.http.impl.conn.SystemDefaultRoutePlanner
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.ProxySelector
import java.time.Instant
import kotlin.collections.set

const val TWO_MINUTES_IN_SECONDS = 120L

class AzureAdTokenConsumer(authEnv: AuthEnv) {
    private val aadAccessTokenUrl = authEnv.azuread.accessTokenUrl
    private val clientId = authEnv.azuread.clientId
    private val clientSecret = authEnv.azuread.clientSecret
    private val log: Logger = LoggerFactory.getLogger(AzureAdAccessToken::class.qualifiedName)

    val config: HttpClientConfig<ApacheEngineConfig>.() -> Unit = {
        install(ContentNegotiation) {
            jackson {
                registerKotlinModule()
                registerModule(JavaTimeModule())
                configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            }
        }
    }

    val proxyConfig: HttpClientConfig<ApacheEngineConfig>.() -> Unit = {
        config()
        engine {
            customizeClient {
                setRoutePlanner(SystemDefaultRoutePlanner(ProxySelector.getDefault()))
            }
        }
    }

    val httpClientWithProxy = HttpClient(Apache, proxyConfig)

    @Volatile
    private var tokenMap = HashMap<String, AzureAdAccessToken>()

    suspend fun getToken(resource: String): String {
        val omToMinutter = Instant.now().plusSeconds(TWO_MINUTES_IN_SECONDS)

        val token: AzureAdAccessToken? = tokenMap.get(resource)

        if (token == null || token.issuedOn!!.plusSeconds(token.expires_in).isBefore(omToMinutter)) {
            log.info("Henter nytt token fra Azure AD for scope : $resource")

            val response = httpClientWithProxy.post(aadAccessTokenUrl) {
                accept(ContentType.Application.Json)

                setBody(
                    FormDataContent(
                        Parameters.build {
                            append("client_id", clientId)
                            append("scope", resource)
                            append("grant_type", "client_credentials")
                            append("client_secret", clientSecret)
                        },
                    ),
                )
            }
            if (response.status == HttpStatusCode.OK) {
                tokenMap[resource] = response.body()
            } else {
                log.error("Could not get token from Azure AD: $response")
            }
        }
        return tokenMap[resource]!!.access_token
    }

    suspend fun getOnBehalfOfToken(resource: String, token: String): String? {
        log.info("Henter nytt obo-token fra Azure AD for scope : $resource")

        val response = httpClientWithProxy.post(aadAccessTokenUrl) {
            accept(ContentType.Application.Json)

            setBody(
                FormDataContent(
                    Parameters.build {
                        append("client_id", clientId)
                        append("scope", resource)
                        append("grant_type", "urn:ietf:params:oauth:grant-type:jwt-bearer")
                        append("client_secret", clientSecret)
                        append("assertion", token)
                        append("client_assertion_type", "urn:ietf:params:oauth:grant-type:jwt-bearer")
                        append("requested_token_use", "on_behalf_of")
                    },
                ),
            )
        }

        return if (response.status == HttpStatusCode.OK) {
            response.body<AzureAdAccessToken>().access_token
        } else {
            log.error("Could not get obo-token from Azure AD: $response")
            null
        }
    }
}

@Suppress("ConstructorParameterNaming")
@JsonIgnoreProperties(ignoreUnknown = true)
data class AzureAdAccessToken(
    val access_token: String,
    val expires_in: Long,
    val issuedOn: Instant? = Instant.now(),
)
