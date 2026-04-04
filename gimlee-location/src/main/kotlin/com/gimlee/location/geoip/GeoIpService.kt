package com.gimlee.location.geoip

import com.maxmind.geoip2.DatabaseReader
import com.maxmind.geoip2.exception.AddressNotFoundException
import com.maxmind.geoip2.exception.GeoIp2Exception
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.net.InetAddress

@Service
class GeoIpService(private val databaseReader: DatabaseReader?) {

    private val log = LoggerFactory.getLogger(javaClass)

    val isAvailable: Boolean get() = databaseReader != null

    fun resolveCountry(ipAddress: String): String? {
        if (databaseReader == null) return null

        return try {
            val inetAddress = InetAddress.getByName(ipAddress)
            val response = databaseReader.country(inetAddress)
            response.country().isoCode()
        } catch (e: AddressNotFoundException) {
            log.debug("No GeoIP record found for IP: {}", ipAddress)
            null
        } catch (e: GeoIp2Exception) {
            log.warn("GeoIP lookup failed for IP {}: {}", ipAddress, e.message)
            null
        } catch (e: Exception) {
            log.warn("Unexpected error during GeoIP lookup for IP {}: {}", ipAddress, e.message)
            null
        }
    }
}
