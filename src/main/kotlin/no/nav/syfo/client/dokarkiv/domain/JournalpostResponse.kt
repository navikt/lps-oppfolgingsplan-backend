package no.nav.syfo.client.dokarkiv.domain

data class JournalpostResponse(
    val dokumenter: List<DokumentInfo>,
    val journalpostId: Int,
    val journalpostferdigstilt: Boolean,
    val journalstatus: String,
    val melding: String?,
)
