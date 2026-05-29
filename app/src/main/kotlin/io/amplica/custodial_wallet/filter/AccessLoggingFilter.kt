package io.amplica.custodial_wallet.filter

import org.springframework.boot.web.embedded.netty.NettyReactiveWebServerFactory
import org.springframework.boot.web.embedded.netty.NettyServerCustomizer
import org.springframework.boot.web.server.WebServerFactoryCustomizer
import org.springframework.stereotype.Component
import reactor.netty.http.server.HttpServer
import reactor.netty.http.server.logging.AccessLog
import reactor.netty.http.server.logging.AccessLogFactory
import reactor.util.annotation.Nullable
import java.net.InetSocketAddress
import java.net.SocketAddress


@Component
class AccessLoggingFilter : WebServerFactoryCustomizer<NettyReactiveWebServerFactory> {
  override fun customize(serverFactory: NettyReactiveWebServerFactory) {
    serverFactory.addServerCustomizers(LogFilterCustomizer())
  }

  private class LogFilterCustomizer() : NettyServerCustomizer {
    override fun apply(httpServer: HttpServer): HttpServer {
      return httpServer.accessLog(
        true, AccessLogFactory.createFilter(
          { accessLogArgProvider -> !accessLogArgProvider.uri().toString().startsWith("/css/") &&
              !accessLogArgProvider.uri().toString().startsWith("/assets/") &&
              !accessLogArgProvider.uri().toString().startsWith("/js/") &&
              !accessLogArgProvider.uri().toString().startsWith("/img/") &&
              !accessLogArgProvider.uri().toString().startsWith("/fonts/") &&
              !accessLogArgProvider.uri().toString().startsWith("/favicon.ico") },
          { accessLogArgProvider -> AccessLog.create(
            "{} - {} [{}] \"{} {} {}\" {} {} {} User-Agent={} X-Forwarded-For={} duration={}",
            applyAddress(accessLogArgProvider.connectionInformation()?.remoteAddress()),
            accessLogArgProvider.user(),
            accessLogArgProvider.accessDateTime(),
            accessLogArgProvider.method(),
            accessLogArgProvider.uri(),
            accessLogArgProvider.protocol(),
            accessLogArgProvider.status(),
            accessLogArgProvider.contentLength(),
            if (accessLogArgProvider.contentLength() > -1) accessLogArgProvider.contentLength() else "-",
            accessLogArgProvider.requestHeader("User-Agent"),
            accessLogArgProvider.requestHeader("X-Forwarded-For"),
            accessLogArgProvider.duration(),
          ) }
        )
      )
    }
  }
}

fun applyAddress(@Nullable socketAddress: SocketAddress?): String {
  return if (socketAddress is InetSocketAddress) socketAddress.hostString else "-"
}
