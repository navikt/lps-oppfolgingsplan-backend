# LPS follow-up plan API: Consumer guide

This API allows for the submission of a [follow-up plan (oppfølgingsplan)](https://www.nav.no/arbeidsgiver/oppfolgingsplan) to NAV and/or the general practitioner. This API supports <i>delegation<i/>, 
which means that you as the API consumer are acting on behalf of another company/end-user. The API also supports integration without <i>delegation<i/>, which means
that you are submitting a follow-up plan on behalf of your own company.

## 🎯 Target audience

This README is primarily intended for "Lønns- og personalsystemer" (Payroll and HR Systems) that wants to integrate with NAV for submitting follow-up plans.

## 🚀 Getting started: Production guide

### 1. The employer must delegate rights to act on their behalf (optional)
<i>This step is not neccessary if you are acting on behalf of your own company.</i>

To submit a follow-up plan on behalf of an employer, the employer must delegate rights to the API (scope) to you as an
LPS system. This is done
in Altinn. Please refer to
the [Altinn documentation](https://altinn.github.io/docs/utviklingsguider/api-delegering/tilgangsstyrer/). Please note
that the employer can find our API by searching for <i>"Oppfølgingsplan"</i> in Altinn, in the menu for <i>"Tilgang til
programmeringsgrensesnitt - API"</i>.

### 2. Configure a maskinporten-client

In order to use the API, you need to have a Maskinporten client configured. Please refer to
the [Maskinporten documentation](https://docs.digdir.no/docs/Maskinporten/maskinporten_guide_apikonsument).

### 3. Retrieve a Maskinporten token

To retrieve a Maskinporten token, you need to send a POST request to the Maskinporten token
endpoint. Please refer to
the [Maskinporten documentation](https://docs.digdir.no/docs/Maskinporten/maskinporten_summary.html) for more
information.
<br>
- Scope to be used when requesting token: `nav:oppfolgingsplan/lps.write`

### 4. Verify integration (optional)

If you want to verify that the integration works as expected, you can use the `verify-integration` endpoint. Please refer to
the [Swagger documentation](https://lps-oppfolgingsplan-mottak.ekstern.dev.nav.no/swagger) for more information. Please
note that you will need to provide a valid Maskinporten token in the Authorization header.

### 5. Submit a follow-up plan

To submit a follow-up plan, you need to send a POST request with the required payload. The API will return a unique
uuid, which can be used later to check the sending status. Please refer to
the [Swagger documentation](https://lps-oppfolgingsplan-mottak.ekstern.dev.nav.no/swagger) for more information. Please
note that you will need to provide a valid Maskinporten token in the Authorization header.

### 6. Check the sending status (optional)

To check the sending status of a follow-up plan, you need to send a GET request with the uuid you received when
submitting the follow-up plan. Please refer to
the [Swagger documentation](https://lps-oppfolgingsplan-mottak.ekstern.dev.nav.no/swagger) for more information. Please
note that you will need to provide a valid Maskinporten token in the Authorization header.

## 🧪 Testing guide for delegated setup
Please refer to [NAV's guide for testing delegable API's](https://github.com/navikt/nav-ekstern-api-dok/blob/main/api-dok/teste-delegerbart-api/teste-delegerbart-api.md)

## 🧪 Testing guide for non-delegated setup
Please refer to [NAV's guide for testing API's](https://github.com/navikt/nav-ekstern-api-dok/blob/main/api-dok/teste-api/teste-api.md)

## Error Codes
If you get an error, we will provide an ApiError object in the response body. 
An ApiError will contain the following fields:
<pre>
- status: HttpStatusCode
- error: ErrorType
- message: String
</pre>

| Status | Error | Example Message | Description                                                                                               |
|--------|-------|---------------|-----------------------------------------------------------------------------------------------------------|
| 400    | VALIDATION_ERROR | Invalid employee identification number | The follow-up plan DTO has validation errors. Please check message for the specific error.                |
| 401    | AUTHENTICATION_ERROR | JWT is expired | The maskinporten-token is invalid. Please check message for the specific error.                           |
| 404    | GENERAL_PRACTITIONER_NOT_FOUND | General practitioner was not found | The general practitioner was not found. Please ensure that it is correctly registered for the employee.   |
| 404    | EMPLOYEE_NOT_FOUND | Could not find requested person in our systems | This employeeIdentificationNumber is not registered in NAV's systems.                                |
| 404    | FOLLOWUP_PLAN_NOT_FOUND | The follow-up plan with a given uuid was not found | The follow-up plan with a given uuid was not found. Only relevant for the status-endpoint.                |
| 500    | INTERNAL_SERVER_ERROR | Internal server error | An unexpected error occurred on the server. Please contact the NAV developer team if the problem persists. |

## 🎬 Demo

We have created a demo-app, which acts as an example of how you can implement the API. Here you will find relevant
input-fields with headers and descriptions for our API. When you fill out the demo-form, it will finally retrieve a
maskinporten-token on behalf of a random test-user, and submit this to our API on our test server. On the last page you
will be able to download a PDF, which looks similar to the PDF generated in production. <br>

Please note that the demo app only showcases the fields relevant for the API. This means that you are not limited to
these fields (and functionality) in your own application! We encourage you to customize the form to fit your own needs.
For example could `sykmeldingsgrad` be a relevant field for leaders, however NAV does not need this information, and it
is therefore not included in the API. <br>

- [Link to demo app](https://demo.ekstern.dev.nav.no/oppfolgingsplan-lps)
- [Link to demo repository](https://github.com/navikt/oppfolgingsplan-lps-demo)

## ✉️ Contact

Please write an email to **team-esyfo@nav.no** if you have questions about the API.
Questions about maskinporten or Altinn must be directed to Digdir/Altinn.

<br>
<details>
<summary><b>🛠️🛠️ For NAV Developers 🛠️🛠️</b></summary>

## Technical

<hr>

### 🚀 Initial setup

- Install and configure the [Detect IDEA plugin](https://plugins.jetbrains.com/plugin/10761-detekt) for live code
  analysis
- Install the [Kotest IDEA plugin](https://plugins.jetbrains.com/plugin/14080-kotest) to enable test-runs in IDEA
-

Set [target JVM version](https://www.jetbrains.com/help/idea/compiler-kotlin-compiler.html#kotlin-compiler-jvm-settings)
to 19

### 🤖 Maskinporten

You will need to configure [Maskinporten](https://docs.digdir.no/docs/Maskinporten/maskinporten_summary.html)
in order to be able to operate on behalf of employer. <br>

### 🗺️ Documentation and useful links

| Description          | Url                                                                |
|----------------------|--------------------------------------------------------------------|
| Testing library      | https://kotest.io/                                                 |
| Mocking library      | https://mockk.io/                                                  |
| Static code analysis | https://detekt.dev/                                                |
| Maskinporten         | https://docs.digdir.no/docs/Maskinporten/maskinporten_summary.html |
| Swagger test         | https://lps-oppfolgingsplan-mottak.ekstern.dev.nav.no/swagger      |
| Demo application     | https://demo.ekstern.dev.nav.no/oppfolgingsplan-lps                |

</details>