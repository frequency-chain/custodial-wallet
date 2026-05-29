package io.amplica.custodial_wallet.controller

import io.amplica.custodial_wallet.orchestration.LookupOrchestrationService
import io.amplica.custodial_wallet.client.redis.dto.PublicKeysRequest
import io.amplica.custodial_wallet.client.redis.dto.PublicKeysResponse
import io.amplica.custodial_wallet.controller.util.PublicKeyAndChainStateRequest
import io.amplica.custodial_wallet.controller.util.PublicKeyAndChainStateResponse
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("api/publicKey")
class PublicKeyController @Autowired constructor(@Value("\${unfinished.custodial-wallet.admin.access.token}") private val adminAccessToken: String,
                                                 @Qualifier("lookupOrchestrationService") private val lookupOrchestrationService: LookupOrchestrationService,
                                                 @Value("\${unfinished.custodial-wallet.admin.shared.secret.rebuild.signup.payload}") private val providerAdminSharedSecret: String): AbstractApiController(true) {
  @PostMapping("admin/find")
  suspend fun findPublicKeys(@RequestBody publicKeysRequest: PublicKeysRequest, @RequestParam(name = "access_token") requestAccessToken: String): ResponseEntity<PublicKeysResponse> {
    return if(requestAccessToken == adminAccessToken){
      val publicKeysResponse = lookupOrchestrationService.findPublicKeysIn(publicKeysRequest)
      ResponseEntity.ok(publicKeysResponse)
    }else{
      ResponseEntity.status(HttpStatus.FORBIDDEN).build()
    }
  }

  @PostMapping("admin/state")
  suspend fun getPublicKeyAndChainState(@RequestBody publicKeyAndChainStateRequest: PublicKeyAndChainStateRequest,
                                        @RequestParam("shared_secret") sharedSecret: String): ResponseEntity<PublicKeyAndChainStateResponse> {
    return if(sharedSecret == providerAdminSharedSecret) {
      val publicKeyAndChainStateResponse = lookupOrchestrationService.getPublicKeyAndChainState(publicKeyAndChainStateRequest)
      return ResponseEntity.ok(publicKeyAndChainStateResponse)
    } else {
      ResponseEntity.status(HttpStatus.FORBIDDEN).build()
    }
  }
}