package io.amplica.custodial_wallet.internationalization

import com.github.mustachejava.Mustache
import com.github.mustachejava.MustacheFactory
import io.amplica.custodial_wallet.template.TemplateConstants
import org.springframework.core.io.ResourceLoader
import java.util.*

class MustacheTemplateResolver(
  private val mustacheFactory: MustacheFactory,
  private val resourceLoader: ResourceLoader,
  private val classpathBaseLocation: String = "classpath:/templates/sms/",
  private val fileType: String = ".mustache"
) {

  fun resolveTemplate(templateName: String, provider: String?, locale: Locale?): Mustache {
    var fullTemplateName = templateName
    val providerShortcode: String = if(provider != null) {
      provider.lowercase()
    } else {
      TemplateConstants.DEFAULT_PROVIDER_NAME
    }
    fullTemplateName += "_$providerShortcode"
    if(locale != null) fullTemplateName += "_${locale.toString().lowercase()}"

    var templateLocation = "$classpathBaseLocation$fullTemplateName$fileType"
    var templateFile = resourceLoader.getResource(templateLocation)
    while(!templateFile.exists()) {
      if(!fullTemplateName.contains("_")) {
        throw IllegalStateException("No support for given template: $fullTemplateName")
      }
      fullTemplateName = fullTemplateName.substringBeforeLast("_")
      templateLocation = "$classpathBaseLocation$fullTemplateName$fileType"
      templateFile = resourceLoader.getResource(templateLocation)
    }

    //This should play nice with how ClasspathResolver internally to mustache works as it uses the Java Classloader.getResourceAsStream approach
    val mustacheTemplateLocation = templateLocation.substringAfter("classpath:/")
    return mustacheFactory.compile(mustacheTemplateLocation)
  }

}