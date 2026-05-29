package io.amplica.custodial_wallet.client.redis

import io.amplica.custodial_wallet.client.redis.dto.BatchPayloadToSignRequest
import io.amplica.custodial_wallet.client.redis.dto.PayloadRequest
import io.amplica.custodial_wallet.client.redis.dto.SesTemplate
import io.amplica.custodial_wallet.client.redis.dto.TypedPayloadRequestWithSignature
import io.amplica.custodial_wallet.exception.ApiError
import io.amplica.custodial_wallet.exception.ApiException
import kotlin.reflect.KClass

interface RedisObjectToDtoMapper<out DTO, REDIS_OBJECT> {
  fun mapFromRedisObject(redisObject: REDIS_OBJECT): DTO
}

interface DtoToRedisObjectMapper<DTO, out REDIS_OBJECT> {
  fun mapToRedisObject(dto: DTO): REDIS_OBJECT
}

class BatchPayloadToSignDtoToRedis(
  private val dtoToRedisObjectMapperRegistry: MutableMap<KClass<out Any>, DtoToRedisObjectMapper<out Any, Any>>,
  private val timeToLiveSeconds: Long
) : DtoToRedisObjectMapper<BatchPayloadToSignRequest, RedisBatchPayloadToSignRequest> {
  override fun mapToRedisObject(dto: BatchPayloadToSignRequest): RedisBatchPayloadToSignRequest {
    @Suppress("unchecked_cast")
    val mapper =
      dtoToRedisObjectMapperRegistry[TypedPayloadRequestWithSignature::class] as DtoToRedisObjectMapper<TypedPayloadRequestWithSignature<*>, *>?
        ?: throw ApiException(
          ApiError.PAYLOAD_TYPE_NOT_SUPPORTED,
          "Payload type not supported, UserIdentifier=${dto.userIdentifier.value}"
        )

    val redisTypedPayloadsToSign: MutableList<RedisTypedPayloadWithSignature<*>> = mutableListOf()
    for (payload in dto.payloads) {
      val redisObject = mapper.mapToRedisObject(payload) ?: throw ApiException(
        ApiError.PAYLOAD_TYPE_NOT_SUPPORTED,
        "Payload type not supported, UserIdentifier=${dto.userIdentifier.value}"
      )
      @Suppress("unchecked_cast")
      redisTypedPayloadsToSign.add(redisObject as RedisTypedPayloadWithSignature<Any>)
    }

    return RedisBatchPayloadToSignRequest(
      dto.id ?: generateUUID(),
      dto.externalUserId,
      dto.userIdentifier,
      dto.publicKey,
      dto.callback,
      redisTypedPayloadsToSign,
      timeToLiveSeconds,
      dto.authenticationCode,
      dto.authorizationCode
    )
  }
}

class BatchPayloadToSignRedisToDto(
  private val redisObjectToDtoMapperRegistry: MutableMap<KClass<out Any>, RedisObjectToDtoMapper<Any, out Any>>,
) : RedisObjectToDtoMapper<BatchPayloadToSignRequest, RedisBatchPayloadToSignRequest> {
  override fun mapFromRedisObject(redisObject: RedisBatchPayloadToSignRequest): BatchPayloadToSignRequest {
    @Suppress("unchecked_cast")
    val mapper = redisObjectToDtoMapperRegistry[RedisTypedPayloadWithSignature::class] as RedisObjectToDtoMapper<*, Any>?
      ?: throw ApiException(
        ApiError.PAYLOAD_TYPE_NOT_SUPPORTED,
        "Payload type not supported, userIdentifier=${redisObject.userIdentifier}"
      )

    val typedPayloadsToSign: MutableList<TypedPayloadRequestWithSignature<PayloadRequest>> = mutableListOf()
    for (payload in redisObject.payloads) {
      val dto = mapper.mapFromRedisObject(payload) ?: throw ApiException(
        ApiError.PAYLOAD_TYPE_NOT_SUPPORTED,
        "Payload type not supported, UserIdentifier=${redisObject.userIdentifier.value}"
      )
      @Suppress("unchecked_cast")
      typedPayloadsToSign.add(dto as TypedPayloadRequestWithSignature<PayloadRequest>)
    }

    return BatchPayloadToSignRequest(
      redisObject.id,
      redisObject.externalUserId,
      redisObject.userIdentifier,
      redisObject.publicKey,
      redisObject.callback,
      typedPayloadsToSign,
      redisObject.authenticationCode,
      redisObject.authorizationCode,
    )
  }
}

class TypedPayloadRequestWithSignatureDtoToRedis(
  private val dtoToRedisObjectMapperRegistry: MutableMap<KClass<out Any>, DtoToRedisObjectMapper<out Any, Any>>,
) : DtoToRedisObjectMapper<TypedPayloadRequestWithSignature<PayloadRequest>, RedisTypedPayloadWithSignature<Any>> {
  override fun mapToRedisObject(dto: TypedPayloadRequestWithSignature<PayloadRequest>): RedisTypedPayloadWithSignature<Any> {
    @Suppress("unchecked_cast")
    val mapper = dtoToRedisObjectMapperRegistry[dto.payload::class] as DtoToRedisObjectMapper<Any, *>?
      ?: throw ApiException(ApiError.PAYLOAD_TYPE_NOT_SUPPORTED, "Payload type not supported")
    val redisObject = mapper.mapToRedisObject(dto.payload) ?: throw ApiException(
      ApiError.PAYLOAD_TYPE_NOT_SUPPORTED,
      "Payload type not supported"
    )

    return RedisTypedPayloadWithSignature(
      dto.signature,
      dto.type,
      redisObject
    )
  }
}

class TypedPayloadRequestWithSignatureRedisToDto(
  private val redisObjectToDtoMapperRegistry: MutableMap<KClass<out Any>, RedisObjectToDtoMapper<Any, out Any>>,
) : RedisObjectToDtoMapper<TypedPayloadRequestWithSignature<*>, RedisTypedPayloadWithSignature<Any>> {
  override fun mapFromRedisObject(redisObject: RedisTypedPayloadWithSignature<Any>): TypedPayloadRequestWithSignature<*> {
    @Suppress("unchecked_cast")
    val mapper = redisObjectToDtoMapperRegistry[redisObject.payload::class] as RedisObjectToDtoMapper<PayloadRequest?, Any>?
      ?: throw ApiException(ApiError.PAYLOAD_TYPE_NOT_SUPPORTED, "Payload type not supported")
    val dto = mapper.mapFromRedisObject(redisObject.payload) ?: throw ApiException(
      ApiError.PAYLOAD_TYPE_NOT_SUPPORTED,
      "Payload type not supported"
    )

    return TypedPayloadRequestWithSignature(
      redisObject.signature,
      redisObject.type,
      dto
    )
  }
}

object SesTemplateRedisToDtoMapper: RedisObjectToDtoMapper<Set<SesTemplate>, RedisSesTemplates> {
  override fun mapFromRedisObject(redisObject: RedisSesTemplates): Set<SesTemplate> {
    val sesTemplateList = redisObject.redisSesTemplates.map { SesTemplate(it.sesTemplateName) }.toSet()
    return sesTemplateList
  }
}

object SesTemplateDtoToRedisMapper: DtoToRedisObjectMapper<Set<SesTemplate>, RedisSesTemplates> {
  override fun mapToRedisObject(dto: Set<SesTemplate>): RedisSesTemplates {
    val redisTemplateList = dto.map {
      RedisSesTemplate(it.sesTemplateName)
    }

    return RedisSesTemplates(redisTemplateList)
  }
}
