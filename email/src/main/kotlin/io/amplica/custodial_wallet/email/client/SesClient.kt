package io.amplica.custodial_wallet.email.client

data class SendEmailRequest(
  val destinationEmail: String,
  val sourceName: String,
  val sourceEmail: String,
  val templateName: String,
  val additionalTemplateData: MutableMap<String, Any> = mutableMapOf()
)

data class SendEmailResponse(
  val messageId: String
)

data class TemplateExistsRequest(
  val templateName: String
)

data class TemplateExistsResponse(
  val templateExists: Boolean
)

data class TemplateMetadata(val name: String)

interface SesClient {
  suspend fun sendEmail(sendEmailRequest: SendEmailRequest): SendEmailResponse
  suspend fun templateExists(templateExistsRequest: TemplateExistsRequest): TemplateExistsResponse
  suspend fun healthCheck(templateExistsRequest: TemplateExistsRequest): Boolean
  suspend fun listAllTemplates(): List<TemplateMetadata>
}
