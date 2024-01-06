package no.nav.syfo.consumer.pdl

import java.io.Serializable

data class PdlIdentResponse(
    val errors: List<PdlError>?,
    val data: PdlHentIdenter?
)

data class PdlHentIdenter(
    val hentIdenter: PdlIdenter?
) : Serializable

data class PdlIdenter(
    val identer: List<PdlIdent>
) : Serializable

data class PdlIdent(
    val ident: String
) : Serializable

data class PdlPersonResponse(
    val errors: List<PdlError>?,
    val data: PdlHentPerson?
) : Serializable

data class PdlHentPerson(
    val hentPerson: PdlPerson?
) : Serializable

data class PdlPerson(
    val adressebeskyttelse: List<Adressebeskyttelse>?,
    val navn: List<PersonNavn>?,
    val foedsel: List<PdlFoedsel>?
) : Serializable

data class PdlFoedsel(val foedselsdato: String?)

data class PersonNavn(
    val fornavn: String?,
    val mellomnavn: String?,
    val etternavn: String?,
)

data class Adressebeskyttelse(
    val gradering: Gradering
) : Serializable

enum class Gradering : Serializable {
    STRENGT_FORTROLIG_UTLAND,
    STRENGT_FORTROLIG,
    FORTROLIG
}

data class PdlError(
    val message: String,
    val locations: List<PdlErrorLocation>,
    val path: List<String>?,
    val extensions: PdlErrorExtension
)

data class PdlErrorLocation(
    val line: Int?,
    val column: Int?
)

data class PdlErrorExtension(
    val code: String?,
    val classification: String
)

enum class IdentType {
    FOLKEREGISTERIDENT
}
