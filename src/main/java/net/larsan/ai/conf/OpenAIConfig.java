package net.larsan.ai.conf;

import java.util.Optional;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import com.google.common.base.Strings;

import io.smallrye.config.ConfigMapping;

@ConfigMapping(prefix = "openai")
public interface OpenAIConfig {

    @ConfigProperty(name = "api-key")
    Optional<String> apiKey();

    Optional<String> uri();

    public default boolean isLegal() {
        return !Strings.isNullOrEmpty(apiKey().orElse(null));
    }
}
