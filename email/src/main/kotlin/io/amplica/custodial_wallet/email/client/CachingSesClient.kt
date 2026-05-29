package io.amplica.custodial_wallet.email.client

import org.slf4j.LoggerFactory
import java.time.Duration

class CachingSesClient(
  private val delegate: SesClient,
  cacheTimeout: Duration, //This was previously used and I'm leaving it because we may cache for real again
  private val templateNamesSet: Set<String>
) : SesClient {
  companion object {
    private val LOG = LoggerFactory.getLogger(CachingSesClient::class.java)
  }
  override suspend fun sendEmail(sendEmailRequest: SendEmailRequest): SendEmailResponse {
    return delegate.sendEmail(sendEmailRequest)
  }

  override suspend fun templateExists(templateExistsRequest: TemplateExistsRequest): TemplateExistsResponse {
    val templateName = templateExistsRequest.templateName
    val exists = templateNamesSet.contains(templateName)
    LOG.debug("For templateNameSet={} templateName={} exists={}", templateNamesSet, templateName, exists)
    return TemplateExistsResponse(exists)
  }

  override suspend fun healthCheck(templateExistsRequest: TemplateExistsRequest): Boolean {
    return templateExists(templateExistsRequest).templateExists
  }

  override suspend fun listAllTemplates(): List<TemplateMetadata> {
    return delegate.listAllTemplates()
  }
}