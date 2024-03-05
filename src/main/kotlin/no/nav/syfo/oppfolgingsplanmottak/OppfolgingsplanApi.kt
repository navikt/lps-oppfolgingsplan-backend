package no.nav.syfo.oppfolgingsplanmottak

import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import no.nav.syfo.application.api.auth.JwtIssuerType
import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.oppfolgingsplanmottak.database.findSendingStatus
import no.nav.syfo.oppfolgingsplanmottak.database.storeFollowUpPlan
import no.nav.syfo.oppfolgingsplanmottak.domain.FollowUpPlanDTO
import no.nav.syfo.util.getLpsOrgnumberFromClaims
import no.nav.syfo.util.getOrgnumberFromClaims
import org.slf4j.LoggerFactory
import java.util.*
import no.nav.syfo.oppfolgingsplanmottak.service.FollowUpPlanSendingService
import no.nav.syfo.util.getSendingTimestamp

fun Routing.registerFollowUpPlanApi(
    database: DatabaseInterface,
    followUpPlanSendingService: FollowUpPlanSendingService,
) {
    val log = LoggerFactory.getLogger("FollowUpPlanApi")
    val uuid = "uuid"

    route("/api/v1/followupplan/") {
        authenticate(JwtIssuerType.MASKINPORTEN.name) {
            post("write") {
                val followUpPlanDTO = call.receive<FollowUpPlanDTO>()
                val planUuid = UUID.randomUUID()
                val employerOrgnr = getOrgnumberFromClaims()
                val lpsOrgnumber = getLpsOrgnumberFromClaims()

                log.info("Received follow up plan from ${followUpPlanDTO.lpsName}, LPS orgnr: $lpsOrgnumber")

                val followUpPlanResponse = followUpPlanSendingService.sendFollowUpPlan(followUpPlanDTO, planUuid, employerOrgnr)

                val sentToGeneralPractitionerAt = getSendingTimestamp(followUpPlanResponse.isSentToGeneralPractitionerStatus)
                val sentToNavAt = getSendingTimestamp(followUpPlanResponse.isSentToGeneralPractitionerStatus)

                database.storeFollowUpPlan(
                    uuid = planUuid,
                    followUpPlanDTO = followUpPlanDTO,
                    organizationNumber = employerOrgnr,
                    lpsOrgnumber = lpsOrgnumber,
                    sentToGeneralPractitionerAt = sentToGeneralPractitionerAt,
                    sentToNavAt = sentToNavAt,
                )

                call.respond(followUpPlanResponse)
            }

            get("read/sendingStatus/{$uuid}") {
                val uuidString = call.parameters["uuid"].toString()
                val sendingStatus = database.findSendingStatus(UUID.fromString(uuidString))
                call.respond(sendingStatus)
            }
        }
    }
}
