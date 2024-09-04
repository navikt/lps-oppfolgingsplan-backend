package no.nav.syfo.oppfolgingsplanmottak.service

import no.nav.syfo.application.environment.ToggleEnv
import no.nav.syfo.application.metric.COUNT_METRIKK_FOLLOWUP_LPS_BISTAND_FRA_NAV_FALSE
import no.nav.syfo.application.metric.COUNT_METRIKK_FOLLOWUP_LPS_BISTAND_FRA_NAV_TRUE
import no.nav.syfo.client.dokarkiv.DokarkivClient
import no.nav.syfo.client.isdialogmelding.IsdialogmeldingClient
import no.nav.syfo.client.oppdfgen.OpPdfGenClient
import no.nav.syfo.oppfolgingsplanmottak.domain.FollowUpPlan
import no.nav.syfo.oppfolgingsplanmottak.domain.FollowUpPlanDTO
import no.nav.syfo.oppfolgingsplanmottak.kafka.FollowUpPlanProducer
import no.nav.syfo.oppfolgingsplanmottak.kafka.domain.KFollowUpPlan
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.util.*

class FollowUpPlanSendingService(
    private val isdialogmeldingClient: IsdialogmeldingClient,
    private val followupPlanProducer: FollowUpPlanProducer,
    private val opPdfGenClient: OpPdfGenClient,
    private val dokarkivClient: DokarkivClient,
    private val toggles: ToggleEnv,
) {
    val log: Logger = LoggerFactory.getLogger(FollowUpPlanSendingService::class.qualifiedName)

    suspend fun sendFollowUpPlan(
        followUpPlanDTO: FollowUpPlanDTO,
        uuid: UUID,
        employerOrgnr: String,
    ): FollowUpPlan {
        val sykmeldtFnr = followUpPlanDTO.employeeIdentificationNumber
        val shouldSendToNav = shouldSendToNav(followUpPlanDTO)
        val shouldSendToGeneralPractitioner = shouldSendToGeneralPractitioner(toggles, followUpPlanDTO)
        val pdf: ByteArray? = opPdfGenClient.getLpsPdf(followUpPlanDTO)

        log.info("Should send to NAV: $shouldSendToNav")
        log.info("Should send to GP: $shouldSendToGeneralPractitioner")

        val sentToFastlegeStatus: Boolean =
            shouldSendToGeneralPractitioner && run {
                if (pdf != null) {
                    isdialogmeldingClient.sendLpsPlanToGeneralPractitioner(
                        sykmeldtFnr,
                        pdf
                    )
                    true
                } else {
                    false
                }
            }

        if (shouldSendToNav) {
            log.info("Sending follow-up plan with uuid $uuid to NAV")
            val planToSendToNav = KFollowUpPlan(
                uuid.toString(),
                followUpPlanDTO.employeeIdentificationNumber,
                employerOrgnr,
                true,
                LocalDate.now().toEpochDay().toInt(),
            )
            followupPlanProducer.createFollowUpPlanTaskInModia(planToSendToNav)

            if (pdf != null) {
                log.info("Sending follow-up plan with uuid $uuid to dokarkiv")
                dokarkivClient.journalforLps(followUpPlanDTO, employerOrgnr, pdf, uuid)
            } else {
                log.warn("Could not send LPS-plan with uuid $uuid to NAV because PDF is null")
            }
            COUNT_METRIKK_FOLLOWUP_LPS_BISTAND_FRA_NAV_TRUE.increment()
        } else {
            COUNT_METRIKK_FOLLOWUP_LPS_BISTAND_FRA_NAV_FALSE.increment()
        }

        return FollowUpPlan(
            uuid = uuid.toString(),
            isSentToGeneralPractitionerStatus = sentToFastlegeStatus,
            isSentToNavStatus = followUpPlanDTO.sendPlanToNav,
            pdf = pdf
        )
    }

    private fun shouldSendToNav(followUpPlanDTO: FollowUpPlanDTO): Boolean {
        return followUpPlanDTO.sendPlanToNav && followUpPlanDTO.needsHelpFromNav == true
    }

    private fun shouldSendToGeneralPractitioner(toggles: ToggleEnv, followUpPlanDTO: FollowUpPlanDTO): Boolean {
        return toggles.sendLpsPlanToFastlegeToggle && followUpPlanDTO.sendPlanToGeneralPractitioner
    }
}
