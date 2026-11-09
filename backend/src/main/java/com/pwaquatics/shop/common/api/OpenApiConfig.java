package com.pwaquatics.shop.common.api;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.SpecVersion;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Schema;
import jakarta.annotation.Nullable;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.ClassUtils;

/**
 * OpenAPI document used to generate the frontend's TypeScript types.
 *
 * <p>Conventions:
 *
 * <ul>
 *   <li>Response records always contain every property, so all their properties are marked
 *       required. Request bodies are named {@code *Request} and keep the required flags derived
 *       from bean validation.
 *   <li>Record components that can be {@code null} carry {@link Nullable}; they are published as
 *       nullable ({@code type: [T, "null"]}, or {@code anyOf: [$ref, null]} for objects).
 * </ul>
 */
@Configuration(proxyBeanMethods = false)
class OpenApiConfig {

    private static final String BASE_PACKAGE = "com.pwaquatics.shop";

    @Bean
    OpenAPI shopOpenApi() {
        return new OpenAPI().info(new Info().title("PW Aquatics API").version("v1"));
    }

    @Bean
    @SuppressWarnings({"rawtypes", "unchecked"})
    OpenApiCustomizer responseConventions() {
        return openApi -> {
            if (openApi.getComponents() == null || openApi.getComponents().getSchemas() == null) {
                return;
            }
            Map<String, Class<?>> records = recordsBySimpleName();
            openApi.getComponents().getSchemas().forEach((name, schema) -> {
                Map<String, Schema> properties = schema.getProperties();
                if (name.endsWith("Request") || properties == null) {
                    return;
                }
                schema.setRequired(new ArrayList<>(properties.keySet()));
                Class<?> type = records.get(name);
                if (type == null) {
                    return;
                }
                for (RecordComponent component : type.getRecordComponents()) {
                    Schema property = properties.get(component.getName());
                    if (property != null && component.isAnnotationPresent(Nullable.class)) {
                        properties.put(component.getName(), nullable(property));
                    }
                }
            });
        };
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Schema nullable(Schema property) {
        if (property.getSpecVersion() != SpecVersion.V31) {
            property.setNullable(true);
            return property;
        }
        if (property.get$ref() == null) {
            if (property.getTypes() == null || !property.getTypes().contains("null")) {
                property.addType("null");
            }
            return property;
        }
        Schema reference = new Schema<>(SpecVersion.V31).$ref(property.get$ref());
        Schema nullType = new Schema<>(SpecVersion.V31);
        nullType.addType("null");
        Schema wrapper = new Schema<>(SpecVersion.V31);
        wrapper.setAnyOf(List.of(reference, nullType));
        return wrapper;
    }

    /** All records in the application, keyed by simple name (= OpenAPI schema name). */
    private static Map<String, Class<?>> recordsBySimpleName() {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter((reader, factory) ->
                "java.lang.Record".equals(reader.getClassMetadata().getSuperClassName()));
        Map<String, Class<?>> records = new HashMap<>();
        for (BeanDefinition candidate : scanner.findCandidateComponents(BASE_PACKAGE)) {
            Class<?> type = ClassUtils.resolveClassName(candidate.getBeanClassName(), OpenApiConfig.class.getClassLoader());
            records.put(type.getSimpleName(), type);
        }
        return records;
    }
}
