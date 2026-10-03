package dev.aragorn.portafolioapi.common.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import com.cloudinary.Cloudinary
@Configuration
class CloudinaryConfig {

    @Value($$"${app.cloudinary.url}")
    private lateinit var cloudinaryUrl: String

    @Bean
    fun cloudinary(): Cloudinary {
        return Cloudinary(cloudinaryUrl)
    }
}