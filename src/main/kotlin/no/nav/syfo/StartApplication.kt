package no.nav.syfo

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.http.*
import io.ktor.serialization.jackson.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.routing.*
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import no.nav.syfo.api.lps.registerOppfolgingsplanApi
import no.nav.syfo.api.nais.registerNaisApi
import no.nav.syfo.api.nais.registerPrometheusApi
import no.nav.syfo.api.setupAuth
import no.nav.syfo.api.swagger.registerSwaggerApi
import no.nav.syfo.api.test.registerMaskinportenTokenApi
import no.nav.syfo.consumer.azuread.AzureAdTokenConsumer
import no.nav.syfo.consumer.isdialogmelding.IsdialogmeldingConsumer
import no.nav.syfo.consumer.oppdfgen.OpPdfGenConsumer
import no.nav.syfo.consumer.pdl.PdlConsumer
import no.nav.syfo.db.Database
import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.db.grantAccessToIAMUsers
import no.nav.syfo.environment.Environment
import no.nav.syfo.environment.getEnv
import no.nav.syfo.environment.isDev
import no.nav.syfo.kafka.consumers.altinnkanal.LPSKafkaConsumer
import no.nav.syfo.kafka.producers.NavLpsProducer
import no.nav.syfo.scheduling.AltinnLpsScheduler
import no.nav.syfo.service.AltinnLPSService
import no.nav.syfo.util.LeaderElection
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.coroutines.CoroutineContext

val state: ApplicationState = ApplicationState()
const val SERVER_SHUTDOWN_GRACE_PERIOD = 10L
const val SERVER_SHUTDOWN_TIMEOUT = 10L
lateinit var database: DatabaseInterface

fun main() {
    val env = getEnv()
    val backgroundTasksContext = Executors.newFixedThreadPool(
        env.application.coroutineThreadPoolSize
    ).asCoroutineDispatcher()
    database = Database(env.database)
    database.grantAccessToIAMUsers()
    val aadTokenConsumer = AzureAdTokenConsumer(env.auth)
    val opPdfGenConsumer = OpPdfGenConsumer(env.urls, env.application)
    val isdialogmeldingConsumer = IsdialogmeldingConsumer(env.urls, aadTokenConsumer)
    val pdlConsumer = PdlConsumer(env.urls, aadTokenConsumer)
    val navLpsProducer = NavLpsProducer(env.kafka)
    val altinnLpsService = AltinnLPSService(
        pdlConsumer,
        opPdfGenConsumer,
        database,
        navLpsProducer,
        isdialogmeldingConsumer,
        env.altinnLps.sendToGpRetryThreshold,
    )

    val server = embeddedServer(
        Netty,
        applicationEngineEnvironment {
            connector {
                port = env.application.port
            }

            module {
                state.running = true
                serverModule(env)
                kafkaModule(
                    state,
                    backgroundTasksContext,
                    altinnLpsService,
                    env,
                )
                schedulerModule(
                    backgroundTasksContext,
                    database,
                    altinnLpsService,
                    env,
                )
            }
        }
    )

    Runtime.getRuntime().addShutdownHook(
        Thread {
            server.stop(SERVER_SHUTDOWN_GRACE_PERIOD, SERVER_SHUTDOWN_TIMEOUT, TimeUnit.SECONDS)
        }
    )

    server.start(wait = true)
}

fun Application.serverModule(env: Environment) {
    install(ContentNegotiation) {
        jackson {
            registerKotlinModule()
            registerModule(JavaTimeModule())
            configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
            configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        }
    }

    install(CORS) {
        anyHost()
        allowHeader(HttpHeaders.ContentType)
    }

    setupAuth(env)

    routing {
        registerNaisApi(state)
        registerPrometheusApi()
        registerOppfolgingsplanApi(database)
        registerSwaggerApi()
    }

    isDev(env) {
        routing {
            registerMaskinportenTokenApi(env)
        }
    }

    state.initialized = true
}

fun Application.kafkaModule(
    appState: ApplicationState,
    backgroundTasksContext: CoroutineContext,
    altinnLPSService: AltinnLPSService,
    env: Environment,
) {
    launch(backgroundTasksContext) {
        try {
            val lpsKafkaConsumer = LPSKafkaConsumer(env.kafka, altinnLPSService)
            lpsKafkaConsumer.listen(appState)
        } finally {
            appState.running = false
        }
    }
}

fun Application.schedulerModule(
    backgroundTasksContext: CoroutineContext,
    database: DatabaseInterface,
    altinnLpsService: AltinnLPSService,
    env: Environment,
) {
    val leaderElection = LeaderElection(env.application)

    launch(backgroundTasksContext) {
        val scheduler = AltinnLpsScheduler(
                database,
                altinnLpsService,
                leaderElection
        ).startScheduler()
        Runtime.getRuntime().addShutdownHook(
            Thread {
                scheduler.shutdown()
            }
        )
    }
}

data class ApplicationState(var running: Boolean = false, var initialized: Boolean = false)
