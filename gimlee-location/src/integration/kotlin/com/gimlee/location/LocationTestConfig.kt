package com.gimlee.location

import com.maxmind.geoip2.DatabaseReader
import com.maxmind.geoip2.exception.AddressNotFoundException
import com.maxmind.geoip2.model.CountryResponse
import com.maxmind.geoip2.record.Country
import io.mockk.every
import io.mockk.mockk
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import java.net.InetAddress

@TestConfiguration
class LocationTestConfig {

    @Bean
    @Primary
    fun mockDatabaseReader(): DatabaseReader {
        val reader = mockk<DatabaseReader>()

        val usCountry = mockk<Country>()
        every { usCountry.isoCode() } returns "US"
        val usResponse = mockk<CountryResponse>()
        every { usResponse.country() } returns usCountry

        val plCountry = mockk<Country>()
        every { plCountry.isoCode() } returns "PL"
        val plResponse = mockk<CountryResponse>()
        every { plResponse.country() } returns plCountry

        every { reader.country(InetAddress.getByName("8.8.8.8")) } returns usResponse
        every { reader.country(InetAddress.getByName("212.77.100.101")) } returns plResponse
        every { reader.country(InetAddress.getByName("127.0.0.1")) } throws
                AddressNotFoundException("127.0.0.1 is not in the database.")
        every { reader.close() } returns Unit

        return reader
    }
}
