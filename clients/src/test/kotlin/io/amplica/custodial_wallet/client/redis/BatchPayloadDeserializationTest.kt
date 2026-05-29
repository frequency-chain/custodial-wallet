package io.amplica.custodial_wallet.client.redis

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonMapperBuilder
import io.amplica.custodial_wallet.client.redis.dto.*
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.io.File
import java.io.InputStream

data class FooBar (
    @JsonProperty("foo") val foo: String,
    @JsonProperty("bar") val bar: String)

abstract class FooishBar (
    val bar: String,
)

class FlogBar (
    @JsonProperty("flog") val flog: String,
    @JsonProperty("bar") bar: String,
) : FooishBar(bar)

class FrimBar (
    @JsonProperty("frim") val frim: List<String>,
    @JsonProperty("bar") bar: String,
) : FooishBar(bar)

class FooishBarWrapper (
    @JsonProperty("type") val type: String,


    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type", include = JsonTypeInfo.As.EXTERNAL_PROPERTY)
    @JsonSubTypes(
        value = [
            JsonSubTypes.Type(value = FlogBar::class, name = "FlogBar"),
            JsonSubTypes.Type(value = FrimBar::class, name = "FrimBar")
        ]
    )
    @JsonProperty("fooishBar") val fooishBar: FooishBar
)

data class FooishBars (
    @JsonProperty("fooishBars") val fooishBars: List<FooishBarWrapper>
)

class BatchPayloadDeserializationTest {

    private val mapper = jacksonMapperBuilder().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build()

    @Test
    fun deserializeFooBarJson() {
        val jsonStream = grabJsonStream("foobar.json")
        val foobar: FooBar = mapper.readValue(jsonStream, FooBar::class.java)
        Assertions.assertEquals("foo", foobar.foo)
    }

    @Test
    fun deserializeFlogBarJson() {
        val jsonStream = grabJsonStream("flogbar.json")
        val fooishbar: FooishBarWrapper = mapper.readValue(jsonStream, FooishBarWrapper::class.java)
        val flogBar = fooishbar.fooishBar as FlogBar
        Assertions.assertEquals("flog", flogBar.flog)
    }

    @Test
    fun deserializeFrimBarJson() {
        val jsonStream = grabJsonStream("frimbar.json")
        val fooishbar: FooishBarWrapper = mapper.readValue(jsonStream, FooishBarWrapper::class.java)
        val frimBar = fooishbar.fooishBar as FrimBar
        Assertions.assertEquals(listOf("f","r","i","m"), frimBar.frim)
    }

    @Test
    fun deserializeListFooishBarsJson() {
        val jsonStream = grabJsonStream("fooishbars.json")
        val fooishbars: FooishBars = mapper.readValue(jsonStream, FooishBars::class.java)

        val fooishBar0 = fooishbars.fooishBars[0].fooishBar
        Assertions.assertTrue(fooishBar0 is FlogBar)
        val flogBar = fooishBar0 as FlogBar
        Assertions.assertEquals("flog", flogBar.flog)


        val fooishBar1 = fooishbars.fooishBars[1].fooishBar
        Assertions.assertTrue(fooishBar1 is FrimBar)
        val frimBar = fooishBar1 as FrimBar
        Assertions.assertEquals(listOf("f","r","i","m"), frimBar.frim)
    }

    @Test
    fun deserializeBatchPayloadRequest() {
        val jsonStream = grabJsonStream("batchPayloadToSignRequest.json")
        val batchPayloadToSignRequest: BatchPayloadToSignRequest = mapper.readValue(jsonStream, BatchPayloadToSignRequest::class.java)

        val payloadToSign0 = batchPayloadToSignRequest.payloads[0].payload
        Assertions.assertTrue(payloadToSign0 is AddProviderPayloadRequest)
        val addProviderPayloadToSign = payloadToSign0 as AddProviderPayloadRequest
        Assertions.assertEquals(1.toBigInteger(), addProviderPayloadToSign.msaId)

        
        val payloadToSign1 = batchPayloadToSignRequest.payloads[1].payload
        Assertions.assertTrue(payloadToSign1 is HandlePayloadRequest)
        val claimHandlePayloadToSign = payloadToSign1 as HandlePayloadRequest
        Assertions.assertEquals("sampleHandle", claimHandlePayloadToSign.baseHandle)
    }


    private fun grabJsonStream(filename: String): InputStream {
        return Thread.currentThread().getContextClassLoader().getResourceAsStream(filename)
            ?: throw NoSuchFileException(File(filename))
    }
}