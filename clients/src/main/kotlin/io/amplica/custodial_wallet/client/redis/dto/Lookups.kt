package io.amplica.custodial_wallet.client.redis.dto

import io.amplica.custodial_wallet.util.key_creation.PublicKeyDto

data class PublicKeyResponse(val publicKey: PublicKeyDto,
                             val isPresent: Boolean)

data class PublicKeysResponse(val publicKeys: List<PublicKeyResponse>)

data class PublicKeyRequest(val publicKey: PublicKeyDto)

data class PublicKeysRequest(val publicKeys: List<PublicKeyRequest>)