package io.amplica.custodial_wallet.util

import org.junit.jupiter.api.Assertions
import org.springframework.boot.test.web.client.TestRestTemplate

class SesUtil(
  private val testRestTemplate: TestRestTemplate,
  val sesEndpoint: String
) {

  fun deleteAllMessages() {
    testRestTemplate.delete(sesEndpoint + SES_PATH)
  }

  fun getMessages(): List<SesMessage> {
    return getSesMessages(sesEndpoint)
  }

  fun getLatestMessage(recipientMustBe: String): SesMessage {
    // Check that the email was sent
    val message = getMessages().last()

    // Assert email sent to user's email
    Assertions.assertTrue(message.recipients.contains(recipientMustBe))
    return message
  }
}