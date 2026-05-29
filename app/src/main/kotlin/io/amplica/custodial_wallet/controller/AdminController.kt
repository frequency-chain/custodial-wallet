package io.amplica.custodial_wallet.controller

import io.amplica.custodial_wallet.client.redis.dto.ProviderUserIdentifier
import io.amplica.custodial_wallet.client.redis.dto.SignUpResponse
import io.amplica.custodial_wallet.client.redis.dto.UserIdentifier
import io.amplica.custodial_wallet.controller.util.ChangeExternalUserIdRequest
import io.amplica.custodial_wallet.controller.util.RebuildSignupPayloadRequest
import io.amplica.custodial_wallet.controller.util.RebuildSignupPayloadRequestByPublicKey
import io.amplica.custodial_wallet.db.repository.ProviderExternalUser
import io.amplica.custodial_wallet.dto.*
import io.amplica.custodial_wallet.orchestration.CustodialWalletOrchestrationService
import io.amplica.custodial_wallet.service.organization.OrganizationData
import io.amplica.custodial_wallet.service.organization.ProviderFrequencyAccountData
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.math.BigInteger
import java.net.URI

@RestController
@RequestMapping("api/admin")
class AdminController @Autowired constructor(
  @Value("\${unfinished.custodial-wallet.admin.shared.secret}") private val adminSharedSecret: String,
  @Value("\${unfinished.custodial-wallet.admin.shared.secret.rebuild.signup.payload}") private val rebuildSignupPayloadSecret: String,
  @Value("#{\${unfinished.custodial-wallet.admin.schema.ids.rebuild.signup.payload}}") private val schemaIds: List<Int>,
  @Qualifier("custodialWalletOrchestrationService") private val custodialWalletOrchestrationService: CustodialWalletOrchestrationService,
): AbstractApiController(true) {

  @PostMapping("deleteUser")
  suspend fun deleteUser(
    @RequestBody userIdentifier: UserIdentifier,
    @RequestParam(name = "shared_secret") requestAccessToken: String
  ): ResponseEntity<DeleteUserResponse> {
    return if (requestAccessToken == adminSharedSecret) {
      val deleteUserResponse = custodialWalletOrchestrationService.deleteUserByUserIdentifier(userIdentifier)
      ResponseEntity.ok(deleteUserResponse)
    } else {
      ResponseEntity.status(HttpStatus.FORBIDDEN).build()
    }
  }

  @PostMapping("deleteUser/externalId")
  suspend fun deleteUserByExternalId(
    @RequestBody deleteUserByExternalIdRequest: DeleteUserByExternalIdRequest,
    @RequestParam(name = "shared_secret") requestAccessToken: String
  ): ResponseEntity<DeleteUserResponse> {
    return if (requestAccessToken == adminSharedSecret) {
      val deleteUserResponse = custodialWalletOrchestrationService.deleteUserByDeleteUserByExternalIdRequest(deleteUserByExternalIdRequest)
      ResponseEntity.ok(deleteUserResponse)
    } else {
      ResponseEntity.status(HttpStatus.FORBIDDEN).build()
    }
  }

  @PostMapping("revokeDelegationAndHandle")
  suspend fun revokeDelegationAndHandle(
    @RequestBody providerUserIdentifier: ProviderUserIdentifier,
    @RequestParam(name = "shared_secret") requestAccessToken: String
  ): ResponseEntity<Void> {
    return if (requestAccessToken == adminSharedSecret) {
      custodialWalletOrchestrationService.revokeDelegationAndHandle(providerUserIdentifier)
      return ResponseEntity.ok().build()
    } else {
      ResponseEntity.status(HttpStatus.FORBIDDEN).build()
    }
  }

  @PostMapping("deleteUserAndRetireMsa")
  suspend fun deleteUserAndRetireMsa(
    @RequestBody providerUserIdentifier: ProviderUserIdentifier,
    @RequestParam(name = "shared_secret") requestAccessToken: String
  ): ResponseEntity<DeleteUserResponse> {
    return if (requestAccessToken == adminSharedSecret) {
      val deleteUserResponse = custodialWalletOrchestrationService.deleteUserAndRetireMsaByUserIdentifier(providerUserIdentifier)
      ResponseEntity.ok(deleteUserResponse)
    } else {
      ResponseEntity.status(HttpStatus.FORBIDDEN).build()
    }
  }

  @PostMapping("externalUserId")
  suspend fun changeExternalUserId(
    @RequestBody changeExternalUserIdRequest: ChangeExternalUserIdRequest,
    @RequestParam("shared_secret") requestAccessToken: String
  ): ResponseEntity<ProviderExternalUser> {
    return if(requestAccessToken == rebuildSignupPayloadSecret) {
      val providerExternalUser = custodialWalletOrchestrationService.changeExternalUserId(changeExternalUserIdRequest)
      ResponseEntity.ok(providerExternalUser)
    } else {
      ResponseEntity.status(HttpStatus.FORBIDDEN).build()
    }
  }
}
