package io.amplica.custodial_wallet.client.redis.conf

import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonMapperBuilder
import io.amplica.custodial_wallet.client.redis.CustodialWalletRedisClient
import io.amplica.custodial_wallet.client.redis.ReactiveCustodialWalletRedisClient
import io.amplica.custodial_wallet.client.redis.frequency.FrequencyClientRedisClient
import io.amplica.custodial_wallet.client.redis.frequency.ReactiveFrequencyClientRedisClient
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.bind.ConstructorBinding
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory
import org.springframework.data.redis.core.ReactiveRedisOperations
import org.springframework.data.redis.core.ReactiveRedisTemplate
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer
import org.springframework.data.redis.serializer.RedisSerializationContext
import org.springframework.data.redis.serializer.RedisSerializationContext.RedisSerializationContextBuilder
import org.springframework.data.redis.serializer.RedisSerializer
import org.springframework.data.redis.serializer.StringRedisSerializer
import java.io.Closeable
import java.io.Serializable
import java.time.Duration
import java.util.logging.Handler
import java.util.regex.Pattern
import javax.naming.Referenceable
import javax.sql.DataSource


@ConfigurationProperties(prefix = "unfinished.custodial-wallet.redis")
data class RedisConfigurationProperties @ConstructorBinding constructor(
  val expiration: Duration
)

@Configuration
class RedisClientConfig {
  @Bean
  fun redisObjectMapper(): ObjectMapper {
    val basicPolymorphicTypeValidator = BasicPolymorphicTypeValidator.builder()
      .denyForExactBaseType(Closeable::class.java)
      .denyForExactBaseType(Serializable::class.java)
      .denyForExactBaseType(AutoCloseable::class.java)
      .denyForExactBaseType(Cloneable::class.java)
      .denyForExactBaseType(Referenceable::class.java)
      .denyForExactBaseType(Handler::class.java)
      .denyForExactBaseType(DataSource::class.java)
      .allowIfBaseType(Pattern.compile(".*"))
      .build()
    return jacksonMapperBuilder()
      .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
      .addModule(JavaTimeModule())
      .activateDefaultTyping(basicPolymorphicTypeValidator, ObjectMapper.DefaultTyping.NON_FINAL, JsonTypeInfo.As.PROPERTY)
      .build()
  }

  @Bean
  fun redisSerializer(
    @Qualifier("redisObjectMapper") objectMapper: ObjectMapper): RedisSerializer<Any> {
    return GenericJackson2JsonRedisSerializer(objectMapper)
  }

  @Bean
  fun reactiveRedisOperations(
    @Qualifier("redisSerializer") redisSerializer: RedisSerializer<Any>,
    connectionFactory: ReactiveRedisConnectionFactory
  ): ReactiveRedisOperations<String, Any> {
    val keySerializer = StringRedisSerializer()
    val builder: RedisSerializationContextBuilder<String, Any> = RedisSerializationContext.newSerializationContext()
    val context: RedisSerializationContext<String, Any> =
      builder.key(keySerializer)
        .value(redisSerializer)
        .hashKey(keySerializer)
        .hashValue(redisSerializer)
        .build()
    return ReactiveRedisTemplate(connectionFactory, context)
  }

  //Reactive Custodial Wallet Redis Client
  @Bean
  fun redisClient(
    @Qualifier("reactiveRedisOperations") reactiveRedisOperations: ReactiveRedisOperations<String, Any>,
    redisConfigurationProperties: RedisConfigurationProperties
  ): CustodialWalletRedisClient {
    return ReactiveCustodialWalletRedisClient(
      reactiveRedisOperations,
      redisConfigurationProperties.expiration
    )
  }

  //Reactive Frequency Client Redis Client
  @Bean
  fun frequencyClientRedisClient(@Qualifier("reactiveStringRedisTemplate") reactiveRedisOperations: ReactiveRedisOperations<String, String>): FrequencyClientRedisClient {
    return ReactiveFrequencyClientRedisClient(reactiveRedisOperations)
  }
}
