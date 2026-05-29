package io.amplica.custodial_wallet.email.client

import kotlinx.coroutines.future.await
import org.json.JSONObject
import software.amazon.awssdk.services.ses.model.*
import software.amazon.awssdk.services.ses.SesAsyncClient as AwsSesAsyncClient

/**
 * NOTE (Feb 12, 2024): SES rate-limits requests to 1 per second. See
 * [SES API Docs](https://docs.aws.amazon.com/ses/latest/APIReference/API_GetTemplate.html)
 * for up-to-date limitations.
 */
class AwsSdkSesAsyncClient(private val sesClient: AwsSesAsyncClient) : SesClient {

  companion object{
    const val AWS_MAX_ITEMS = 100
  }

  override suspend fun sendEmail(sendEmailRequest: SendEmailRequest): SendEmailResponse {
    val templateData = sendEmailRequest.additionalTemplateData
    val request = SendTemplatedEmailRequest.builder()
      .destination(
        Destination.builder().toAddresses(sendEmailRequest.destinationEmail)
          .build()
      )
      .source("${sendEmailRequest.sourceName} <${sendEmailRequest.sourceEmail}>")
      .template(sendEmailRequest.templateName)
      .templateData(JSONObject(templateData).toString())
      .build()
    val sendTemplatedEmailResponse = sesClient.sendTemplatedEmail(request).await()
    return SendEmailResponse(sendTemplatedEmailResponse.messageId())
  }

  override suspend fun templateExists(templateExistsRequest: TemplateExistsRequest): TemplateExistsResponse {
    val request = GetTemplateRequest.builder()
      .templateName(templateExistsRequest.templateName)
      .build()
    return try{
      sesClient.getTemplate(request).await()
      TemplateExistsResponse(true)
    } catch(e: TemplateDoesNotExistException){
      TemplateExistsResponse(false)
    }
  }

  override suspend fun healthCheck(templateExistsRequest: TemplateExistsRequest): Boolean {
    return templateExists(templateExistsRequest).templateExists
  }

  override suspend fun listAllTemplates(): List<TemplateMetadata> {
    val listTemplatesResponse = sesClient.listTemplates(
      ListTemplatesRequest.builder().maxItems(AWS_MAX_ITEMS).build()
    ).await()
    return if(listTemplatesResponse.templatesMetadata().isNotEmpty()){
      val templateMetadataList = mutableListOf<TemplateMetadata>()
      for(returnedMetadata in listTemplatesResponse.templatesMetadata()){
        templateMetadataList.add(TemplateMetadata(returnedMetadata.name()))
      }
      templateMetadataList
    }else{
      emptyList()
    }
  }
}