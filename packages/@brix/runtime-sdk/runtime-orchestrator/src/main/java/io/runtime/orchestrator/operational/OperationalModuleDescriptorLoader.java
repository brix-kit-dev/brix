/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.operational;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;

/**
 * Strict loader for the unique platform operational descriptor.
 *
 * @author Runtime SDK Team
 * @since 3.0.10
 */
public final class OperationalModuleDescriptorLoader {

    /** Active descriptor path. */
    public static final String DESCRIPTOR_RESOURCE = "META-INF/brix/platform-operational.yaml";
    /** Bundled strict descriptor schema. */
    public static final String SCHEMA_RESOURCE = "META-INF/schemas/platform-operational.schema.json";

    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
    private final JsonSchema schema;

    /**
     * Creates a loader using the Runtime class loader for the bundled schema.
     */
    public OperationalModuleDescriptorLoader() {
        InputStream input = OperationalModuleDescriptorLoader.class.getClassLoader()
            .getResourceAsStream(SCHEMA_RESOURCE);
        if (input == null) {
            throw new OperationalRuntimeException(
                "operational.schema.missing",
                "Bundled operational descriptor schema is missing");
        }
        this.schema = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(input);
    }

    /**
     * Loads and validates a descriptor URL.
     *
     * @param descriptorUrl descriptor URL
     * @return validated descriptor
     */
    public OperationalModuleDescriptor load(URL descriptorUrl) {
        try (InputStream input = descriptorUrl.openStream()) {
            JsonNode root = yamlMapper.readTree(input);
            Set<ValidationMessage> errors = schema.validate(root);
            if (!errors.isEmpty()) {
                String detail = errors.stream().map(ValidationMessage::getMessage).sorted()
                    .collect(Collectors.joining("; "));
                throw new OperationalRuntimeException(
                    "operational.descriptor.invalid",
                    "Invalid platform operational descriptor: " + detail);
            }
            return map(root);
        } catch (IOException e) {
            throw new OperationalRuntimeException(
                "operational.descriptor.unreadable",
                "Unable to read platform operational descriptor",
                e);
        }
    }

    private OperationalModuleDescriptor map(JsonNode root) {
        JsonNode metadata = root.path("metadata");
        JsonNode contracts = root.path("internalContracts");
        Map<String, OperationalModuleDescriptor.EndpointDeclaration> endpoints = new LinkedHashMap<>();
        for (JsonNode node : root.path("endpoints").path("provides")) {
            OperationalModuleDescriptor.EndpointDeclaration declaration =
                new OperationalModuleDescriptor.EndpointDeclaration(
                    text(node, "endpointId"),
                    text(node, "method"),
                    text(node, "path"),
                    text(node, "handlerId"),
                    authorizationPolicy(node.path("authorization")));
            endpoints.put(declaration.endpointId(), declaration);
        }
        Map<String, OperationalModuleDescriptor.TaskDeclaration> tasks = new LinkedHashMap<>();
        for (JsonNode node : root.path("tasks").path("provides")) {
            OperationalModuleDescriptor.TaskDeclaration declaration =
                new OperationalModuleDescriptor.TaskDeclaration(
                    text(node, "taskId"),
                    text(node, "handlerId"));
            tasks.put(declaration.taskId(), declaration);
        }
        return new OperationalModuleDescriptor(
            new OperationalModuleIdentity(
                text(metadata, "id"),
                text(metadata, "version"),
                text(metadata, "owner")),
            text(root.path("runtime"), "supportedRange"),
            stream(contracts.path("provides")).map(node ->
                new OperationalModuleDescriptor.ProvidedInternalContract(
                    text(node, "contractId"),
                    text(node, "contractType"),
                    text(node, "contractVersion"),
                    text(node, "providerId"),
                    text(node, "owner"))).toList(),
            stream(contracts.path("requires")).map(node ->
                new OperationalModuleDescriptor.RequiredInternalContract(
                    text(node, "contractId"),
                    text(node, "contractType"),
                    text(node, "versionRange"),
                    node.path("required").asBoolean(),
                    text(node, "privilegeAllowlistRef"))).toList(),
            stream(root.path("privileges").path("allowlist")).map(JsonNode::asText).collect(Collectors.toSet()),
            endpoints,
            tasks);
    }

    private static String authorizationPolicy(JsonNode authorization) {
        String resourcePolicy = text(authorization, "resourcePolicyRef");
        return resourcePolicy.isEmpty() ? text(authorization, "policyRef") : resourcePolicy;
    }

    private static java.util.stream.Stream<JsonNode> stream(JsonNode array) {
        return StreamSupport.stream(array.spliterator(), false);
    }

    private static String text(JsonNode node, String field) {
        return node.path(field).asText();
    }
}
